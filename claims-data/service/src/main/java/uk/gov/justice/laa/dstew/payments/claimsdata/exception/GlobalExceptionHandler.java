package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** The global exception handler for all exceptions. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  /**
   * The handler for ClaimNotFoundException.
   *
   * @param exception the exception
   * @return the response status with error message
   */
  @ExceptionHandler(ClaimNotFoundException.class)
  public ResponseEntity<String> handleClaimNotFound(ClaimNotFoundException exception) {
    return ResponseEntity.status(NOT_FOUND).body(exception.getMessage());
  }

  /**
   * Handles validation-related exceptions by returning a HTTP 400 Bad Request status with the
   * corresponding error message from the exception.
   *
   * @param ex the BulkSubmissionValidationException encountered during validation
   * @return a ResponseEntity containing the HTTP Bad Request status and the exception message
   */
  @ExceptionHandler(BulkSubmissionValidationException.class)
  public ResponseEntity<String> handleValidationException(BulkSubmissionValidationException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }

  /**
   * Handles validation-related exceptions by returning a HTTP 400 Bad Request status with the
   * corresponding error message from the exception.
   *
   * @param ex the BulkSubmissionInvalidFileException encountered during validation
   * @return a ResponseEntity containing the HTTP unsupported media type status and the exception
   *     message
   */
  @ExceptionHandler(BulkSubmissionInvalidFileException.class)
  public ResponseEntity<String> handleUnsupportedMediaTypeValidationException(
      BulkSubmissionInvalidFileException ex) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ex.getMessage());
  }

  /**
   * The handler for SubmissionNotFoundException.
   *
   * @param exception the exception
   * @return the response status with error message
   */
  @ExceptionHandler(SubmissionNotFoundException.class)
  public ResponseEntity<String> handleSubmissionNotFound(SubmissionNotFoundException exception) {
    return ResponseEntity.status(NOT_FOUND).body(exception.getMessage());
  }

  /**
   * The handler for SubmissionBadRequestException.
   *
   * @param ex the SubmissionBadRequestException encountered during submission processing endpoints
   * @return a ResponseEntity containing the HTTP Bad Request status and the exception message.
   */
  @ExceptionHandler(SubmissionBadRequestException.class)
  public ResponseEntity<String> handleSubmissionBadRequestException(
      SubmissionBadRequestException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }

  /**
   * The handler for BulkSubmissionQueuePublishException.
   *
   * @param exception the exception
   * @return the response status with error message
   */
  @ExceptionHandler(BulkSubmissionQueuePublishException.class)
  public ResponseEntity<String> handleBulkSubmissionQueuePublishException(
      BulkSubmissionQueuePublishException exception) {
    return ResponseEntity.internalServerError().body(exception.getMessage());
  }

  /**
   * The handler for Exception.
   *
   * @param exception the exception
   * @return the response status with error message
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleGenericException(Exception exception) {
    String logMessage = "An unexpected application error has occurred.";
    log.error(logMessage, exception);
    return ResponseEntity.internalServerError().body(logMessage);
  }
}
