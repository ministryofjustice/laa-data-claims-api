package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * The exception thrown when a live submission already exists for the same office account number,
 * area of law and submission period.
 *
 * <p>Extending {@link ClaimsDataException} associates this exception with a {@link
 * org.springframework.http.HttpStatus#CONFLICT 409 Conflict} status code, surfacing the database
 * unique-constraint violation to clients as a conflict rather than a generic server error.
 */
public class DuplicateSubmissionException extends ClaimsDataException {

  /**
   * Construct a new exception with the specified detail message and underlying cause.
   *
   * @param message the error message
   * @param cause the underlying data integrity violation
   */
  public DuplicateSubmissionException(String message, Throwable cause) {
    super(message, HttpStatus.CONFLICT, cause);
  }
}
