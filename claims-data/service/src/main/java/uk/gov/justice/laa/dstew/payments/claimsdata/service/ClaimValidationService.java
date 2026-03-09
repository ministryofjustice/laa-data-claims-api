package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AssessmentInvalidUserException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;

import java.util.UUID;

import static uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimService.CLAIM_PATCH_VOID_INVALID_OPERATION;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimValidationService {

  public static final String NO_CLAIM_FOUND_WITH_ID_ERROR = "No Claim found with id: %s";
  public static final String CLAIM_WITH_ID_DOES_NOT_HAVE_VALID_STATUS_ERROR = "Claim with id: %s does not have VALID status";
  public static final String CLAIM_IS_ALREADY_VOID_STATUS_ERROR = "Claim with id: %s is already in VOID status";
  public static final String NO_SUMMARY_FEE_FOR_CLAIM_ID_ERROR = "No Claim summary fee for claimId %s";
  public static final String NO_CLAIM_SUMMARY_FEE_FOUND_WITH_ID_ERROR = "No Claim Summary Fee found with id: %s";
  public static final String CLAIM_ID_MUST_BE_PROVIDED_ERROR = "claimId must be provided";
  public static final String CREATED_BY_USER_ID_MUST_BE_PROVIDED_ERROR = "createdByUserId must be provided";
  public static final String ASSESSMENT_REASON_MUST_BE_PROVIDED_ERROR = "assessmentReason must be provided";

  private final ClaimRepository claimRepository;
  private final ClaimSummaryFeeRepository claimSummaryFeeRepository;

  public void validateVoidClaimParameters(UUID claimId, UUID createdByUserId, String assessmentReason) {
    if (claimId == null) {
      throw new ClaimBadRequestException(CLAIM_ID_MUST_BE_PROVIDED_ERROR);
    }

    if (createdByUserId == null) {
      throw new ClaimBadRequestException(CREATED_BY_USER_ID_MUST_BE_PROVIDED_ERROR);
    }

    validateUserId(createdByUserId.toString());
    if (!StringUtils.hasText(assessmentReason)) {
      throw new ClaimBadRequestException(ASSESSMENT_REASON_MUST_BE_PROVIDED_ERROR);
    }
  }

  /**
   * Validates that the provided user ID meets all requirements.
   *
   * <p>This method performs the following validation checks:
   *
   * <ul>
   *   <li>Ensures the user ID is not null
   *   <li>Ensures the user ID is not blank (empty or whitespace-only)
   *   <li>Ensures the user ID is a valid UUID format
   * </ul>
   *
   * @param userId the user ID to validate
   * @throws AssessmentInvalidUserException if any validation check fails
   */
  public void validateUserId(String userId) {
    validateUserIdIsNotNullOrBlank(userId);
    if (!isValidUuid(userId)) {
      throw new AssessmentInvalidUserException(
          AssessmentInvalidUserException.ErrorMessage.INVALID_UUID_FORMAT.getMessage(userId));
    }
  }

  private static void validateUserIdIsNotNullOrBlank(String userId) {
    if (!StringUtils.hasText(userId)) {
      throw new AssessmentInvalidUserException(
          AssessmentInvalidUserException.ErrorMessage.NULL_OR_BLANK.getMessage());
    }
  }

  /**
   * Checks whether the provided string is a valid UUID format.
   *
   * @param uuid the string to validate as a UUID
   * @return true if the string is a valid UUID, false otherwise
   */
  private boolean isValidUuid(String uuid) {
    try {
      UUID.fromString(uuid);
      return true;
    } catch (IllegalArgumentException | NullPointerException e) {
      return false;
    }
  }

  public void ensureAssessmentTypeIsNotVoid(AssessmentType assessmentType) {
    if (assessmentType == AssessmentType.VOID) {
      throw new ClaimBadRequestException(
          CLAIM_PATCH_VOID_INVALID_OPERATION.formatted("assessment"));
    }
  }

  public Claim getValidClaimOrThrow(UUID claimId) {
    Claim claim =
        claimRepository
            .findById(claimId)
            .orElseThrow(
                () ->
                    new ClaimNotFoundException(
                        String.format(NO_CLAIM_FOUND_WITH_ID_ERROR, claimId)));

    if (claim.getStatus() == ClaimStatus.VOID) {
      throw new ClaimBadRequestException(
          String.format(CLAIM_IS_ALREADY_VOID_STATUS_ERROR, claimId));
    }

    ensureClaimIsValid(claim);

    return claim;
  }

  public void ensureClaimIsValid(Claim claim) {
    if (claim.getStatus() != ClaimStatus.VALID) {
      throw new ClaimBadRequestException(
          String.format(CLAIM_WITH_ID_DOES_NOT_HAVE_VALID_STATUS_ERROR, claim.getId()));
    }
  }

  public ClaimSummaryFee getClaimSummaryFeeByClaimIdOrThrow(UUID claimId) {
    return
        claimSummaryFeeRepository
            .findByClaimId(claimId)
            .orElseThrow(
                () ->
                    new ClaimSummaryFeeNotFoundException(
                        String.format(NO_SUMMARY_FEE_FOR_CLAIM_ID_ERROR, claimId)));
  }

  public ClaimSummaryFee getClaimSummaryFeeByIdOrThrow(UUID claimSummaryFeeId) {
    if (!claimSummaryFeeRepository.existsById(claimSummaryFeeId)) {
      throw new ClaimSummaryFeeNotFoundException(
          String.format(NO_CLAIM_SUMMARY_FEE_FOUND_WITH_ID_ERROR, claimSummaryFeeId));
    }

    return claimSummaryFeeRepository.getReferenceById(claimSummaryFeeId);
  }
}
