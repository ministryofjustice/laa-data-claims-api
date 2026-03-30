package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_CREATED_BY_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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

  @PersistenceContext
  private EntityManager entityManager;

  private Submission submission;

  @BeforeEach
  void setup() {
    var bulkSubmission =
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
            .previousSubmissionId(null)
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
    BigDecimal result = assessmentRepository.getAssessedTotalAmount(submission.getId());

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("Should return assessed total for one assessed claim")
  void shouldReturnAssessedTotalForOneAssessedClaim() {
    Claim claim = saveClaim(submission, true);
    ClaimSummaryFee claimSummaryFee = saveClaimSummaryFee(claim);

    saveAssessment(claim, claimSummaryFee, "12.34", TENTH_APRIL_2024);

    BigDecimal result = assessmentRepository.getAssessedTotalAmount(submission.getId());

    assertThat(result).isEqualByComparingTo("12.34");
  }

  @Test
  @DisplayName("Should sum latest assessments across multiple claims")
  void shouldSumLatestAssessmentsAcrossMultipleClaims() {
    Claim claim1 = saveClaim(submission, true);
    Claim claim2 = saveClaim(submission, true);

    ClaimSummaryFee claim1SummaryFee = saveClaimSummaryFee(claim1);
    ClaimSummaryFee claim2SummaryFee = saveClaimSummaryFee(claim2);

    saveAssessment(claim1, claim1SummaryFee, "10.00", TENTH_APRIL_2024);
    saveAssessment(claim2, claim2SummaryFee, "5.25", ELEVENTH_APRIL_2024);

    BigDecimal result = assessmentRepository.getAssessedTotalAmount(submission.getId());

    assertThat(result).isEqualByComparingTo("15.25");
  }

  @Test
  @DisplayName("Should only count the latest assessment for the same claim")
  void shouldOnlyCountLatestAssessmentForSameClaim() {
    Claim claim = saveClaim(submission, true);
    ClaimSummaryFee claimSummaryFee = saveClaimSummaryFee(claim);

    saveAssessment(claim, claimSummaryFee, "10.00", TENTH_APRIL_2024);
    saveAssessment(claim, claimSummaryFee, "7.50", TWELFTH_APRIL_2024);

    BigDecimal result = assessmentRepository.getAssessedTotalAmount(submission.getId());

    assertThat(result).isEqualByComparingTo("7.50");
  }

  @Test
  @DisplayName("Should sum latest assessment per claim when one claim has multiple assessments")
  void shouldSumLatestAssessmentPerClaimWhenOneClaimHasMultipleAssessments() {
    Claim claim1 = saveClaim(submission, true);
    Claim claim2 = saveClaim(submission, true);

    ClaimSummaryFee claim1SummaryFee = saveClaimSummaryFee(claim1);
    ClaimSummaryFee claim2SummaryFee = saveClaimSummaryFee(claim2);

    saveAssessment(claim1, claim1SummaryFee, "10.00", TENTH_APRIL_2024);
    saveAssessment(claim1, claim1SummaryFee, "12.00", TWELFTH_APRIL_2024); // latest for claim1
    saveAssessment(claim2, claim2SummaryFee, "5.00", ELEVENTH_APRIL_2024);

    BigDecimal result = assessmentRepository.getAssessedTotalAmount(submission.getId());

    assertThat(result).isEqualByComparingTo("17.00");
  }

  @Test
  @DisplayName("Should return zero when latest assessments sum to zero")
  void shouldReturnZeroWhenLatestAssessmentsSumToZero() {
    Claim claim1 = saveClaim(submission, true);
    Claim claim2 = saveClaim(submission, true);

    ClaimSummaryFee claim1SummaryFee = saveClaimSummaryFee(claim1);
    ClaimSummaryFee claim2SummaryFee = saveClaimSummaryFee(claim2);

    saveAssessment(claim1, claim1SummaryFee, "10.00", TENTH_APRIL_2024);
    saveAssessment(claim2, claim2SummaryFee, "-10.00", ELEVENTH_APRIL_2024);

    BigDecimal result = assessmentRepository.getAssessedTotalAmount(submission.getId());

    assertThat(result).isNotNull();
    assertThat(result).isEqualByComparingTo("0.00");
  }

  private Claim saveClaim(Submission submission, boolean hasAssessment) {
    Claim claim =
        Claim.builder()
            .id(UUID.randomUUID())
            .submission(submission)
            .hasAssessment(hasAssessment)
            .matterTypeCode("MTC-333")
            .status(ClaimStatus.READY_TO_PROCESS)
            .lineNumber(1)
            .createdByUserId(USER_ID)
            .build();

    return claimRepository.save(claim);
  }

  private ClaimSummaryFee saveClaimSummaryFee(Claim claim) {
    ClaimSummaryFee claimSummaryFee =
        ClaimSummaryFee.builder()
            .id(UUID.randomUUID())
            .createdByUserId(USER_ID)
            .claim(claim)
            .build();

    return claimSummaryFeeRepository.save(claimSummaryFee);
  }

  private void saveAssessment(
      Claim claim,
      ClaimSummaryFee claimSummaryFee,
      String assessedTotalInclVat,
      Instant createdOn) {

    Assessment assessment =
        Assessment.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .claimSummaryFee(claimSummaryFee)
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
}