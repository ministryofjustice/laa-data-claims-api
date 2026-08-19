package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * The exception thrown when attempting to create a submission whose identifier already exists.
 *
 * <p>Extending {@link ClaimsDataException} associates this exception with a {@link
 * org.springframework.http.HttpStatus#CONFLICT 409 Conflict} status code, so a duplicate create
 * request fails fast instead of silently overwriting the existing submission.
 */
public class SubmissionAlreadyExistsException extends ClaimsDataException {
  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the error message
   */
  public SubmissionAlreadyExistsException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
