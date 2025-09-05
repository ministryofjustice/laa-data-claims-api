package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

/**
 * The exception is thrown when there's been a bad request while processing one of the submissions
 * endpoint (e.g. some arguments are missing or not well formatted).
 */
public class SubmissionBadRequestException extends RuntimeException {
  public SubmissionBadRequestException(String message) {
    super(message);
  }
}
