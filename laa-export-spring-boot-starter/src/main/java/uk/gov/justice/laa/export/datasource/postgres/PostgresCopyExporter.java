package uk.gov.justice.laa.export.datasource.postgres;

import java.io.Writer;
import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

/**
 * Exports SQL results via PostgreSQL COPY ... TO STDOUT.
 */
public final class PostgresCopyExporter {
  private final DataSource dataSource;

  public PostgresCopyExporter(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * Executes COPY TO STDOUT using rendered SQL and writes CSV to the writer.
   */
  public long copyCsv(
      String sql,
      Map<String, Object> params,
      Writer writer,
      boolean includeHeader) {
    String rendered = PostgresSqlRenderer.render(sql, params);
    String copySql = buildCopySql(rendered, includeHeader);
    try (Connection conn = dataSource.getConnection()) {
      conn.setReadOnly(true);
      conn.setAutoCommit(false);
      PGConnection pgConnection = conn.unwrap(PGConnection.class);
      CopyManager copyManager = pgConnection.getCopyAPI();
      long rows = copyManager.copyOut(copySql, writer);
      conn.commit();
      return rows;
    } catch (Exception e) {
      throw new RuntimeException("CSV export failed", e);
    }
  }

  private String buildCopySql(String renderedSql, boolean includeHeader) {
    String baseSql = renderedSql == null ? "" : renderedSql.trim();
    if (baseSql.endsWith(";")) {
      baseSql = baseSql.substring(0, baseSql.length() - 1);
    }
    String headerClause = includeHeader ? " HEADER" : "";
    return "COPY (" + baseSql + ") TO STDOUT WITH CSV" + headerClause;
  }
}
