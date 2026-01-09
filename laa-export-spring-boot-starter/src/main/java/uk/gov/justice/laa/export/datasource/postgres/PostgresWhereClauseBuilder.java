package uk.gov.justice.laa.export.datasource.postgres;

import java.util.Map;
import java.util.StringJoiner;

/**
 /**
 * Builds a safe SQL {@code WHERE} clause for use with PostgreSQL
 * {@code COPY (SELECT ... ) TO STDOUT} statements.
 *
 * <h2>Why this class exists</h2>
 * PostgreSQL's {@code COPY} command does <strong>not</strong> support bind
 * variables (prepared statement parameters) inside the {@code SELECT}
 * statement. This means that a traditional {@code PreparedStatement} with
 * {@code ?} placeholders cannot be used when exporting data via
 * {@code COPY ... TO STDOUT}.
 *
 * <p>As a result, any dynamic filtering must be rendered into the SQL text
 * itself.</p>
 *
 * <h2>Why this is still safe</h2>
 * Although this class produces SQL fragments as strings, it is designed to
 * <strong>prevent SQL injection</strong> by construction:
 *
 * <ul>
 *   <li>Only a predefined set of allowed filters may be used.</li>
 *   <li>Column names are taken exclusively from trusted configuration
 *       ({@link SqlFilterDefinition}) and never from user input.</li>
 *   <li>Values are strictly typed and validated before being rendered.</li>
 *   <li>All literal values are safely escaped according to PostgreSQL rules.</li>
 *   <li>No raw user input is ever concatenated directly into SQL.</li>
 * </ul>
 *
 * <h2>Intended usage</h2>
 * This builder should be used <em>only</em> for generating WHERE clauses for
 * {@code COPY} exports where prepared statements are not available. It should
 * not be used as a general-purpose SQL builder.
 *
 * <p>When prepared statements <em>are</em> supported, they should always be
 * preferred.</p>
 *
 * <h2>Design constraints</h2>
 * <ul>
 *   <li>PostgreSQL-specific</li>
 *   <li>Intentionally restrictive API</li>
 *   <li>Focused on safety over flexibility</li>
 * </ul>
 *
 * This class exists to balance PostgreSQL COPY performance with strong
 * security guarantees.
 */
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
