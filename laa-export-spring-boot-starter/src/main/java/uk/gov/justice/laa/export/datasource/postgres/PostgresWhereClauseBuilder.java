package uk.gov.justice.laa.export.datasource.postgres;

import java.util.Map;
import java.util.StringJoiner;

public class PostgresWhereClauseBuilder {

  private final Map<String, SqlFilterDefinition> allowedFilters;

  public PostgresWhereClauseBuilder(
      Map<String, SqlFilterDefinition> allowedFilters
  ) {
    this.allowedFilters = allowedFilters;
  }

  public String buildWhereClause(Map<String, Object> filters) {

    if (filters == null || filters.isEmpty()) {
      return "";
    }

    StringJoiner joiner = new StringJoiner(" AND ", " WHERE ", "");

    for (var entry : filters.entrySet()) {

      SqlFilterDefinition def = allowedFilters.get(entry.getKey());
      if (def == null) {
        throw new IllegalArgumentException(
            "Filter not allowed: " + entry.getKey()
        );
      }

      Object value = entry.getValue();
      validateType(def, value);

      joiner.add(def.column() + " = " + formatValue(value));
    }

    return joiner.toString();
  }

  private void validateType(SqlFilterDefinition def, Object value) {
    if (!def.type().isInstance(value)) {
      throw new IllegalArgumentException(
          "Invalid type for filter " + def.column()
      );
    }
  }

  private String formatValue(Object value) {
    if (value instanceof Number) {
      return value.toString();
    }
    return "'" + value.toString().replace("'", "''") + "'";
  }
}
