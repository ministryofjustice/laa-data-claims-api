package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import uk.gov.laa.springboot.exception.ApplicationException;

/**
 * Exception for issues encountered during validation of a bulk submission.
 *
 * <p>Extending {@link uk.gov.laa.springboot.exception.ApplicationException} allows the framework to
 * use the supplied {@link org.springframework.http.HttpStatus#BAD_REQUEST} to construct a 400
 * response automatically.
 */
public class BulkSubmissionValidationException extends ApplicationException {
  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public BulkSubmissionValidationException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
