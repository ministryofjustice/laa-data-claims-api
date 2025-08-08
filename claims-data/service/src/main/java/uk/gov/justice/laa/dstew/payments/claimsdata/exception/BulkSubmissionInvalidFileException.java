package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

/** Exception for issues when the uploaded file is the wrong file type. */
public class BulkSubmissionInvalidFileException extends RuntimeException {

  public BulkSubmissionInvalidFileException(String message) {
    super(message);
  }
}
