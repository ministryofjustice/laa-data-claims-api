package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Amendment eligibility gate for {@code claim.status} (DSTEW-1764).
 *
 * <p>Only claims whose status is {@link ClaimStatus#VALID} may be amended. A voided claim is
 * rejected with {@link ClaimAmendmentErrorCode#INVALID_VOIDED_CLAIM_NOT_AMENDABLE}; any other
 * non-{@code VALID} status is rejected with {@link
 * ClaimAmendmentErrorCode#INVALID_CLAIM_STATE_NOT_AMENDABLE}. Both rejections are fatal, so no
 * later step runs and the amendment is not saved.
 */
@Component
public class ClaimStatusValidationStep implements ClaimAmendmentValidationStep {

  static final String VOIDED_CLAIM_MESSAGE = "A voided claim cannot be amended.";
  static final String CLAIM_STATE_NOT_AMENDABLE_MESSAGE =
      "Claim status %s is not amendable; only claims with status %s can be amended.";

  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    ClaimStatus status = state.getBeforeState().getStatus();

    if (status == ClaimStatus.VALID) {
      return List.of();
    }

    if (status == ClaimStatus.VOID) {
      return List.of(
          new ClaimAmendmentValidationError(
              ClaimAmendmentErrorCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE,
              VOIDED_CLAIM_MESSAGE,
              true));
    }

    return List.of(
        new ClaimAmendmentValidationError(
            ClaimAmendmentErrorCode.INVALID_CLAIM_STATE_NOT_AMENDABLE,
            CLAIM_STATE_NOT_AMENDABLE_MESSAGE.formatted(status, ClaimStatus.VALID),
            true));
  }
}
