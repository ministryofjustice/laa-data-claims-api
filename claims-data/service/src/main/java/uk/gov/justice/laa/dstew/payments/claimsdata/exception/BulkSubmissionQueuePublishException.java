package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

/** Exception for issues when attempting to read and map a bulk submission file. */
public class BulkSubmissionQueuePublishException extends RuntimeException {
  public BulkSubmissionQueuePublishException(String message) {
    super(message);
  }

  public BulkSubmissionQueuePublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
