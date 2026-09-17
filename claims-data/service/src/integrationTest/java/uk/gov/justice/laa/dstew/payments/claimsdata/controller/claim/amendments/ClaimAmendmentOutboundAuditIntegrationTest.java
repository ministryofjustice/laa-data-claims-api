package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * DSTEW-2001: proves the resolver, connector and audit service run together on the amendment route.
 * Only the fee-calculation call is audited on this path today; the library's PDA and fee-details
 * calls are not yet wired, so exactly one row per priced amendment is expected.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Amendment outbound API audit (DSTEW-2001) Integration Test")
public class ClaimAmendmentOutboundAuditIntegrationTest
    extends AbstractAmendmentPatchIntegrationTest {

  private static final String AUDIT_ROWS_SQL =
      "SELECT * FROM audit.external_api_call_log WHERE system_type = 'FEE_SCHEME_PLATFORM'";
  private static final String FSP_RESPONSE =
      "{\"feeCode\":\"FEE-123\",\"schemeId\":\"SCHEME-TEST\",\"escapeCaseFlag\":false,"
          + "\"feeCalculation\":{\"totalAmount\":650.00,\"netProfitCostsAmount\":450.00,"
          + "\"vatIndicator\":true}}";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws Exception {
    stubExternalValidationEndpoints();
    stubFeeDetailsAreaOfLaw("LEGAL_HELP");

    Claim claim1 = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    claim1.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(claim1);
    createCalculatedFeeDetail(claim1, false, Instant.now().minus(1, ChronoUnit.DAYS));

    mockServerClient.clear(request().withPath(FEE_CALCULATION));
    jdbcTemplate.update("DELETE FROM audit.external_api_call_log");
  }

  @AfterEach
  void cleanUpAudit() {
    jdbcTemplate.update("DELETE FROM audit.external_api_call_log");
  }

  @Test
  @DisplayName("AC1: A successful reprice writes one row with verbatim payloads and the route ids")
  void successfulRepriceIsAudited() throws Exception {
    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(FSP_RESPONSE));

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, pricingChange());
    assertResponseStatus(result, HttpStatus.NO_CONTENT);

    Map<String, Object> row = singleAuditRow();
    assertThat(row.get("endpoint")).isEqualTo("/api/v1/fee-calculation");
    assertThat(row.get("http_method")).isEqualTo("POST");
    assertThat(row.get("http_status")).isEqualTo(200);
    assertThat(row.get("claim_id")).isEqualTo(CLAIM_1_ID);
    assertThat(row.get("submission_id")).isEqualTo(SUBMISSION_1_ID);
    assertThat(row.get("created_on")).isNotNull();

    HttpRequest[] recorded =
        mockServerClient.retrieveRecordedRequests(request().withPath(FEE_CALCULATION));
    assertThat(recorded).hasSize(1);
    assertThat(objectMapper.readTree(row.get("request_payload").toString()))
        .isEqualTo(objectMapper.readTree(recorded[0].getBodyAsString()));
    assertThat(objectMapper.readTree(row.get("response_payload").toString()))
        .isEqualTo(objectMapper.readTree(FSP_RESPONSE));
  }

  @Test
  @DisplayName("AC2: A 400 with a plain-text body is stored with a status and a raw wrapper")
  void validationOnFailureIsAudited() throws Exception {
    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION))
        .respond(
            response().withStatusCode(400).withBody("Invalid profit cost configuration combo"));

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, pricingChange());
    assertResponseStatus(result, HttpStatus.BAD_REQUEST);

    Map<String, Object> row = singleAuditRow();
    assertThat(row.get("http_status")).isEqualTo(400);
    assertThat(objectMapper.readTree(row.get("response_payload").toString()).get("raw").asText())
        .isEqualTo("Invalid profit cost configuration combo");
  }

  @Test
  @DisplayName(
      "AC6: A dropped connection is stored with the request body, null response and status")
  void droppedConnectionIsAudited() throws Exception {
    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION))
        .error(HttpError.error().withDropConnection(true));

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, pricingChange());
    assertResponseStatus(result, HttpStatus.SERVICE_UNAVAILABLE);

    Map<String, Object> row = singleAuditRow();
    assertThat(row.get("http_status")).isNull();
    assertThat(row.get("response_payload")).isNull();
    assertThat(objectMapper.readTree(row.get("request_payload").toString()).get("feeCode"))
        .isNotNull();
  }

  @Test
  @DisplayName("A 500 with no body is stored with the status and a null response")
  void serverErrorWithoutBodyIsAudited() throws Exception {
    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION))
        .respond(response().withStatusCode(500));

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, pricingChange());
    assertResponseStatus(result, HttpStatus.SERVICE_UNAVAILABLE);

    Map<String, Object> row = singleAuditRow();
    assertThat(row.get("http_status")).isEqualTo(500);
    assertThat(row.get("response_payload")).isNull();
  }

  @Test
  @DisplayName("AC4: A skipped FSP call writes no row")
  void skippedCallWritesNoRow() throws Exception {
    ClaimPatch patch = createBasePatch();
    patch.setVersion(1L);
    patch.setClientForename("NewForename");

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);
    assertResponseStatus(result, HttpStatus.NO_CONTENT);

    assertThat(auditRows()).isEmpty();
  }

  @Test
  @DisplayName("AC5: A failing audit write does not change the amendment outcome")
  void auditWriteFailureDoesNotAffectAmendmentOutcome() throws Exception {
    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(FSP_RESPONSE));

    // Make the audit fail as the table is gone
    jdbcTemplate.execute(
        "ALTER TABLE audit.external_api_call_log RENAME TO external_api_call_log_x");

    try {
      MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, pricingChange());
      assertResponseStatus(result, HttpStatus.NO_CONTENT);
      assertAmendmentCommittedGeneric(CLAIM_1_ID);
      assertAmendmentCommittedGeneric(CLAIM_1_ID);
    } finally {
      jdbcTemplate.execute(
          "ALTER TABLE audit.external_api_call_log_x RENAME TO external_api_call_log");
    }

    assertThat(auditRows()).isEmpty();
  }

  private ClaimPatch pricingChange() {
    ClaimPatch patch = createBasePatch();
    patch.setVersion(1L);
    patch.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));
    return patch;
  }

  private List<Map<String, Object>> auditRows() {
    return jdbcTemplate.queryForList(AUDIT_ROWS_SQL);
  }

  private Map<String, Object> singleAuditRow() {
    List<Map<String, Object>> rows = auditRows();
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }
}
