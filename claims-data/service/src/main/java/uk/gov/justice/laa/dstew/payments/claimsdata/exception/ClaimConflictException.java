package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * The exception is thrown when there's a conflict while processing one of the claims endpoint (e.g.
 * a claim with the same line number already exists).
 *
 * <p>By extending {@link ClaimsDataException} the framework will respond with a {@link
 * HttpStatus#CONFLICT 409 Conflict} status whenever this exception is thrown. This clearly
 * communicates to clients that their request could not be completed due to a conflict with the
 * current state of the resource.
 */
public class ClaimConflictException extends ClaimsDataException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public ClaimConflictException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
