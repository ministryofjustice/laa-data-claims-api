package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import uk.gov.laa.springboot.exception.ApplicationException;

/**
 * Thrown when a claim amendment is not found for a given ID.
 *
 * <p>By extending {@link uk.gov.laa.springboot.exception.ApplicationException} the framework will
 * respond with a {@link org.springframework.http.HttpStatus#NOT_FOUND 404 Not Found} status
 * whenever this exception is thrown. This clearly communicates to clients that the resource was not
 * found.
 */
public class ClaimAmendmentNotFoundException extends ApplicationException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public ClaimAmendmentNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
