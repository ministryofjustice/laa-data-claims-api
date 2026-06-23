package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

/** Stable error codes produced by claim amendment validation. */
public enum ClaimAmendmentErrorCode {

  /** The claim is voided and therefore cannot be amended. */
  INVALID_VOIDED_CLAIM_NOT_AMENDABLE,

  /**
   * The claim is in a non-amendable state - any {@code claim.status} other than {@code VALID} that
   * is not voided.
   */
  INVALID_CLAIM_STATE_NOT_AMENDABLE
}
