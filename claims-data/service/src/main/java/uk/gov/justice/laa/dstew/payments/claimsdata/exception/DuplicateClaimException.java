package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a claim would duplicate an existing {@code (submission_id, line_number)} within a
 * submission (a claim's line number must be unique within its submission).
 *
 * <p>By extending {@link ClaimsDataException} the framework responds with a {@link
 * org.springframework.http.HttpStatus#CONFLICT 409 Conflict}. This is the user-facing result of the
 * application-level pre-check in {@code ClaimService.createClaim}; the authoritative, race-safe
 * enforcement is the database partial unique index {@code uq_claim_submission_line_number}, whose
 * violation is mapped to the same 409 by {@link DataClaimsExceptionHandler} as a backstop.
 */
public class DuplicateClaimException extends ClaimsDataException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public DuplicateClaimException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
