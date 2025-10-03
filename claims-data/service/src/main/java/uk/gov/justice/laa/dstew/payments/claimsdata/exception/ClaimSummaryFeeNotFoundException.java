package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

/** The exception thrown when claim summary fee not found. */
public class ClaimSummaryFeeNotFoundException extends RuntimeException {
  /**
   * Constructor for ClaimSummaryFeeNotFoundException.
   *
   * @param message the error message
   */
  public ClaimSummaryFeeNotFoundException(String message) {
    super(message);
  }
}
