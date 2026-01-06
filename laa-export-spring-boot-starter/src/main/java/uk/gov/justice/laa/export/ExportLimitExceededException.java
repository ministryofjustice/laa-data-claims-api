package uk.gov.justice.laa.export;

/**
 * Thrown when an export exceeds the configured row limit.
 */
public class ExportLimitExceededException extends RuntimeException {
  public ExportLimitExceededException(String message) {
    super(message);
  }
}
