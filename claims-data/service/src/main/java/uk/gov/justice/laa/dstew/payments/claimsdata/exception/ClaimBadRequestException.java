package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

/**
 * The exception is thrown when there's been a bad request while processing one of the claims
 * endpoint (e.g. some arguments are missing or not well formatted).
 */
public class ClaimBadRequestException extends RuntimeException {
  public ClaimBadRequestException(String message) {
    super(message);
  }
}
