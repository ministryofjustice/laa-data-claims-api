package uk.gov.justice.laa.export.core;

import java.io.Writer;
import java.util.List;

public class CsvExportService {

  private final List<CsvDataSource> dataSources;

  public CsvExportService(List<CsvDataSource> dataSources) {
    this.dataSources = dataSources;
  }

  public void export(
      CsvExportRequest request,
      Writer writer
  ) {
    CsvDataSource source = dataSources.stream()
        .filter(ds -> ds.getName().equals(request.getSourceName()))
        .findFirst()
        .orElseThrow(() ->
            new CsvException("Unknown CSV source: " + request.getSourceName())
        );

    try {
      source.writeCsv(request, writer);
    } catch (Exception e) {
      throw new CsvException("CSV export failed", e);
    }
  }
}