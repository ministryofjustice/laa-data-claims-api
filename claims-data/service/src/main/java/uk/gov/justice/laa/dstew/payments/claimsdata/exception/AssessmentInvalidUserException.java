package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import uk.gov.laa.springboot.exception.ApplicationException;

/**
 * Exception thrown when an {@link uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment}
 * cannot be created or updated due to an invalid userid passed.
 *
 * <p>This exception results in an HTTP 400 (Bad request) response when propagated through the API
 * layer.
 *
 * <p>Typical usage:
 *
 * <pre>
 *     throw new AssessmentInvalidUserException(ErrorMessage.NULL_OR_BLANK.getMessage());
 * </pre>
 *
 * @see ApplicationException
 */
public class AssessmentInvalidUserException extends ApplicationException {
  /** Enumeration of error messages for invalid user ID scenarios. */
  public enum ErrorMessage {
    /** User ID is null or blank (empty or whitespace-only). */
    NULL_OR_BLANK("User ID cannot be null or blank"),
    /** User ID is not in a valid UUID format. */
    INVALID_UUID_FORMAT("User ID must be a valid UUID: %s");

    private final String message;

    ErrorMessage(String message) {
      this.message = message;
    }

    /**
     * Get the error message.
     *
     * @return the error message
     */
    public String getMessage() {
      return message;
    }

    /**
     * Get the formatted error message with arguments.
     *
     * @param args the format arguments (if any)
     * @return the formatted error message
     */
    public String getMessage(Object... args) {
      if (args.length == 0) {
        return message;
      }
      return String.format(message, args);
    }
  }

  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the error message
   */
  public AssessmentInvalidUserException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
