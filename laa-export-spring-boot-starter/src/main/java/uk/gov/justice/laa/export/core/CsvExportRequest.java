package uk.gov.justice.laa.export.core;

import java.util.Map;

/**
 * Represents a request to export CSV data from a specified source with given filters.
 */
public class CsvExportRequest {

  private final String sourceName;
  private final Map<String, Object> filters;

  public CsvExportRequest(
      String sourceName,
      Map<String, Object> filters
  ) {
    this.sourceName = sourceName;
    this.filters = filters;
  }

  public String getSourceName() {
    return sourceName;
  }

  public Map<String, Object> getFilters() {
    return filters;
  }
}