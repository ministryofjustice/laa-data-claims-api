package uk.gov.justice.laa.export.model;

/**
 * CSV column metadata.
 */
public final class ExportColumn {
  private final String key;
  private final String header;
  private final String format;

  /**
   * Creates column metadata for CSV output.
   */
  public ExportColumn(String key, String header, String format) {
    this.key = key;
    this.header = header;
    this.format = format;
  }

  public String getKey() {
    return key;
  }

  public String getHeader() {
    return header;
  }

  public String getFormat() {
    return format;
  }
}
