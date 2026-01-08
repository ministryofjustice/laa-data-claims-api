package uk.gov.justice.laa.export.csv;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uk.gov.justice.laa.export.model.ExportColumn;

/**
 * Writes CSV headers using configured column order and overrides.
 */
public final class CsvHeaderWriter {

  private CsvHeaderWriter() {}

  /**
   * Writes a CSV header line using configured columns and order.
   */
  public static void writeHeader(
      Writer writer,
      List<String> columnOrder,
      List<ExportColumn> columns) throws IOException {
    List<ExportColumn> resolved = resolveColumns(columnOrder, columns);
    if (resolved.isEmpty()) {
      return;
    }
    StringBuilder line = new StringBuilder();
    for (int i = 0; i < resolved.size(); i++) {
      if (i > 0) {
        line.append(',');
      }
      ExportColumn column = resolved.get(i);
      String header = column.getHeader() == null ? column.getKey() : column.getHeader();
      line.append(escape(header));
    }
    writer.write(line.toString());
    writer.write("\n");
  }

  private static List<ExportColumn> resolveColumns(
      List<String> columnOrder,
      List<ExportColumn> columns) {
    List<String> order = columnOrder == null ? List.of() : columnOrder;
    List<ExportColumn> overrides = columns == null ? List.of() : columns;

    if (order.isEmpty()) {
      return new ArrayList<>(overrides);
    }

    Map<String, ExportColumn> overridesByKey = new HashMap<>();
    for (ExportColumn column : overrides) {
      overridesByKey.put(column.getKey(), column);
    }

    List<ExportColumn> resolved = new ArrayList<>(order.size());
    Map<String, Boolean> orderedKeys = new HashMap<>();
    for (String key : order) {
      orderedKeys.put(key, Boolean.TRUE);
      ExportColumn override = overridesByKey.get(key);
      if (override == null) {
        resolved.add(new ExportColumn(key, key, null));
      } else {
        resolved.add(new ExportColumn(key, override.getHeader(), override.getFormat()));
      }
    }

    for (ExportColumn column : overrides) {
      if (!orderedKeys.containsKey(column.getKey())) {
        resolved.add(column);
      }
    }

    return resolved;
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    boolean needsQuotes =
        value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
    if (!needsQuotes) {
      return value;
    }
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
