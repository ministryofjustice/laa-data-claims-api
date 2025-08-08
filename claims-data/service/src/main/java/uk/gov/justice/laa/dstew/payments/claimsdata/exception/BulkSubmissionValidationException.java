package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

/** Exception for issues when the uploaded file is the wrong file type. */
public class BulkSubmissionValidationException extends RuntimeException {

  public BulkSubmissionValidationException(String message) {
    super(message);
  }
}
