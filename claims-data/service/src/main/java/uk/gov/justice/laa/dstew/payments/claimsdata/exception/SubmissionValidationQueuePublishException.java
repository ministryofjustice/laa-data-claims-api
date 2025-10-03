package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import uk.gov.laa.springboot.exception.ApplicationException;

/**
 * Exception for issues when attempting to publish a submission validation event.
 *
 * <p>This exception indicates an internal failure when publishing to a validation queue. It extends
 * {@link uk.gov.laa.springboot.exception.ApplicationException} and will automatically result in a
 * {@link org.springframework.http.HttpStatus#INTERNAL_SERVER_ERROR 500} response being returned to
 * the client.
 */
public class SubmissionValidationQueuePublishException extends ApplicationException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public SubmissionValidationQueuePublishException(String message) {
    super(message, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * Construct a new exception with the specified detail message and cause.
   *
   * <p>The {@code cause} will be attached to this exception using {@link
   * Throwable#initCause(Throwable)} so that debugging information is preserved.
   *
   * @param message the detail message
   * @param cause the cause of the exception
   */
  public SubmissionValidationQueuePublishException(String message, Throwable cause) {
    super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    initCause(cause);
  }
}
