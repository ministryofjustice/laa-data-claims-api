package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.model.MediaType;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "laa.claims.api.external-api-audit.enabled=false")
@DisplayName("Amendment outbound API audit disabled Integration Test")
public class ClaimAmendmentOutboundAuditDisabledIntegrationTest
    extends AbstractAmendmentPatchIntegrationTest {

  private static final String FSP_RESPONSE =
      "{\"feeCode\":\"FEE-123\",\"schemeId\":\"SCHEME-TEST\",\"escapeCaseFlag\":false,"
          + "\"feeCalculation\":{\"totalAmount\":650.00,\"netProfitCostsAmount\":450.00,"
          + "\"vatIndicator\":true}}";

  @Autowired private JdbcTemplate jdbcTemplate;

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

  @Test
  @DisplayName("A successful call is made and the amendment commits, with no audit written")
  void successfulAmendmentWithNoAudit() throws Exception {
    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(FSP_RESPONSE));

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, pricingChange());
    assertResponseStatus(result, HttpStatus.NO_CONTENT);
    assertAmendmentCommittedGeneric(CLAIM_1_ID);

    mockServerClient.verify(request().withPath(FEE_CALCULATION), VerificationTimes.exactly(1));
    assertThat(auditRowCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("A failing call is not recorded in the audit")
  void failingAmendmentWithNoAudit() throws Exception {
    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION))
        .respond(response().withStatusCode(500));

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, pricingChange());
    assertResponseStatus(result, HttpStatus.SERVICE_UNAVAILABLE);

    mockServerClient.verify(request().withPath(FEE_CALCULATION), VerificationTimes.exactly(1));
    assertThat(auditRowCount()).isEqualTo(0);
  }

  private ClaimPatch pricingChange() {
    ClaimPatch patch = createBasePatch();
    patch.setVersion(1L);
    patch.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));
    return patch;
  }

  private int auditRowCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM audit.external_api_call_log", Integer.class);
  }
}
