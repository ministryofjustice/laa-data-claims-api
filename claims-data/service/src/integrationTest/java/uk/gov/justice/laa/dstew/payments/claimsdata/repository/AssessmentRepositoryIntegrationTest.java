package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_CREATED_BY_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

/**
 * Integration tests for assessed total aggregation queries.
 *
 * <p>These tests verify that assessed total amounts for submissions are calculated as the sum of
 * {@code assessedTotalInclVat} from the latest assessment for each claim in the submission.
 *
 * <p>The scenarios covered include:
 *
 * <ul>
 *   <li>returning {@code null} when a submission has no assessments
 *   <li>returning the value for a single assessed claim
 *   <li>summing assessed totals across multiple claims
 *   <li>counting only the most recent assessment when a claim has multiple assessments
 *   <li>returning zero when the latest assessments sum to zero
 *   <li>returning grouped assessed totals for multiple submissions
 * </ul>
 */
@Slf4j
@Isolated
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("AssessmentRepository Integration Test")
@Transactional
class AssessmentRepositoryIntegrationTest extends AbstractIntegrationTest {

  private static final Instant TENTH_APRIL_2024 =
      LocalDate.of(2024, 4, 10).atStartOfDay().toInstant(ZoneOffset.UTC);
  private static final Instant ELEVENTH_APRIL_2024 =
      LocalDate.of(2024, 4, 11).atStartOfDay().toInstant(ZoneOffset.UTC);
  private static final Instant TWELFTH_APRIL_2024 =
      LocalDate.of(2024, 4, 12).atStartOfDay().toInstant(ZoneOffset.UTC);

  private Submission submission;

  @BeforeEach
  void setup() {
    BulkSubmission bulkSubmission =
        BulkSubmission.builder()
            .id(BULK_SUBMISSION_ID)
            .data(new GetBulkSubmission200ResponseDetails())
            .status(BulkSubmissionStatus.READY_FOR_PARSING)
            .createdByUserId(BULK_SUBMISSION_CREATED_BY_USER_ID)
            .createdOn(TENTH_APRIL_2024)
            .updatedOn(TENTH_APRIL_2024)
            .build();
    bulkSubmissionRepository.saveAndFlush(bulkSubmission);

    submission =
        Submission.builder()
            .id(UUID.randomUUID())
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("office1")
            .submissionPeriod("JAN-25")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .crimeLowerScheduleNumber("office1/CRIME")
            .legalHelpSubmissionReference("office1/LEGAL")
            .mediationSubmissionReference("office1/MEDIATION")
            .isNilSubmission(false)
            .numberOfClaims(2)
            .createdByUserId(USER_ID)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .createdOn(TENTH_APRIL_2024)
            .build();
    submissionRepository.saveAndFlush(submission);
  }

  @Test
  @DisplayName("Should return null when submission has no assessments")
  void shouldReturnNullWhenSubmissionHasNoAssessments() {
    assertAssessedTotalIsNull();
  }

  @Test
  @DisplayName("Should return assessed total for one assessed claim")
  void shouldReturnAssessedTotalForOneAssessedClaim() {
    ClaimWithFee claim = createClaimWithFee();
    saveAssessment(claim, "12.34", TENTH_APRIL_2024);

    assertAssessedTotal("12.34");
  }

  @Test
  @DisplayName("Should sum latest assessments across multiple claims")
  void shouldSumLatestAssessmentsAcrossMultipleClaims() {
    ClaimWithFee claim1 = createClaimWithFee();
    ClaimWithFee claim2 = createClaimWithFee();

    saveAssessment(claim1, "10.00", TENTH_APRIL_2024);
    saveAssessment(claim2, "5.25", ELEVENTH_APRIL_2024);

    assertAssessedTotal("15.25");
  }

  @Test
  @DisplayName("Should only count the latest assessment for the same claim")
  void shouldOnlyCountLatestAssessmentForSameClaim() {
    ClaimWithFee claim = createClaimWithFee();

    saveAssessment(claim, "10.00", TENTH_APRIL_2024);
    saveAssessment(claim, "7.50", TWELFTH_APRIL_2024);

    assertAssessedTotal("7.50");
  }

  @Test
  @DisplayName("Should sum latest assessment per claim when any claim has multiple assessments")
  void shouldSumLatestAssessmentPerClaimWhenOneClaimHasMultipleAssessments() {
    ClaimWithFee claim1 = createClaimWithFee();
    ClaimWithFee claim2 = createClaimWithFee();

    saveAssessment(claim1, "10.00", TENTH_APRIL_2024);
    saveAssessment(claim1, "12.00", TWELFTH_APRIL_2024);

    saveAssessment(claim2, "15.00", TENTH_APRIL_2024);
    saveAssessment(claim2, "33.00", ELEVENTH_APRIL_2024);
    saveAssessment(claim2, "5.00", TWELFTH_APRIL_2024);

    assertAssessedTotal("17.00");
  }

