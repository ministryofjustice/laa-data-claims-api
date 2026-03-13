package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Base exception class for all Claims Data service exceptions.
 *
 * <p>This class provides a standardised way to associate exceptions with HTTP status codes,
 * enabling automatic conversion to RFC 9457 Problem Details responses via {@link
 * DataClaimsExceptionHandler}.
 *
 * <p>All domain-specific exceptions should extend this class and specify an appropriate {@link
 * HttpStatus} to indicate the HTTP response code to return to clients.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public class ClaimNotFoundException extends ClaimsDataException {
 *   public ClaimNotFoundException(String message) {
 *     super(message, HttpStatus.NOT_FOUND);
 *   }
 * }
 * }</pre>
 */
@Getter
public class ClaimsDataException extends RuntimeException {
  private final HttpStatusCode httpStatus;

  /**
   * Construct a new exception with the specified message and HTTP status.
   *
   * @param message the error message
   * @param httpStatus the HTTP status code to return
   */
  public ClaimsDataException(String message, HttpStatusCode httpStatus) {
    super(message);
    this.httpStatus = httpStatus;
  }

  /**
   * Construct a new exception with the specified message, HTTP status, and cause.
   *
   * @param message the error message
   * @param httpStatus the HTTP status code to return
   * @param cause the underlying cause
   */
  public ClaimsDataException(String message, HttpStatusCode httpStatus, Throwable cause) {
    super(message, cause);
    this.httpStatus = httpStatus;
  }
}
