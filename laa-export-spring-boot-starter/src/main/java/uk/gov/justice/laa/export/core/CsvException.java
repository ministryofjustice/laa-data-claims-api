package uk.gov.justice.laa.export.core;

public class CsvException extends RuntimeException {

  public CsvException(String message) {
    super(message);
  }

  public CsvException(String message, Throwable cause) {
    super(message, cause);
  }
}