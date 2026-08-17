package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a live submission already exists for the same office account number, area of law and
 * submission period (only one live submission is permitted per combination).
 *
 * <p>By extending {@link ClaimsDataException} the framework responds with a {@link
 * org.springframework.http.HttpStatus#CONFLICT 409 Conflict}. This is the user-facing result of the
 * application-level pre-check in {@code SubmissionService.createSubmission}; the authoritative,
 * race-safe enforcement is the database partial unique index {@code
 * uq_submission_live_office_aol_period}, whose violation is mapped to the same 409 by {@link
 * DataClaimsExceptionHandler} as a backstop.
 */
public class DuplicateSubmissionException extends ClaimsDataException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public DuplicateSubmissionException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
