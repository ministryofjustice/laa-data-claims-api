package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AssessmentNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.AssessmentMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Service containing business logic for handling assessments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentService {

  private final ClaimRepository claimRepository;
  private final AssessmentRepository assessmentRepository;
  private final AssessmentMapper assessmentMapper;
  private final ClaimValidationService claimValidationService;

  /**
   * Create an assessment for a claim.
   *
   * @param claimId claim identifier
   * @param request request payload
   * @return identifier of the created assessment
   */
  @Transactional
  public UUID createAssessment(UUID claimId, AssessmentPost request) {
    claimValidationService.validateUserId(request.getCreatedByUserId());
    claimValidationService.validateAssessmentReason(request.getAssessmentReason());

    Claim claim = claimValidationService.getValidClaimOrThrow(claimId);
    ClaimSummaryFee claimSummaryFee =
        claimValidationService.getClaimSummaryFeeByIdOrThrow(request.getClaimSummaryFeeId());

    claimValidationService.ensureAssessmentTypeIsNotVoid(request.getAssessmentType());
    updateClaimAssessmentStatus(claim);

    Assessment assessment = assessmentMapper.toAssessment(request);

    setCommonFields(
        assessment,
        claim,
        claimSummaryFee,
        request.getCreatedByUserId(),
        request.getAssessmentReason(),
        AssessmentType.ESCAPE_CASE_ASSESSMENT);

    return assessmentRepository.save(assessment).getId();
  }

  /**
   * Updates the claim assessment status when an assessment is first created for a claim.
   *
   * <p>This method checks whether the claim has been assessed before. If the claim has not been
   * assessed ({@link Claim#isHasAssessment()} returns false), it updates the claim's assessment
   * status to true and logs the result.
   *
   * <p>This is typically called during assessment creation to mark the claim as assessed once the
   * first assessment is added.
   *
   * @param claim the claim to update; must not be null
   */
  private void updateClaimAssessmentStatus(Claim claim) {
    if (!claim.isHasAssessment()) {
      int noOfClaimsUpdated = claimRepository.updateAssessmentStatus(claim.getId(), true);
      log.info(
          "Number of claims updated with assessed status: {} Claim id: {}",
          noOfClaimsUpdated,
          claim.getId());
    }
  }

  /**
   * Retrieves an {@link AssessmentGet} representation of an assessment for a given claim.
   *
   * <p>This method looks up an {@link Assessment} by its unique {@code assessmentId} and associated
   * {@code claimId}. If no matching assessment is found, an {@link AssessmentNotFoundException} is
   * thrown.
   *
   * @param claimId      the unique identifier of the claim
   * @param assessmentId the unique identifier of the assessment
   * @return an {@link AssessmentGet} DTO containing assessment details
   * @throws AssessmentNotFoundException if the assessment does not exist for the given claim
   */
  public AssessmentGet getAssessment(UUID claimId, UUID assessmentId) {
    var assessment =
        assessmentRepository
            .findByIdAndClaimId(assessmentId, claimId)
            .orElseThrow(
                () ->
                    new AssessmentNotFoundException(
                        String.format(
                            "No Assessment found with id: %s and claim id: %s",
                            assessmentId, claimId)));

    return assessmentMapper.toAssessmentGet(assessment);
  }

  /**
   * Retrieves all assessments associated with the given claim ID. This method performs the
   * following steps:
   *
   * <ul>
   *   <li>Validates that the provided {@code claimId} is not null.
   *   <li>Fetches assessments from the repository ordered by creation date (descending).
   *   <li>If no assessments are found, throws {@link AssessmentNotFoundException}.
   *   <li>Maps the list of assessments to an {@link AssessmentResultSet} using the mapper.
   * </ul>
   *
   * @param claimId the unique identifier of the claim; must not be {@code null}.
   * @return an {@link AssessmentResultSet} containing all assessments for the claim.
   * @throws IllegalArgumentException    if {@code claimId} is {@code null}.
   * @throws AssessmentNotFoundException if no assessments exist for the given claim ID.
   */
  @Transactional(readOnly = true)
  public AssessmentResultSet getAssessmentsByClaimId(@NotNull UUID claimId, Pageable pageable) {
    var assessments = assessmentRepository.findByClaimId(claimId, pageable);
    if (assessments.isEmpty()) {
      throw new AssessmentNotFoundException(
          String.format("No assessments found for claimId: %s", claimId));
    }
    return assessmentMapper.toAssessmentResultSet(assessments);
  }

  /**
   * Creates a new {@link Assessment} of type VOID and initializes it with specific parameters. All
   * monetary fields are set to zero and common fields are populated using the provided arguments.
   *
   * @param assessmentReason the reason for creating the void assessment
   * @param claim            the associated {@link Claim} instance
   * @param claimSummaryFee  the related {@link ClaimSummaryFee} instance
   * @param createdByUserId  the UUID of the user creating the assessment
   * @return a new {@link Assessment} of type VOID
   */
  public Assessment createVoidAssessment(
      String assessmentReason, Claim claim, ClaimSummaryFee claimSummaryFee, UUID createdByUserId) {

    Assessment assessment = new Assessment();
    setCommonFields(
        assessment,
        claim,
        claimSummaryFee,
        createdByUserId.toString(),
        assessmentReason,
        AssessmentType.VOID);
    setMonetaryFieldsToZero(assessment);

    return assessment;
  }

  /**
   * Sets all monetary fields of the given {@link Assessment} to zero.
   *
   * @param assessment the {@link Assessment} whose monetary fields will be reset to zero
   */
  private void setMonetaryFieldsToZero(Assessment assessment) {
    BigDecimal zero = BigDecimal.ZERO;

    assessment.setFixedFeeAmount(zero);
    assessment.setNetTravelCostsAmount(zero);
    assessment.setNetWaitingCostsAmount(zero);
    assessment.setNetProfitCostsAmount(zero);
    assessment.setDisbursementAmount(zero);
    assessment.setDisbursementVatAmount(zero);
    assessment.setNetCostOfCounselAmount(zero);
    assessment.setDetentionTravelAndWaitingCostsAmount(zero);
    assessment.setBoltOnAdjournedHearingFee(zero);
    assessment.setJrFormFillingAmount(zero);
    assessment.setBoltOnCmrhOralFee(zero);
    assessment.setBoltOnCmrhTelephoneFee(zero);
    assessment.setBoltOnSubstantiveHearingFee(zero);
    assessment.setBoltOnHomeOfficeInterviewFee(zero);
    assessment.setAssessedTotalVat(zero);
    assessment.setAssessedTotalInclVat(zero);
    assessment.setAllowedTotalVat(zero);
    assessment.setAllowedTotalInclVat(zero);
  }

  /**
   * Populates common fields in the given {@link Assessment} based on the provided parameters.
   *
   * @param assessment       the {@link Assessment} to be updated
   * @param claim            the associated {@link Claim} instance
   * @param claimSummaryFee  the related {@link ClaimSummaryFee} instance
   * @param createdByUserId  the ID of the user creating the assessment
   * @param assessmentReason the reason for the assessment
   * @param assessmentType   the type of the assessment (e.g., VOID)
   */
  protected void setCommonFields(
      Assessment assessment,
      Claim claim,
      ClaimSummaryFee claimSummaryFee,
      String createdByUserId,
      String assessmentReason,
      AssessmentType assessmentType) {
    assessment.setId(Uuid7.timeBasedUuid());
    assessment.setClaim(claim);
    assessment.setClaimSummaryFee(claimSummaryFee);
    assessment.setCreatedByUserId(createdByUserId);
    assessment.setUpdatedByUserId(createdByUserId);
    assessment.setAssessmentReason(assessmentReason);
    assessment.setAssessmentType(assessmentType);
  }

  /**
   * Returns the assessed total amount for the given submission.
   *
   * <p>The value is calculated as the sum of {@code assessedTotalInclVat} from the most recently
   * created assessment for each claim in the submission that has one or more assessments.
   *
   * <p>Claims with no assessments do not contribute to the total. If no assessments exist for any
   * claim in the submission, this method returns {@code null}.
   *
   * @param submissionId the unique identifier of the submission
   * @return the summed assessed total amount for the submission, or {@code null} if no assessments
   * exist ``
   */
  public BigDecimal getAssessedTotalAmount(UUID submissionId) {
    return assessmentRepository.getAssessedTotalAmount(submissionId);
  }

  public Map<UUID, BigDecimal> getAssessedTotalAmounts(List<UUID> submissionIds) {
    if (CollectionUtils.isEmpty(submissionIds)) {
      return Map.of();
    }

    return assessmentRepository.getAssessedTotalAmounts(submissionIds)
        .stream()
        .collect(Collectors.toMap(
            AssessmentRepository.AssessedTotalAmountProjection::getSubmissionId,
            AssessmentRepository.AssessedTotalAmountProjection::getTotal
        ));
  }
}
