package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

/**
 * The exception thrown when bulk submission not found.
 */
public class BulkSubmissionNotFoundException extends RuntimeException {
  /**
   * Constructor for BulkSubmissionNotFoundException.
   *
   * @param message the error message
   */
  public BulkSubmissionNotFoundException(String message) {
    super(message);
  }
}