  @Test
  @DisplayName("Should return zero when latest assessments sum to zero")
  void shouldReturnZeroWhenLatestAssessmentsSumToZero() {
    ClaimWithFee claim1 = createClaimWithFee();
    ClaimWithFee claim2 = createClaimWithFee();

    saveAssessment(claim1, "10.00", TENTH_APRIL_2024);
    saveAssessment(claim2, "-10.00", ELEVENTH_APRIL_2024);

    assertAssessedTotal("0.00");
  }

  @Test
  @DisplayName("Should return assessed totals for multiple submissions")
  void shouldReturnAssessedTotalsForMultipleSubmissions() {
    Submission submission2 = createSubmission("office2");

    ClaimWithFee submission1Claim = createClaimWithFee();
    ClaimWithFee submission2Claim = createClaimWithFee(submission2);

    saveAssessment(submission1Claim, "10.00", TENTH_APRIL_2024);
    saveAssessment(submission2Claim, "25.50", TENTH_APRIL_2024);

    Map<UUID, BigDecimal> totals =
        assessmentRepository
            .getAssessedTotalAmounts(List.of(submission.getId(), submission2.getId()))
            .stream()
            .collect(
                Collectors.toMap(
                    AssessmentRepository.AssessedTotalAmountProjection::getSubmissionId,
                    AssessmentRepository.AssessedTotalAmountProjection::getTotal));

    assertThat(totals)
        .hasSize(2)
        .containsEntry(submission.getId(), new BigDecimal("10.00"))
        .containsEntry(submission2.getId(), new BigDecimal("25.50"));
  }

  @Test
  @DisplayName("Should only count the latest assessment per claim across submissions")
  void shouldOnlyCountLatestAssessmentPerClaimAcrossSubmissions() {
    Submission submission2 = createSubmission("office2");

    ClaimWithFee submission1Claim = createClaimWithFee();
    ClaimWithFee submission2Claim = createClaimWithFee(submission2);

    saveAssessment(submission1Claim, "10.00", TENTH_APRIL_2024);
    saveAssessment(submission1Claim, "15.00", TWELFTH_APRIL_2024); // latest for submission 1

    saveAssessment(submission2Claim, "20.00", TENTH_APRIL_2024);
    saveAssessment(submission2Claim, "5.00", ELEVENTH_APRIL_2024); // latest for submission 2

    Map<UUID, BigDecimal> totals =
        assessmentRepository
            .getAssessedTotalAmounts(List.of(submission.getId(), submission2.getId()))
            .stream()
            .collect(
                Collectors.toMap(
                    AssessmentRepository.AssessedTotalAmountProjection::getSubmissionId,
                    AssessmentRepository.AssessedTotalAmountProjection::getTotal));

    assertThat(totals)
        .hasSize(2)
        .containsEntry(submission.getId(), new BigDecimal("15.00"))
        .containsEntry(submission2.getId(), new BigDecimal("5.00"));
  }

  @Test
  @DisplayName("Should not return submissions with no assessments in bulk query results")
  void shouldNotReturnSubmissionsWithNoAssessmentsInBulkQueryResults() {
    Submission submission2 = createSubmission("office2");

    ClaimWithFee submission1Claim = createClaimWithFee();
    saveAssessment(submission1Claim, "10.00", TENTH_APRIL_2024);

    Map<UUID, BigDecimal> totals =
        assessmentRepository
            .getAssessedTotalAmounts(List.of(submission.getId(), submission2.getId()))
            .stream()
            .collect(
                Collectors.toMap(
                    AssessmentRepository.AssessedTotalAmountProjection::getSubmissionId,
                    AssessmentRepository.AssessedTotalAmountProjection::getTotal));

    assertThat(totals)
        .hasSize(1)
        .containsEntry(submission.getId(), new BigDecimal("10.00"))
        .doesNotContainKey(submission2.getId());
  }

