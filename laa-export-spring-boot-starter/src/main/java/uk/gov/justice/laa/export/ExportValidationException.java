package uk.gov.justice.laa.export;

/**
 * Thrown when an export request fails validation.
 */
public class ExportValidationException extends RuntimeException {
  public ExportValidationException(String message) {
    super(message);
  }
}
