package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import jakarta.servlet.http.HttpServletRequest;
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
   * @param exception the claims data exception
   * @param request the HTTP request
   * @return a response containing a {@link ProblemDetail} with the appropriate status code
   */
  @ExceptionHandler(ClaimsDataException.class)
  public ResponseEntity<ProblemDetail> handleClaimsDataException(
      ClaimsDataException exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.resolve(exception.getHttpStatus().value());
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    log.warn("ClaimsDataException occurred: {}", exception.getMessage());
    return buildProblemDetailResponse(
        status, exception.getMessage(), exception.getClass(), request);
  }

  /**
   * Handle {@link ExportValidationException} instances and convert them to RFC 9457 Problem
   * Details.
   *
   * @param exception the export validation exception
   * @param request the HTTP request
   * @return a response containing a {@link ProblemDetail} with a 400 status code
   */
  @ExceptionHandler(ExportValidationException.class)
  public ResponseEntity<ProblemDetail> handleExportValidationException(
      ExportValidationException exception, HttpServletRequest request) {
    log.warn("ExportValidationException occurred: {}", exception.getMessage());
    return buildProblemDetailResponse(
        HttpStatus.BAD_REQUEST, exception.getMessage(), exception.getClass(), request);
  }

  /**
   * Handle any uncaught exceptions that are not instances of {@code ClaimsDataException}.
   *
   * <p>This method serves as a last-resort handler to capture and log any unexpected exceptions and
   * respond with a standardised RFC 9457 Problem Detail containing a generic 500 Internal Server
   * Error.
   *
   * @param exception the uncaught exception
   * @param request the HTTP request
   * @return a response containing a {@link ProblemDetail} with a 500 status code
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGenericException(
      Exception exception, HttpServletRequest request) {
    String errorMessage = "An unexpected application error has occurred.";
    log.error(errorMessage, exception);
    return buildProblemDetailResponse(
        HttpStatus.INTERNAL_SERVER_ERROR, errorMessage, exception.getClass(), request);
  }

  /**
   * Build a standardised RFC 9457 Problem Detail response.
   *
   * @param status the HTTP status
   * @param detail the error detail message
   * @param exceptionClass the exception class for type URI generation
   * @param request the HTTP request for populating the instance field
   * @return a response containing a {@link ProblemDetail}
   */
  private ResponseEntity<ProblemDetail> buildProblemDetailResponse(
      HttpStatus status, String detail, Class<?> exceptionClass, HttpServletRequest request) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(status.getReasonPhrase());
    problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + toKebabCase(exceptionClass)));
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    // Add backward compatibility property for downstream services
    problemDetail.setProperty("message", detail);

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
}
