package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.Optional;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentEligibilityError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Amendment eligibility gate for {@code claim.status}.
 *
 * <p>Only claims whose status is {@link ClaimStatus#VALID} may proceed to later amendment
 * validation. A voided claim is rejected with {@link
 * ClaimAmendmentErrorCode#INVALID_VOIDED_CLAIM_NOT_AMENDABLE}; any other non-{@code VALID} status
 * is rejected with {@link ClaimAmendmentErrorCode#INVALID_CLAIM_STATE_NOT_AMENDABLE}, carrying the
 * current status.
 *
 * <p>This gate is a pure, in-memory check: it has no repositories or clients, performs no
 * persistence and makes no external (PDA or FSP) calls. The parent flow runs it after claim
 * retrieval and before any PDA/FSP call, short-circuiting the amendment when a rejection is
 * returned - so no external call, claim update or amendment record is produced for an ineligible
 * claim.
 */
@Component
public class ClaimAmendmentEligibilityValidator {

  static final String VOIDED_CLAIM_MESSAGE = "A voided claim cannot be amended.";
  static final String CLAIM_STATE_NOT_AMENDABLE_MESSAGE =
      "Claim status %s is not amendable; only claims with status %s can be amended.";

  /**
   * Checks whether a claim with the given status is eligible for amendment.
   *
   * @param status the current stored claim status
   * @return {@link Optional#empty()} when the claim is eligible (status {@code VALID}); otherwise a
   *     populated {@link ClaimAmendmentEligibilityError} describing the rejection
   */
  public Optional<ClaimAmendmentEligibilityError> checkEligibility(ClaimStatus status) {
    if (status == ClaimStatus.VALID) {
      return Optional.empty();
    }

    if (status == ClaimStatus.VOID) {
      return Optional.of(
          new ClaimAmendmentEligibilityError(
              ClaimAmendmentErrorCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE,
              status,
              VOIDED_CLAIM_MESSAGE));
    }

    return Optional.of(
        new ClaimAmendmentEligibilityError(
            ClaimAmendmentErrorCode.INVALID_CLAIM_STATE_NOT_AMENDABLE,
            status,
            CLAIM_STATE_NOT_AMENDABLE_MESSAGE.formatted(status, ClaimStatus.VALID)));
  }
}
