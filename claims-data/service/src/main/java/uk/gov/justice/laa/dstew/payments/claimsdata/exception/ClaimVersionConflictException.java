package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an early gate concurrency check detects that the client is attempting to update a
 * stale version of a Claim.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ClaimVersionConflictException extends RuntimeException {

  public ClaimVersionConflictException(String message) {
    super(message);
  }

  public ClaimVersionConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
