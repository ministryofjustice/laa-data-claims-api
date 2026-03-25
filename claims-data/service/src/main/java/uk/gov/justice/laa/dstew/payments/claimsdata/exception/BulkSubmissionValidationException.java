package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for issues encountered during validation of a bulk submission.
 *
 * <p>Extending {@link ClaimsDataException} allows the framework to use the supplied {@link
 * org.springframework.http.HttpStatus#BAD_REQUEST} to construct a 400 response automatically.
 */
public class BulkSubmissionValidationException extends ClaimsDataException {
  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public BulkSubmissionValidationException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
