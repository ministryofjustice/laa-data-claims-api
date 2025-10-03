package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import uk.gov.laa.springboot.exception.ApplicationException;

/**
 * The exception thrown when a bulk submission cannot be located.
 *
 * <p>Extending {@link uk.gov.laa.springboot.exception.ApplicationException} allows a {@link
 * org.springframework.http.HttpStatus#NOT_FOUND} response to be produced automatically, which
 * results in a 404 status being sent back to clients.
 */
public class BulkSubmissionNotFoundException extends ApplicationException {
  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the error message
   */
  public BulkSubmissionNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
