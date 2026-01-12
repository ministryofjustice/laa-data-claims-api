package uk.gov.justice.laa.export.datasource.postgres;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders named-parameter SQL into a PostgreSQL COPY-ready statement.
 */
public final class PostgresSqlRenderer {

  private static final Pattern PARAM_PATTERN =
      Pattern.compile("(?<!:):([A-Za-z_][A-Za-z0-9_]*)");

  private PostgresSqlRenderer() {}

  /**
   * Renders named parameters into SQL literals for COPY execution.
   */
  public static String render(String sql, Map<String, Object> params) {
    if (sql == null || sql.isBlank()) {
      return sql;
    }
    Map<String, Object> safeParams = params == null ? Map.of() : params;
    Matcher matcher = PARAM_PATTERN.matcher(sql);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String name = matcher.group(1);
      Object value = safeParams.get(name);
      String literal = formatValue(value);
      matcher.appendReplacement(sb, Matcher.quoteReplacement(literal));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private static String formatValue(Object value) {
    if (value == null) {
      return "NULL";
    }
    if (value instanceof Number) {
      return value.toString();
    }
    if (value instanceof Boolean bool) {
      return bool ? "TRUE" : "FALSE";
    }
    if (value instanceof LocalDate date) {
      return quote(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }
    if (value instanceof Instant instant) {
      return quote(DateTimeFormatter.ISO_INSTANT.format(instant));
    }
    if (value instanceof OffsetDateTime offsetDateTime) {
      return quote(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(offsetDateTime));
    }
    if (value instanceof UUID) {
      return quote(value.toString());
    }
    if (value instanceof Enum<?> enumValue) {
      return quote(enumValue.name());
    }
    if (value instanceof Collection<?> collection) {
      if (collection.isEmpty()) {
        return "NULL";
      }
      StringBuilder joined = new StringBuilder();
      boolean first = true;
      for (Object item : collection) {
        if (!first) {
          joined.append(", ");
        }
        joined.append(formatValue(item));
        first = false;
      }
      return joined.toString();
    }
    return quote(value.toString());
  }

  private static String quote(String value) {
    String escaped = value == null ? "" : value.replace("'", "''");
    return "'" + escaped + "'";
  }
}
