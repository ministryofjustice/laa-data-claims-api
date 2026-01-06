package uk.gov.justice.laa.export;

/**
 * Thrown when an export key is not registered.
 */
public class ExportDefinitionNotFoundException extends RuntimeException {
  public ExportDefinitionNotFoundException(String message) {
    super(message);
  }
}
