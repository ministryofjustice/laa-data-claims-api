package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a claim amendment is not found for a given ID.
 *
 * <p>By extending {@link ClaimsDataException} the framework will respond with a {@link
 * HttpStatus#NOT_FOUND 404 Not Found} status whenever this exception is thrown. This clearly
 * communicates to clients that the resource was not found.
 */
public class ClaimAmendmentNotFoundException extends ClaimsDataException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public ClaimAmendmentNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
