package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

/** Exception for issues when attempting to read and map a bulk submission file. */
public class BulkSubmissionFileReadException extends RuntimeException {
  public BulkSubmissionFileReadException(String message) {
    super(message);
  }

  public BulkSubmissionFileReadException(String message, Exception cause) {
    super(message, cause);
  }
}
