package uk.gov.justice.laa.export.csv;

import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;
import uk.gov.justice.laa.export.model.ExportColumn;

/**
 * Writes rows to CSV output.
 */
public interface CsvRowWriter {
  <T> void writeAll(
      OutputStream out,
      Class<T> rowType,
      List<ExportColumn> columns,
      Consumer<Consumer<T>> rowProducer);
}
