package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "laa.claims.api.external-api-audit.enabled=false")
@DisplayName("Amendment library-originated outbound API audit disabled integration tests")
public class ClaimAmendmentLibraryCallAuditDisabledIntegrationTest
    extends AbstractAmendmentPatchIntegrationTest {

  private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final LocalDate CASE_START = LocalDate.of(2099, Month.JANUARY, 1);
  private static final LocalDate AMENDED_CASE_START = LocalDate.of(2099, Month.JUNE, 1);

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() throws Exception {
    stubFeeSchemeEndpoints();
    stubFeeDetailsAreaOfLaw("LEGAL_HELP");
    stubProviderSchedules("provider-details/get-firm-schedules-wide-window-200.json");
    jdbcTemplate.update("DELETE FROM audit.external_api_call_log");
  }

  @Test
  @DisplayName("The library's calls are still made, and no audit is written")
  void libraryCallsAreMadeWithNoAudit() throws Exception {
    UUID submissionId = createSubmissionWithUniqueOffice();
    Claim claim =
        createAmendableClaim(
            submissionId, builder -> builder.feeCode("AUDOFF1").caseStartDate(CASE_START));
    createBaselineCalculatedFeeDetail(claim);

    ClaimPatch patch = createBasePatch();
    patch.setCaseStartDate(AMENDED_CASE_START.format(API_DATE));

    performPatch(submissionId, claim.getId(), patch);

    verifyProviderSchedulesCalled(VerificationTimes.exactly(1));
    mockServerClient.verify(request().withPath(FEE_DETAILS + ".*"), VerificationTimes.exactly(1));

    assertThat(auditRows()).isEmpty();
  }

  private List<Map<String, Object>> auditRows() {
    return jdbcTemplate.queryForList("SELECT * FROM audit.external_api_call_log");
  }
}
