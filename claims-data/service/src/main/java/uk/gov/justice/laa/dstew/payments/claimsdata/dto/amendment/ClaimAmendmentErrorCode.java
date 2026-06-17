package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

/**
 * Stable error codes produced by claim amendment validation.
 *
 * <p>Each value is a contract identifier that the parent amendment flow maps onto the shared
 * structured validation/error response. This enum is the single home for amendment validation
 * codes; further codes (e.g. assessed-claim and version checks) are added by their owning tickets.
 */
public enum ClaimAmendmentErrorCode {

  /** The claim is voided and therefore cannot be amended. */
  INVALID_VOIDED_CLAIM_NOT_AMENDABLE,

  /**
   * The claim is in a non-amendable state - any {@code claim.status} other than {@code VALID} that
   * is not voided.
   */
  INVALID_CLAIM_STATE_NOT_AMENDABLE
}
