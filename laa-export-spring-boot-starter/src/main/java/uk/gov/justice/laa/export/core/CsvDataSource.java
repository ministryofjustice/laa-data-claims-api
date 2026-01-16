package uk.gov.justice.laa.export.core;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;

public interface CsvDataSource {

  /**
   * Logical name of this source (e.g. "claims-table")
   */
  String getName();

  /**
   * Stream CSV output to the provided writer.
   */
  void writeCsv(
      CsvExportRequest request,
      Writer writer
  ) throws CsvException, IOException, SQLException;
}