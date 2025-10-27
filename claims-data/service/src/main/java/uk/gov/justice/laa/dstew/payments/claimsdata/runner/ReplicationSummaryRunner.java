package uk.gov.justice.laa.dstew.payments.claimsdata.runner;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The ReplicationSummaryRunner class is responsible for generating and updating a daily summary of
 * replication information for tables in a specified publication, excluding the replication summary
 * table itself. This component is conditionally enabled based on the "replication.summary.enabled"
 * property. We set this true in the cronjob's settings so that only this runner executes (in case
 * we add more runners in future)
 *
 * <p>1. Retrieves a list of tables within the "claims_reporting_service_pub" publication. 2.
 * Calculates the total record count and the count of records updated in the last day for each
 * table. 3. Upserts the summary information into the "replication_summary" table using the provided
 * database schema.
 *
 * <p>Dependencies: - JdbcTemplate: Used for executing SQL queries and updates. Simplifies this
 * non-business-logic code and keeps it all in one place (compared to the JPA entities/repositories
 * etc).
 *
 * <p>Annotations: - `@ConditionalOnProperty`: Enables this component only if the
 * "replication.summary.enabled" property is set to true.
 */
@Component
@ConditionalOnProperty(name = "replication.summary.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ReplicationSummaryRunner implements ApplicationRunner {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void run(ApplicationArguments args) {
    try {
      log.info("Starting replication summary update...");
      LocalDate summaryDate = LocalDate.now().minusDays(1); // yesterday
      Timestamp startOfDay = Timestamp.valueOf(summaryDate.atStartOfDay());
      Timestamp endOfDay = Timestamp.valueOf(summaryDate.plusDays(1).atStartOfDay());

      // Get all tables in the publication except the replication_summary table itself
      String tablesQuery =
          """
          SELECT t.schemaname, t.tablename
          FROM pg_publication_tables t
          WHERE t.pubname = 'claims_reporting_service_pub'
            AND t.tablename != 'replication_summary'
          """;

      List<Map<String, Object>> tables = jdbcTemplate.queryForList(tablesQuery);

      for (Map<String, Object> table : tables) {
        String schema = (String) table.get("schemaname");
        String tableName = (String) table.get("tablename");
        String fullTableName = schema + "." + tableName;

        log.info("Processing table: {}", fullTableName);

        // Build SQL dynamically
        String countSql =
            String.format("SELECT count(*) FROM %s WHERE created_on < ?", fullTableName);
        String updatedSql =
            String.format(
                "SELECT count(*) FROM %s WHERE updated_on BETWEEN ? AND ?", fullTableName);

        Long recordCount = jdbcTemplate.queryForObject(countSql, Long.class, endOfDay);
        Long updatedCount =
            jdbcTemplate.queryForObject(updatedSql, Long.class, startOfDay, endOfDay);

        // Upsert into replication_summary
        String upsertSql =
            """
            INSERT INTO claims.replication_summary (table_name, summary_date, record_count, updated_count, wal_lsn, created_on)
            VALUES (?, ?, ?, ?, pg_current_wal_lsn(), now())
            ON CONFLICT (table_name, summary_date)
            DO UPDATE SET
              record_count = EXCLUDED.record_count,
              updated_count = EXCLUDED.updated_count,
              wal_lsn = pg_current_wal_lsn(),
              created_on = now();
            """;

        jdbcTemplate.update(upsertSql, fullTableName, summaryDate, recordCount, updatedCount);
      }

      log.info("Replication summary update complete.");
    } catch (DataAccessException ex) {
      log.error("Database access error while updating replication summary", ex);
      throw ex;
    } catch (Exception ex) {
      log.error("Unexpected error while updating replication summary", ex);
      throw ex;
    }
  }
}
