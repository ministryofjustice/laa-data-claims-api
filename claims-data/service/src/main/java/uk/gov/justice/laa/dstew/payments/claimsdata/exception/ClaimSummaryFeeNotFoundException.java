package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import uk.gov.laa.springboot.exception.ApplicationException;

/**
 * The exception thrown when a claim summary fee cannot be located.
 *
 * <p>Extending {@link uk.gov.laa.springboot.exception.ApplicationException} allows the framework to
 * interpret this exception as a 404 error by using the {@link
 * org.springframework.http.HttpStatus#NOT_FOUND} status.
 */
public class ClaimSummaryFeeNotFoundException extends ApplicationException {
  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the error message
   */
  public ClaimSummaryFeeNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
