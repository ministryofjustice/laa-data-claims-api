package uk.gov.justice.laa.export;

/**
 * Thrown when export access is denied.
 */
public class ExportAccessDeniedException extends RuntimeException {
  public ExportAccessDeniedException(String message) {
    super(message);
  }
}
