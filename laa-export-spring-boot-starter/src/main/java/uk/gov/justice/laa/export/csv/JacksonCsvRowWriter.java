package uk.gov.justice.laa.export.csv;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import java.beans.Introspector;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import uk.gov.justice.laa.export.model.ExportColumn;

/**
 * CSV writer backed by Jackson CsvMapper.
 */
public final class JacksonCsvRowWriter implements CsvRowWriter {
  private final CsvMapper mapper;

  public JacksonCsvRowWriter(CsvMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public <T> void writeAll(
      OutputStream out,
      Class<T> rowType,
      List<ExportColumn> columns,
      Consumer<Consumer<T>> rowProducer) {
    try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
      List<ExportColumn> resolvedColumns = resolveColumns(rowType, columns);
      if (resolvedColumns.isEmpty()) {
        var schema = mapper.schemaFor(rowType).withHeader();
        var seq = mapper.writer(schema).writeValues(writer);

        rowProducer.accept(
            row -> {
              try {
                seq.write(row);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

        seq.flush();
        writer.flush();
        return;
      }

      List<ColumnAccessor> accessors = buildAccessors(rowType, resolvedColumns);
      writeHeader(writer, accessors);
      rowProducer.accept(
          row -> {
            try {
              writeRow(writer, accessors, row);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      writer.flush();
    } catch (Exception e) {
      throw new RuntimeException("Failed to stream CSV", e);
    }
  }

  private <T> List<ColumnAccessor> buildAccessors(
      Class<T> rowType, List<ExportColumn> columns) throws Exception {
    Map<String, Method> getters = new HashMap<>();
    for (var descriptor : Introspector.getBeanInfo(rowType).getPropertyDescriptors()) {
      if (descriptor.getReadMethod() != null) {
        getters.put(descriptor.getName(), descriptor.getReadMethod());
      }
    }
    return columns.stream()
        .map(
            column -> {
              Method getter = getters.get(column.getKey());
              if (getter == null) {
                throw new IllegalArgumentException(
                    "No getter for column key: " + column.getKey());
              }
              String header = column.getHeader() == null ? column.getKey() : column.getHeader();
              return new ColumnAccessor(column.getKey(), header, column.getFormat(), getter);
            })
        .toList();
  }

  private <T> List<ExportColumn> resolveColumns(Class<T> rowType, List<ExportColumn> columns) {
    List<String> fieldOrder = new java.util.ArrayList<>();
    for (Field field : rowType.getDeclaredFields()) {
      fieldOrder.add(field.getName());
    }
    if (fieldOrder.isEmpty()) {
      return columns == null ? List.of() : columns;
    }

    if (columns == null || columns.isEmpty()) {
      return fieldOrder.stream()
          .map(key -> new ExportColumn(key, key, null))
          .toList();
    }

    Map<String, ExportColumn> overrides = new HashMap<>();
    for (ExportColumn column : columns) {
      overrides.put(column.getKey(), column);
    }

    if (overrides.size() < fieldOrder.size()) {
      return fieldOrder.stream()
          .map(
              key -> {
                ExportColumn override = overrides.get(key);
                return override == null ? new ExportColumn(key, key, null) : override;
              })
          .toList();
    }

    return columns;
  }

  private void writeHeader(Writer writer, List<ColumnAccessor> accessors) throws Exception {
    StringBuilder line = new StringBuilder();
    for (int i = 0; i < accessors.size(); i++) {
      if (i > 0) {
        line.append(',');
      }
      line.append(escape(accessors.get(i).header));
    }
    writer.write(line.toString());
    writer.write("\n");
  }

  private <T> void writeRow(Writer writer, List<ColumnAccessor> accessors, T row)
      throws Exception {
    StringBuilder line = new StringBuilder();
    for (int i = 0; i < accessors.size(); i++) {
      if (i > 0) {
        line.append(',');
      }
      ColumnAccessor accessor = accessors.get(i);
      Object value = accessor.getter.invoke(row);
      line.append(escape(formatValue(value, accessor.format)));
    }
    writer.write(line.toString());
    writer.write("\n");
  }

  private String formatValue(Object value, String format) {
    if (value == null) {
      return "";
    }
    if (format != null && !format.isBlank() && value instanceof TemporalAccessor temporal) {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
      if (value instanceof Instant instant) {
        return formatter.withZone(ZoneOffset.UTC).format(instant);
      }
      if (value instanceof OffsetDateTime offsetDateTime) {
        return formatter.format(offsetDateTime);
      }
      return formatter.format(temporal);
    }
    return String.valueOf(value);
  }

  private String escape(String value) {
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

  private static final class ColumnAccessor {
    private final String key;
    private final String header;
    private final String format;
    private final Method getter;

    private ColumnAccessor(String key, String header, String format, Method getter) {
      this.key = key;
      this.header = header;
      this.format = format;
      this.getter = getter;
    }
  }
}
