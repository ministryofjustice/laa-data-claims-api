package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import uk.gov.laa.springboot.exception.ApplicationException;

/**
 * Exception for issues when the uploaded file is the wrong file type.
 *
 * <p>This exception extends {@link uk.gov.laa.springboot.exception.ApplicationException} so that a
 * {@link org.springframework.http.HttpStatus#UNSUPPORTED_MEDIA_TYPE 415 Unsupported Media Type}
 * status code is returned to the client when this exception is thrown. This clearly communicates
 * that the content type supplied is not acceptable.
 */
public class BulkSubmissionInvalidFileException extends ApplicationException {

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public BulkSubmissionInvalidFileException(String message) {
    super(message, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }
}
