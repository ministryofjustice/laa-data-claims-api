package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AssessmentNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.AssessmentMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/** Service containing business logic for handling assessments. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentService {

  private final ClaimRepository claimRepository;
  private final ClaimSummaryFeeRepository claimSummaryFeeRepository;
  private final AssessmentRepository assessmentRepository;
  private final AssessmentMapper assessmentMapper;

  /**
   * Create an assessment for a claim.
   *
   * @param claimId claim identifier
   * @param request request payload
   * @return identifier of the created assessment
   */
  @Transactional
  public UUID createAssessment(UUID claimId, AssessmentPost request) {
    UUID claimSummaryFeeId = request.getClaimSummaryFeeId();

    if (!claimRepository.existsById(claimId)) {
      throw new ClaimNotFoundException(String.format("No entity found with id: %s", claimId));
    }
    Claim claim = claimRepository.getReferenceById(claimId);

    if (!claimSummaryFeeRepository.existsById(claimSummaryFeeId)) {
      throw new ClaimSummaryFeeNotFoundException(
          String.format("No entity found with id: %s", claimSummaryFeeId));
    }

    updateClaimAssessmentStatus(claim);
    ClaimSummaryFee claimSummaryFee = claimSummaryFeeRepository.getReferenceById(claimSummaryFeeId);

    Assessment assessment = assessmentMapper.toAssessment(request);
    assessment.setId(Uuid7.timeBasedUuid());
    assessment.setClaim(claim);
    assessment.setClaimSummaryFee(claimSummaryFee);

    return assessmentRepository.save(assessment).getId();
  }

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
   * @param claimId the unique identifier of the claim
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
   * @throws IllegalArgumentException if {@code claimId} is {@code null}.
   * @throws AssessmentNotFoundException if no assessments exist for the given claim ID.
   */
  @Transactional(readOnly = true)
  public AssessmentResultSet getAssessmentsByClaimId(@NotNull UUID claimId) {
    var assessments = assessmentRepository.findByClaimIdOrderByCreatedOnDesc(claimId);
    if (assessments.isEmpty()) {
      throw new AssessmentNotFoundException(
          String.format("No assessments found for claimId: %s", claimId));
    }
    return AssessmentResultSet.builder()
        .assessments(assessments.stream().map(assessmentMapper::toAssessmentGet).toList())
        .build();
  }
}
