package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import uk.gov.laa.springboot.exception.ApplicationException;

/**
 * The exception is thrown when there's been a bad request while processing one of the claims
 * endpoint (e.g. some arguments are missing or not well formatted).
 *
 * <p>By extending {@link uk.gov.laa.springboot.exception.ApplicationException} the framework will
 * respond with a {@link org.springframework.http.HttpStatus#BAD_REQUEST 400 Bad Request} status
 * whenever this exception is thrown. This clearly communicates to clients that their request was
 * malformed.
 */
public class ClaimBadRequestException extends ApplicationException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public ClaimBadRequestException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
