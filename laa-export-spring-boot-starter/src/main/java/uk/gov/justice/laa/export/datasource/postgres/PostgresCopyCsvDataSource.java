package uk.gov.justice.laa.export.datasource.postgres;

import java.io.IOException;
import java.sql.SQLException;
import uk.gov.justice.laa.export.core.CsvDataSource;
import uk.gov.justice.laa.export.core.CsvException;
import uk.gov.justice.laa.export.core.CsvExportRequest;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

import javax.sql.DataSource;
import java.io.Writer;
import java.sql.Connection;

public class PostgresCopyCsvDataSource implements CsvDataSource {

  private final String name;
  private final DataSource dataSource;
  private final String baseSelect;
  private final PostgresWhereClauseBuilder whereClauseBuilder;

  public PostgresCopyCsvDataSource(
      String name,
      DataSource dataSource,
      String baseSelect,
      PostgresWhereClauseBuilder whereClauseBuilder
  ) {
    this.name = name;
    this.dataSource = dataSource;
    this.baseSelect = baseSelect;
    this.whereClauseBuilder = whereClauseBuilder;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void writeCsv(
      CsvExportRequest request,
      Writer writer
  ) throws CsvException, IOException, SQLException {

    String whereClause =
        whereClauseBuilder.buildWhereClause(request.getFilters());

    String copySql = """
            COPY (
              %s
              %s
            ) TO STDOUT WITH CSV HEADER
            """.formatted(baseSelect, whereClause);

    try (Connection conn = dataSource.getConnection()) {

      conn.setAutoCommit(false);

      PGConnection pgConnection = conn.unwrap(PGConnection.class);
      CopyManager copyManager = pgConnection.getCopyAPI();

      copyManager.copyOut(copySql, writer);
    }
  }
}