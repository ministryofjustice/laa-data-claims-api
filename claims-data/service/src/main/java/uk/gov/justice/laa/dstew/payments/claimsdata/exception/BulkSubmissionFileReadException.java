package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import uk.gov.laa.springboot.exception.ApplicationException;

/**
 * Exception for issues when attempting to read and map a bulk submission file.
 *
 * <p>This exception extends {@link uk.gov.laa.springboot.exception.ApplicationException} so that it
 * carries an associated {@link org.springframework.http.HttpStatus} for the framework to interpret
 * and produce the correct HTTP response. A {@code BAD_REQUEST} status is used to reflect that the
 * client supplied invalid input or the file could not be read.
 */
public class BulkSubmissionFileReadException extends ApplicationException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public BulkSubmissionFileReadException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }

  /**
   * Construct a new exception with the specified detail message and cause.
   *
   * <p>The {@link Throwable} cause will be attached to this exception using {@link
   * Throwable#initCause(Throwable)} so that full stack traces are preserved even though the base
   * {@link uk.gov.laa.springboot.exception.ApplicationException} class does not expose a
   * constructor taking a cause.
   *
   * @param message the detail message
   * @param cause the cause of the exception
   */
  public BulkSubmissionFileReadException(String message, Exception cause) {
    super(message, HttpStatus.BAD_REQUEST);
    initCause(cause);
  }
}
