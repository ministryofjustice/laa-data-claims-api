package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for issues when attempting to publish a bulk submission event.
 *
 * <p>This exception indicates an internal failure when publishing to an underlying queue. It
 * extends {@link ClaimsDataException} and will automatically result in a {@link
 * org.springframework.http.HttpStatus#INTERNAL_SERVER_ERROR 500} response being returned to the
 * client.
 */
public class BulkSubmissionQueuePublishException extends ClaimsDataException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public BulkSubmissionQueuePublishException(String message) {
    super(message, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * Construct a new exception with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause of the exception
   */
  public BulkSubmissionQueuePublishException(String message, Throwable cause) {
    super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
  }
}
