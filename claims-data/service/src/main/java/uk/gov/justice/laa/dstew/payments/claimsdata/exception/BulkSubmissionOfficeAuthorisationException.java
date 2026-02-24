package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * The exception thrown when a bulk submission office authorisation cannot be granted.
 *
 * <p>Extending {@link ClaimsDataException} allows a {@link
 * org.springframework.http.HttpStatus#FORBIDDEN} response to be produced automatically, which
 * results in a 403 status being sent back to clients.
 */
public class BulkSubmissionOfficeAuthorisationException extends ClaimsDataException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the error message
   */
  public BulkSubmissionOfficeAuthorisationException(final String message) {
    super(message, HttpStatus.FORBIDDEN);
  }
}
