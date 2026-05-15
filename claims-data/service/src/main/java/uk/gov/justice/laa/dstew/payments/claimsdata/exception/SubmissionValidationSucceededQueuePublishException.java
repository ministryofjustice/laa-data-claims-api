package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for issues when attempting to publish a submission validation succeeded event.
 *
 * <p>This exception indicates an internal failure when publishing the submission validation succeeded event to the SNS topic. It extends
 * {@link ClaimsDataException} and will automatically result in a {@link
 * org.springframework.http.HttpStatus#INTERNAL_SERVER_ERROR 500} response being returned to the
 * client.
 */
public class SubmissionValidationSucceededQueuePublishException extends ClaimsDataException {

    /**
     * Construct a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public SubmissionValidationSucceededQueuePublishException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Construct a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public SubmissionValidationSucceededQueuePublishException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