  @Test
  @DisplayName(
      "Should return only submissions with assessments and use latest assessment per claim")
  void shouldReturnOnlySubmissionsWithAssessmentsAndUseLatestAssessmentPerClaim() {
    Submission submission2 = createSubmission("office2");
    Submission submission3 = createSubmission("office3");

    // submission 1 has assessments on two claims
    ClaimWithFee submission1Claim1 = createClaimWithFee();
    ClaimWithFee submission1Claim2 = createClaimWithFee();

    saveAssessment(submission1Claim1, "10.00", TENTH_APRIL_2024);
    saveAssessment(submission1Claim1, "15.00", TWELFTH_APRIL_2024); // latest for claim 1
    saveAssessment(submission1Claim2, "7.50", ELEVENTH_APRIL_2024);

    // submission 2 has one assessed claim with multiple assessments
    ClaimWithFee submission2Claim = createClaimWithFee(submission2);

    saveAssessment(submission2Claim, "20.00", TENTH_APRIL_2024);
    saveAssessment(submission2Claim, "5.00", ELEVENTH_APRIL_2024); // latest for submission 2

    // submission 3 has no assessments

    Map<UUID, BigDecimal> totals =
        assessmentRepository
            .getAssessedTotalAmounts(
                List.of(submission.getId(), submission2.getId(), submission3.getId()))
            .stream()
            .collect(
                Collectors.toMap(
                    AssessmentRepository.AssessedTotalAmountProjection::getSubmissionId,
                    AssessmentRepository.AssessedTotalAmountProjection::getTotal));

    assertThat(totals)
        .hasSize(2)
        .containsEntry(submission.getId(), new BigDecimal("22.50"))
        .containsEntry(submission2.getId(), new BigDecimal("5.00"))
        .doesNotContainKey(submission3.getId());
  }

  private void assertAssessedTotal(String expected) {
    BigDecimal result = assessmentRepository.getAssessedTotalAmount(submission.getId());
    assertThat(result).isEqualByComparingTo(expected);
  }

  private void assertAssessedTotalIsNull() {
    BigDecimal result = assessmentRepository.getAssessedTotalAmount(submission.getId());
    assertThat(result).isNull();
  }

  private Submission createSubmission(String officeAccountNumber) {
    Submission newSubmission =
        Submission.builder()
            .id(UUID.randomUUID())
            .bulkSubmissionId(submission.getBulkSubmissionId())
            .officeAccountNumber(officeAccountNumber)
            .submissionPeriod("JAN-25")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .crimeLowerScheduleNumber(officeAccountNumber + "/CRIME")
            .legalHelpSubmissionReference(officeAccountNumber + "/LEGAL")
            .mediationSubmissionReference(officeAccountNumber + "/MEDIATION")
            .isNilSubmission(false)
            .numberOfClaims(2)
            .createdByUserId(USER_ID)
            .providerUserId(submission.getProviderUserId())
            .createdOn(TENTH_APRIL_2024)
            .build();

    return submissionRepository.saveAndFlush(newSubmission);
  }

  private ClaimWithFee createClaimWithFee() {
    return createClaimWithFee(submission);
  }

  private ClaimWithFee createClaimWithFee(Submission forSubmission) {
    Claim claim =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(UUID.randomUUID())
                .submission(forSubmission)
                .hasAssessment(true)
                .matterTypeCode("MTC-333")
                .status(ClaimStatus.READY_TO_PROCESS)
                .lineNumber(1)
                .createdByUserId(USER_ID)
                .build());

    ClaimSummaryFee claimSummaryFee =
        claimSummaryFeeRepository.saveAndFlush(
            ClaimSummaryFee.builder()
                .id(UUID.randomUUID())
                .createdByUserId(USER_ID)
                .claim(claim)
                .build());

    return ClaimWithFee.builder().claim(claim).claimSummaryFee(claimSummaryFee).build();
  }

  private void saveAssessment(
      ClaimWithFee claimWithFee, String assessedTotalInclVat, Instant createdOn) {
    Assessment assessment =
        Assessment.builder()
            .id(UUID.randomUUID())
            .claim(claimWithFee.claim())
            .claimSummaryFee(claimWithFee.claimSummaryFee())
            .assessedTotalVat(BigDecimal.ZERO)
            .assessedTotalInclVat(new BigDecimal(assessedTotalInclVat))
            .allowedTotalVat(BigDecimal.ZERO)
            .allowedTotalInclVat(BigDecimal.ZERO)
            .createdByUserId(USER_ID)
            .updatedByUserId(USER_ID)
            .assessmentType(AssessmentType.ESCAPE_CASE_ASSESSMENT)
            .createdOn(createdOn)
            .assessmentReason("Reason for Assessment")
            .build();

    assessmentRepository.saveAndFlush(assessment);
  }

  @Builder
  private record ClaimWithFee(Claim claim, ClaimSummaryFee claimSummaryFee) {}
}
