package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.laa.springboot.exception.ApplicationException;
import uk.gov.laa.springboot.exception.GlobalExceptionHandler;

/**
 * Global exception handler for the Claims Data service.
 *
 * <p>This class extends the platform-provided {@link
 * uk.gov.laa.springboot.exception.GlobalExceptionHandler} so that exceptions deriving from {@link
 * uk.gov.laa.springboot.exception.ApplicationException} are handled automatically based on their
 * embedded {@link org.springframework.http.HttpStatus}. Only a catch-all handler for other {@link
 * Exception} types is retained here to ensure that unexpected errors are logged and an appropriate
 * internal server error response is returned.
 */
@RestControllerAdvice
@Slf4j
public class DataClaimsExceptionHandler extends GlobalExceptionHandler {

  /**
   * Handle any uncaught exceptions that are not instances of {@code ApplicationException}.
   *
   * <p>The underlying {@link uk.gov.laa.springboot.exception.GlobalExceptionHandler} will take care
   * of mapping {@link uk.gov.laa.springboot.exception.ApplicationException}s to their corresponding
   * HTTP status codes. This method serves as a last-resort handler to capture and log any
   * unexpected exceptions and respond with a generic 500 Internal Server Error.
   *
   * @param exception the uncaught exception
   * @return a response containing a generic error message and a 500 status code
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleGenericException(Exception exception) {
    String logMessage = "An unexpected application error has occurred.";
    log.error(logMessage, exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(logMessage);
  }

  @Override
  @ExceptionHandler(ApplicationException.class)
  public ResponseEntity<ApplicationException> handleApplicationException(ApplicationException ex) {
    return super.handleApplicationException(ex);
  }
}
