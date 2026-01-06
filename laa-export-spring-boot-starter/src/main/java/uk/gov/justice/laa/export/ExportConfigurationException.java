package uk.gov.justice.laa.export;

/**
 * Thrown when export configuration is invalid.
 */
public class ExportConfigurationException extends RuntimeException {
  public ExportConfigurationException(String message) {
    super(message);
  }

  public ExportConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
