package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import uk.gov.laa.springboot.exception.ApplicationException;

/**
 * Thrown for illegal state or business rule violations in claim amendments.
 *
 * <p>By extending {@link uk.gov.laa.springboot.exception.ApplicationException} the framework will
 * respond with a {@link org.springframework.http.HttpStatus#CONFLICT 409 Conflict} status whenever
 * this exception is thrown. This clearly communicates to clients that the request could not be
 * completed due to a conflict with the current state of the resource.
 */
public class ClaimAmendmentStateException extends ApplicationException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public ClaimAmendmentStateException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
