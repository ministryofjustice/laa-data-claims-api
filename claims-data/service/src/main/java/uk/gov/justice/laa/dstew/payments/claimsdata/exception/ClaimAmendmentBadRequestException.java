package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * The exception is thrown when there's been a bad request while processing one of the claim
 * amendments.
 *
 * <p>By extending {@link ClaimsDataException} the framework will respond with a {@link
 * HttpStatus#BAD_REQUEST 400 Bad Request} status whenever this exception is thrown. This clearly
 * communicates to clients that their request was malformed.
 */
public class ClaimAmendmentBadRequestException extends ClaimsDataException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public ClaimAmendmentBadRequestException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
