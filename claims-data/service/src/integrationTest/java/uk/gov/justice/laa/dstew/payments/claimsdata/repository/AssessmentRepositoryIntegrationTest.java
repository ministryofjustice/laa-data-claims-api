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
import java.util.UUID;
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
 * Integration tests for {@link AssessmentRepository#getAssessedTotalAmount(UUID)}.
 *
 * <p>These tests verify that the assessed total amount for a submission is calculated as the sum of
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
    AssessedClaim claim = createAssessedClaim();

    saveAssessment(claim, "12.34", TENTH_APRIL_2024);

    assertAssessedTotal("12.34");
  }

  @Test
  @DisplayName("Should sum latest assessments across multiple claims")
  void shouldSumLatestAssessmentsAcrossMultipleClaims() {
    AssessedClaim claim1 = createAssessedClaim();
    AssessedClaim claim2 = createAssessedClaim();

    saveAssessment(claim1, "10.00", TENTH_APRIL_2024);
    saveAssessment(claim2, "5.25", ELEVENTH_APRIL_2024);

    assertAssessedTotal("15.25");
  }

  @Test
  @DisplayName("Should only count the latest assessment for the same claim")
  void shouldOnlyCountLatestAssessmentForSameClaim() {
    AssessedClaim claim = createAssessedClaim();

    saveAssessment(claim, "10.00", TENTH_APRIL_2024);
    saveAssessment(claim, "7.50", TWELFTH_APRIL_2024);

    assertAssessedTotal("7.50");
  }

  @Test
  @DisplayName("Should sum latest assessment per claim when any claim has multiple assessments")
  void shouldSumLatestAssessmentPerClaimWhenOneClaimHasMultipleAssessments() {
    AssessedClaim claim1 = createAssessedClaim();
    AssessedClaim claim2 = createAssessedClaim();

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
    AssessedClaim claim1 = createAssessedClaim();
    AssessedClaim claim2 = createAssessedClaim();

    saveAssessment(claim1, "10.00", TENTH_APRIL_2024);
    saveAssessment(claim2, "-10.00", ELEVENTH_APRIL_2024);

    assertAssessedTotal("0.00");
  }

  private void assertAssessedTotal(String expected) {
    BigDecimal result = assessmentRepository.getAssessedTotalAmount(submission.getId());
    assertThat(result).isEqualByComparingTo(expected);
  }

  @Test
  @DisplayName("Should return assessed totals for multiple submissions")
  void shouldReturnAssessedTotalsForMultipleSubmissions() {
    // Second submission
    Submission submission2 =
        Submission.builder()
            .id(UUID.randomUUID())
            .bulkSubmissionId(submission.getBulkSubmissionId())
            .officeAccountNumber("office2")
            .submissionPeriod("JAN-25")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(USER_ID)
            .providerUserId(submission.getProviderUserId())
            .createdOn(TENTH_APRIL_2024)
            .build();

    submissionRepository.saveAndFlush(submission2);

    // Submission 1 assessments
    AssessedClaim s1Claim1 = createAssessedClaim();
    saveAssessment(s1Claim1, "10.00", TENTH_APRIL_2024);

    // Submission 2 assessments
    Claim claimForSubmission2 =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(UUID.randomUUID())
                .submission(submission2)
                .hasAssessment(true)
                .matterTypeCode("MTC-444")
                .status(ClaimStatus.READY_TO_PROCESS)
                .lineNumber(1)
                .createdByUserId(USER_ID)
                .build());

    ClaimSummaryFee feeForSubmission2 =
        claimSummaryFeeRepository.saveAndFlush(
            ClaimSummaryFee.builder()
                .id(UUID.randomUUID())
                .claim(claimForSubmission2)
                .createdByUserId(USER_ID)
                .build());

    saveAssessment(
        AssessedClaim.builder()
            .claim(claimForSubmission2)
            .claimSummaryFee(feeForSubmission2)
            .build(),
        "25.50",
        TENTH_APRIL_2024);

    // Execute
    var results =
        assessmentRepository.getAssessedTotalAmounts(
            List.of(submission.getId(), submission2.getId()));

    // Convert to Map for easy assertion
    var totals =
        results.stream()
            .collect(java.util.stream.Collectors.toMap(r -> (UUID) r[0], r -> (BigDecimal) r[1]));

    // Assert
    assertThat(totals)
        .containsEntry(submission.getId(), new BigDecimal("10.00"))
        .containsEntry(submission2.getId(), new BigDecimal("25.50"));
  }

  @Test
  @DisplayName("Should only count the latest assessment per claim across submissions")
  void shouldOnlyCountLatestAssessmentPerClaimAcrossSubmissions() {
    AssessedClaim claim1 = createAssessedClaim();
    saveAssessment(claim1, "10.00", TENTH_APRIL_2024);
    saveAssessment(claim1, "15.00", TWELFTH_APRIL_2024); // latest

    var results = assessmentRepository.getAssessedTotalAmounts(List.of(submission.getId()));

    assertThat(results).hasSize(1);
    assertThat((BigDecimal) results.getFirst()[1]).isEqualByComparingTo("15.00");
  }

  private void assertAssessedTotalIsNull() {
    BigDecimal result = assessmentRepository.getAssessedTotalAmount(submission.getId());
    assertThat(result).isNull();
  }

  private AssessedClaim createAssessedClaim() {
    Claim claim =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(UUID.randomUUID())
                .submission(submission)
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

    return AssessedClaim.builder().claim(claim).claimSummaryFee(claimSummaryFee).build();
  }

  private void saveAssessment(
      AssessedClaim assessedClaim, String assessedTotalInclVat, Instant createdOn) {

    Assessment assessment =
        Assessment.builder()
            .id(UUID.randomUUID())
            .claim(assessedClaim.claim())
            .claimSummaryFee(assessedClaim.claimSummaryFee())
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
  private record AssessedClaim(Claim claim, ClaimSummaryFee claimSummaryFee) {}
}
