package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uk.gov.laa.springboot.export.ExportValidationException;

/**
 * Global exception handler for the Claims Data service using RFC 9457 Problem Details.
 *
 * <p>This class extends {@link ResponseEntityExceptionHandler} to leverage Spring's native RFC 9457
 * Problem Details support. All exceptions are converted to {@link ProblemDetail} responses,
 * providing a standardised error format with fields such as {@code type}, {@code title}, {@code
 * status}, {@code detail}, and {@code instance}.
 *
 * <p>For backward compatibility with downstream services, the response structure includes:
 *
 * <ul>
 *   <li>{@code status} - HTTP status code
 *   <li>{@code title} - Short description of the error type
 *   <li>{@code detail} - Detailed error message (previously the exception message)
 *   <li>{@code type} - URI reference identifying the error type
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class DataClaimsExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String ERROR_TYPE_BASE_URI =
      "https://claimsdata.payments.laa.justice.gov.uk/errors/";

  /**
   * Handle {@link ClaimsDataException} instances and convert them to RFC 9457 Problem Details.
   *
   * <p>This is the primary exception handler for the service's custom exceptions.
   *
   * @param ex the claims data exception
   * @return a response containing a {@link ProblemDetail} with the appropriate status code
   */
  @ExceptionHandler(ClaimsDataException.class)
  public ResponseEntity<ProblemDetail> handleClaimsDataException(ClaimsDataException ex) {
    HttpStatus status = HttpStatus.resolve(ex.getHttpStatus().value());
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    return buildProblemDetailResponse(status, ex.getMessage(), ex.getClass());
  }

  /**
   * Handle any uncaught exceptions that are not instances of {@code ClaimsDataException}.
   *
   * <p>This method serves as a last-resort handler to capture and log any unexpected exceptions and
   * respond with a standardised RFC 9457 Problem Detail containing a generic 500 Internal Server
   * Error.
   *
   * @param exception the uncaught exception
   * @return a response containing a {@link ProblemDetail} with a 500 status code
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGenericException(Exception exception) {
    String logMessage = "An unexpected application error has occurred.";
    log.error(logMessage, exception);

    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, logMessage);
    problemDetail.setTitle(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
    problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "internal-server-error"));

    // Add backward compatibility property for downstream services
    problemDetail.setProperty("message", logMessage);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
  }

  /**
   * Build a standardised RFC 9457 Problem Detail response.
   *
   * @param status the HTTP status
   * @param message the error message
   * @param exceptionClass the exception class for type URI generation
   * @return a response containing a {@link ProblemDetail}
   */
  private ResponseEntity<ProblemDetail> buildProblemDetailResponse(
      HttpStatus status, String message, Class<?> exceptionClass) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, message);
    problemDetail.setTitle(status.getReasonPhrase());
    problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + toKebabCase(exceptionClass)));

    // Add backward compatibility property for downstream services
    problemDetail.setProperty("message", message);

    log.warn("Exception occurred: {}", message);
    return ResponseEntity.status(status).body(problemDetail);
  }

  /**
   * Convert a class name to kebab-case for use in error type URIs.
   *
   * @param exceptionClass the exception class
   * @return the kebab-case representation of the class name
   */
  private String toKebabCase(Class<?> exceptionClass) {
    String simpleName = exceptionClass.getSimpleName();
    return simpleName
        .replaceAll("Exception$", "")
        .replaceAll("([a-z])([A-Z])", "$1-$2")
        .toLowerCase();
  }

  @ExceptionHandler(ExportValidationException.class)
  public ResponseEntity<String> handleExportValidationException(ExportValidationException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }
}
