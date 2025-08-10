package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The global exception handler for all exceptions.
 */
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
   * The handler for SubmissionNotFoundException.
   *
   * @param exception the exception
   * @return the response status with error message
   */
  @ExceptionHandler(SubmissionNotFoundException.class)
  public ResponseEntity<String> handleSubmissionNotFound(
      SubmissionNotFoundException exception) {
    return ResponseEntity.status(NOT_FOUND).body(exception.getMessage());
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
