package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import static uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode.INVALID_CLAIM_VERSION_CONFLICT;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
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
  private static final Pattern EXCEPTION_SUFFIX = Pattern.compile("Exception$");
  private static final Pattern CAMEL_CASE = Pattern.compile("([a-z])([A-Z])");

  /**
   * Handle {@link SubmissionValidationException} and include the list of validation issues as a
   * structured property inside the RFC 9457 Problem Detail body.
   *
   * @param exception the submission validation exception
   * @param request the HTTP request
   * @return a response containing the issues list
   */
  @ExceptionHandler(SubmissionValidationException.class)
  public ResponseEntity<ProblemDetail> handleSubmissionValidationException(
      SubmissionValidationException exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.resolve(exception.getHttpStatus().value());
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    ResponseEntity<ProblemDetail> response =
        buildProblemDetailResponse(status, exception.getMessage(), exception.getClass(), request);
    if (response.getBody() != null) {
      response.getBody().setProperty("issues", exception.getIssues());
    }
    return response;
  }

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
   * Handles validation failures originating from the claim amendment orchestrator.
   *
   * <p>Inspects the collection of validation errors returned by the orchestrator. If the error list
   * contains a fatal failure, the highest priority status code is surfaced. For standard errors, a
   * 400 Bad Request status code is returned. The collection of underlying validation errors is
   * attached as a structured property to the {@link ProblemDetail} payload.
   *
   * @param ex the exception containing the list of collected validation errors
   * @param request the HTTP request
   * @return a response containing a {@link ProblemDetail} detailing the amendment failures
   */
  @ExceptionHandler(ClaimAmendmentValidationException.class)
  public ResponseEntity<ProblemDetail> handleClaimAmendmentValidationException(
      ClaimAmendmentValidationException ex, HttpServletRequest request) {

    log.warn("ClaimAmendmentValidationException occurred with {} errors", ex.getErrors().size());
    ClaimAmendmentValidationError primaryError =
        sortValidationErrorsByFatalAndStatus(ex).getFirst();

    HttpStatus status = HttpStatus.BAD_REQUEST;
    if (primaryError != null && primaryError.isFatal()) {
      status = primaryError.getHttpStatus();
    }

    // A 204 outcome (e.g. a no-op amendment that changed nothing) is a success status and must not
    // carry a response body per RFC 9110; return an empty 204 without the ProblemDetail/errors.
    if (status == HttpStatus.NO_CONTENT) {
      return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    ResponseEntity<ProblemDetail> response =
        buildProblemDetailResponse(status, ex.getMessage(), ex.getClass(), request);

    if (response.getBody() != null) {
      response.getBody().setProperty("errors", ex.getErrors());
    }

    return response;
  }

  List<ClaimAmendmentValidationError> sortValidationErrorsByFatalAndStatus(
      ClaimAmendmentValidationException ex) {
    return ex.getErrors().stream()
        .sorted(
            Comparator.comparing(ClaimAmendmentValidationError::isFatal, Comparator.reverseOrder())
                .thenComparing(error -> error.getHttpStatus().value(), Comparator.reverseOrder()))
        .toList();
  }

  /**
   * Handles raw JPA-level optimistic locking failures during entity persistence.
   *
   * <p>This exception is typically thrown directly by the underlying ORM (e.g., Hibernate) when a
   * concurrent modification is detected, often during a manual flush operation before Spring's
   * transaction manager has the opportunity to translate the exception. This handler intercepts the
   * failure and returns an HTTP {@code 409 Conflict} status wrapped in an RFC 9457 Problem Detail.
   *
   * @param ex the raw JPA optimistic lock exception thrown by the persistence layer
   * @param request the HTTP request
   * @return a response containing a {@link ProblemDetail} with a 409 Conflict status code
   */
  @ExceptionHandler(OptimisticLockException.class)
  public ResponseEntity<ProblemDetail> handleDatabaseOptimisticLockException(
      OptimisticLockException ex, HttpServletRequest request) {

    log.warn("Database level optimistic locking failure occurred: {}", ex.getMessage());
    return buildProblemDetailResponse(
        HttpStatus.CONFLICT,
        INVALID_CLAIM_VERSION_CONFLICT.getMessageTemplate(),
        ex.getClass(),
        request);
  }

  /**
   * Handles Spring-translated optimistic locking failures during entity persistence.
   *
   * <p>This exception is thrown by Spring's data access layer when it intercepts and translates a
   * concurrent modification exception from the underlying persistence provider (e.g., typically at
   * the transaction commit boundary). This handler intercepts the failure and returns an HTTP
   * {@code 409 Conflict} status wrapped in an RFC 9457 Problem Detail.
   *
   * @param ex the Spring-translated optimistic locking exception
   * @param request the HTTP request
   * @return a response containing a {@link ProblemDetail} with a 409 Conflict status code
   */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> handleDatabaseOptimisticLockException(
      ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {

    log.warn("Database level object optimistic locking failure occurred: {}", ex.getMessage());
    return buildProblemDetailResponse(
        HttpStatus.CONFLICT,
        INVALID_CLAIM_VERSION_CONFLICT.getMessageTemplate(),
        ex.getClass(),
        request);
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
    // Remove 'Exception' suffix using precompiled pattern
    String noSuffix = EXCEPTION_SUFFIX.matcher(simpleName).replaceAll("");
    // Insert hyphens between camel case using precompiled pattern
    String kebab = CAMEL_CASE.matcher(noSuffix).replaceAll("$1-$2");
    return kebab.toLowerCase();
  }
}
