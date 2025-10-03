package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import uk.gov.laa.springboot.exception.ApplicationException;

/**
 * The exception is thrown when there's been a bad request while processing one of the submissions
 * endpoint (e.g. some arguments are missing or not well formatted).
 *
 * <p>This class extends {@link uk.gov.laa.springboot.exception.ApplicationException} so that a
 * {@link org.springframework.http.HttpStatus#BAD_REQUEST 400 Bad Request} response is returned to
 * clients when this exception is thrown.
 */
public class SubmissionBadRequestException extends ApplicationException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public SubmissionBadRequestException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
