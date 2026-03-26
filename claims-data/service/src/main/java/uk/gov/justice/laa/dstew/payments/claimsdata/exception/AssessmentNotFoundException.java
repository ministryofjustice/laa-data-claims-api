package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an {@link uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment}
 * cannot be found for the given identifier.
 *
 * <p>This exception results in an HTTP 404 (Not Found) response when propagated through the API
 * layer.
 *
 * <p>Typical usage:
 *
 * <pre>
 *     throw new AssessmentNotFoundException("Assessment not found for ID: " + assessmentId);
 * </pre>
 *
 * @see ClaimsDataException
 */
public class AssessmentNotFoundException extends ClaimsDataException {
  /**
   * Construct a new exception with the specified detail message.
   *
   * @param message the error message
   */
  public AssessmentNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
