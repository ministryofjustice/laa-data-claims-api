package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * The exception thrown when a submission cannot be located.
 *
 * <p>Extending {@link ClaimsDataException} associates this exception with a {@link
 * org.springframework.http.HttpStatus#NOT_FOUND 404 Not Found} status code.
 */
public class SubmissionNotFoundException extends ClaimsDataException {
  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the error message
   */
  public SubmissionNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
