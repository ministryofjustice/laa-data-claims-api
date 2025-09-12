package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

/** Exception for issues when attempting to publish a submission validation event. */
public class SubmissionValidationQueuePublishException extends RuntimeException {
  public SubmissionValidationQueuePublishException(String message) {
    super(message);
  }

  public SubmissionValidationQueuePublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
