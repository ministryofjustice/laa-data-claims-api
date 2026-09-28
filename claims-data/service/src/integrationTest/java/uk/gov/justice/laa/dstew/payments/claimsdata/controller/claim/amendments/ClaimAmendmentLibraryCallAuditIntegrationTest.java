package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Amendment library-originated outbound API audit integration tests")
public class ClaimAmendmentLibraryCallAuditIntegrationTest
    extends AbstractAmendmentPatchIntegrationTest {

  private static final AtomicInteger FEE_SEQ = new AtomicInteger();
  private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final LocalDate CASE_START = LocalDate.of(2099, Month.JANUARY, 1);
  private static final LocalDate AMENDED_CASE_START = LocalDate.of(2099, Month.JUNE, 1);

  private static final String SCHEDULES_PREFIX = "/api/v1/provider-offices/";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws Exception {
    stubFeeSchemeEndpoints();
    stubFeeDetailsAreaOfLaw("LEGAL_HELP");
    stubProviderSchedules("provider-details/get-firm-schedules-wide-window-200.json");
    cleanUpAudit();
  }

  @AfterEach
  void cleanUpAudit() {
    jdbcTemplate.update("DELETE FROM audit.external_api_call_log");
  }

  @Test
  @DisplayName("AC3: The library's schedules lookup is recorded with a route's claim ids")
  void scheduleLookupIsAudited() throws Exception {
    UUID submissionId = createSubmissionWithUniqueOffice();
    Claim claim = amendableClaim(submissionId, uniqueFeeCode());

    performPatch(submissionId, claim.getId(), caseStartDateChange());

    verifyProviderSchedulesCalled(VerificationTimes.exactly(1));

    List<Map<String, Object>> rows = rowsFor("PROVIDER_DETAILS_API");
    assertThat(rows).hasSize(1);

    Map<String, Object> row = rows.getFirst();
    assertThat(row.get("http_method")).isEqualTo("GET");
    assertThat(row.get("http_status")).isEqualTo(200);
    assertThat(row.get("claim_id")).isEqualTo(claim.getId());
    assertThat(row.get("submission_id")).isEqualTo(submissionId);
    assertThat(row.get("created_on")).isNotNull();
    assertThat(row.get("response_payload")).isNotNull();
    assertThat((String) row.get("endpoint")).startsWith(SCHEDULES_PREFIX).contains("/schedules");

    JsonNode requestPayload = objectMapper.readTree(row.get("request_payload").toString());
    assertThat(requestPayload.get("path").asText()).startsWith(SCHEDULES_PREFIX);
    assertThat(requestPayload.has("query")).isTrue();
  }

  @Test
  @DisplayName("The library's fee-details lookup is recorded under FEE_SCHEME_PLATFORM system type")
  void feeDetailsLookupIsAudited() throws Exception {
    UUID submissionId = createSubmissionWithUniqueOffice();
    String feeCode = uniqueFeeCode();
    Claim claim = amendableClaim(submissionId, feeCode);

    performPatch(submissionId, claim.getId(), caseStartDateChange());

    mockServerClient.verify(request().withPath(FEE_DETAILS + ".*"), VerificationTimes.atLeast(1));

    List<Map<String, Object>> rows = rowsMatching("FEE_SCHEME_PLATFORM", FEE_DETAILS);
    assertThat(rows)
        .extracting(row -> (String) row.get("endpoint"))
        .contains(FEE_DETAILS + feeCode);
    assertThat(rows)
        .allSatisfy(
            row -> {
              assertThat(row.get("http_method")).isEqualTo("GET");
              assertThat(row.get("claim_id")).isEqualTo(claim.getId());
              assertThat(row.get("submission_id")).isEqualTo(submissionId);
            });
  }

  @Test
  @DisplayName("AC4: A second claim served from the caches writes no additional lookup rows")
  void cachedResponsesWriteNoAdditionalRows() throws Exception {
    UUID submissionId = createSubmissionWithUniqueOffice();
    String sharedFeeCode = uniqueFeeCode();
    Claim firstClaim = amendableClaim(submissionId, sharedFeeCode);
    Claim secondClaim = amendableClaim(submissionId, sharedFeeCode);

    performPatch(submissionId, firstClaim.getId(), caseStartDateChange());
    assertThat(rowsFor("PROVIDER_DETAILS_API")).hasSize(1);
    performPatch(submissionId, secondClaim.getId(), caseStartDateChange());

    verifyProviderSchedulesCalled(VerificationTimes.exactly(1));

    List<Map<String, Object>> rows = rowsFor("PROVIDER_DETAILS_API");
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().get("claim_id")).isEqualTo(firstClaim.getId());
    assertThat(rowsMatching("FEE_SCHEME_PLATFORM", FEE_DETAILS)).hasSize(1);
  }

  private Claim amendableClaim(UUID submissionId, String feeCode) {
    Claim claim =
        createAmendableClaim(
            submissionId, builder -> builder.feeCode(feeCode).caseStartDate(CASE_START));
    createBaselineCalculatedFeeDetail(claim);
    return claim;
  }

  private String uniqueFeeCode() {
    return "AUDFEE" + FEE_SEQ.incrementAndGet();
  }

  private ClaimPatch caseStartDateChange() {
    ClaimPatch patch = createBasePatch();
    patch.setCaseStartDate(AMENDED_CASE_START.format(API_DATE));
    return patch;
  }

  private List<Map<String, Object>> rowsFor(String systemType) {
    return jdbcTemplate.queryForList(
        "SELECT * FROM audit.external_api_call_log WHERE system_type = ? ORDER BY created_on",
        systemType);
  }

  private List<Map<String, Object>> rowsMatching(String systemType, String endpointPrefix) {
    return jdbcTemplate.queryForList(
        "SELECT * FROM audit.external_api_call_log "
            + "WHERE system_type = ? AND endpoint LIKE ? ORDER BY created_on",
        systemType,
        endpointPrefix + "%");
  }
}
