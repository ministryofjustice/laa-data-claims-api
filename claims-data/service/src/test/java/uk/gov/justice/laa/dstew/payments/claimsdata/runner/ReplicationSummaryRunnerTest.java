package uk.gov.justice.laa.dstew.payments.claimsdata.runner;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class ReplicationSummaryRunnerTest {

  @Mock private JdbcTemplate jdbcTemplate;

  @InjectMocks private ReplicationSummaryRunner runner;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void run_shouldProcessTablesAndUpdateSummary() {
    // Arrange
    LocalDate yesterday = LocalDate.now().minusDays(1);

    // Mock the list of tables returned by queryForList
    List<Map<String, Object>> mockTables =
        List.of(
            Map.of("schemaname", "claims", "tablename", "case_data"),
            Map.of("schemaname", "claims", "tablename", "payments"));

    when(jdbcTemplate.queryForList(anyString())).thenReturn(mockTables);

    // Mock counts for each table
    when(jdbcTemplate.queryForObject(
            startsWith("SELECT count(*) FROM claims.case_data WHERE created_on < ?"),
            eq(Long.class)))
        .thenReturn(10L);
    when(jdbcTemplate.queryForObject(
            startsWith("SELECT count(*) FROM claims.case_data WHERE updated_on BETWEEN ? AND ?"),
            eq(Long.class),
            any(Timestamp.class),
            any(Timestamp.class)))
        .thenReturn(2L);

    when(jdbcTemplate.queryForObject(
            startsWith("SELECT count(*) FROM claims.payments"), eq(Long.class)))
        .thenReturn(20L);
    when(jdbcTemplate.queryForObject(
            startsWith("SELECT count(*) FROM claims.payments WHERE updated_on BETWEEN ? AND ?"),
            eq(Long.class),
            any(Timestamp.class),
            any(Timestamp.class)))
        .thenReturn(5L);

    // Act
    assertThatNoException().isThrownBy(() -> runner.run(mock(ApplicationArguments.class)));

    // Assert
    // Verify the upsert was executed twice (once per table)
    verify(jdbcTemplate, times(2))
        .update(
            argThat(sql -> sql.contains("INSERT INTO claims.replication_summary")),
            any(),
            eq(yesterday),
            any(),
            any());

    // Verify we queried the table list
    verify(jdbcTemplate)
        .queryForList(
            contains(
                "FROM pg_publication_tables t\nWHERE t.pubname = 'claims_reporting_service_pub'"));
  }

  @Test
  void run_shouldDoNothingWhenNoTablesFound() {
    when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

    assertThatNoException().isThrownBy(() -> runner.run(mock(ApplicationArguments.class)));

    // verify no insert/update
    verify(jdbcTemplate, never()).update(anyString(), any(), any());
  }

  @Test
  void run_shouldSkipTablesWithInvalidNames() {
    // Arrange: invalid schema and table names
    List<Map<String, Object>> mockTables =
        List.of(
            Map.of("schemaname", "claims", "tablename", "valid_table"),
            Map.of("schemaname", "invalid schema", "tablename", "table1"), // space in schema
            Map.of("schemaname", "claims", "tablename", "invalid-table"), // dash in table
            Map.of("schemaname", "123claims", "tablename", "table2"), // starts with number
            Map.of("schemaname", "claims", "tablename", "table with space"), // space in table
            Map.of(
                "schemaname",
                "claims",
                "tablename",
                "valid_table; DROP TABLE users; --") // attempted SQL injection
            );

    when(jdbcTemplate.queryForList(anyString())).thenReturn(mockTables);

    // Only the valid table should be processed
    when(jdbcTemplate.queryForObject(
            startsWith("SELECT count(*) FROM \"claims\".\"valid_table\" WHERE created_on < ?"),
            eq(Long.class),
            any(Timestamp.class)))
        .thenReturn(42L);
    when(jdbcTemplate.queryForObject(
            startsWith(
                "SELECT count(*) FROM \"claims\".\"valid_table\" WHERE updated_on BETWEEN ? AND ?"),
            eq(Long.class),
            any(Timestamp.class),
            any(Timestamp.class)))
        .thenReturn(7L);

    // Act
    assertThatNoException().isThrownBy(() -> runner.run(mock(ApplicationArguments.class)));

    // Assert: only one upsert for the valid table
    verify(jdbcTemplate, times(1))
        .update(
            argThat(sql -> sql.contains("INSERT INTO claims.replication_summary")),
            eq("claims.valid_table"),
            any(),
            any(),
            any());

    // No upsert for invalid tables (including attempted SQL injection)
    verify(jdbcTemplate, times(1))
        .queryForObject(
            startsWith("SELECT count(*) FROM \"claims\".\"valid_table\" WHERE created_on < ?"),
            eq(Long.class),
            any(Timestamp.class));
    verify(jdbcTemplate, times(1))
        .queryForObject(
            startsWith(
                "SELECT count(*) FROM \"claims\".\"valid_table\" WHERE updated_on BETWEEN ? AND ?"),
            eq(Long.class),
            any(Timestamp.class),
            any(Timestamp.class));

    // Ensure no update for invalid tables (by checking total update calls is 1)
    verify(jdbcTemplate, times(1)).update(anyString(), any(), any(), any(), any());

    // Ensure no query or update for the SQL injection table
    verify(jdbcTemplate, never())
        .queryForObject(contains("valid_table; DROP TABLE users; --"), any(Class.class), any());
    verify(jdbcTemplate, never())
        .update(
            anyString(),
            eq("\"claims\".\"valid_table; DROP TABLE users; --\""),
            any(),
            any(),
            any());
  }

  @Test
  void run_shouldPropagateDataAccessExceptionAndLog() {
    // Arrange: jdbcTemplate throws a DataAccessException when querying tables
    DataAccessResourceFailureException dae = new DataAccessResourceFailureException("DB down");
    when(jdbcTemplate.queryForList(anyString())).thenThrow(dae);

    // attach list appender to capture logs
    Logger logger = (Logger) LoggerFactory.getLogger(ReplicationSummaryRunner.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);

    // Act & Assert: exception is propagated
    org.junit.jupiter.api.Assertions.assertThrows(
        DataAccessResourceFailureException.class,
        () -> runner.run(mock(ApplicationArguments.class)));

    // Verify an error log was emitted
    boolean foundError =
        listAppender.list.stream()
            .anyMatch(
                e ->
                    e.getLevel().equals(Level.ERROR)
                        && e.getFormattedMessage().contains("Database access error"));
    org.junit.jupiter.api.Assertions.assertTrue(
        foundError, "Expected an ERROR log for DataAccessException");

    // cleanup
    logger.detachAppender(listAppender);
  }

  @Test
  void run_shouldPropagateUnexpectedExceptionAndLog() {
    // Arrange: jdbcTemplate throws a generic runtime exception
    RuntimeException rte = new RuntimeException("boom");
    when(jdbcTemplate.queryForList(anyString())).thenThrow(rte);

    // attach list appender to capture logs
    Logger logger = (Logger) LoggerFactory.getLogger(ReplicationSummaryRunner.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);

    // Act & Assert: runtime exception is propagated
    org.junit.jupiter.api.Assertions.assertThrows(
        RuntimeException.class, () -> runner.run(mock(ApplicationArguments.class)));

    // Verify an error log was emitted for unexpected error
    boolean foundError =
        listAppender.list.stream()
            .anyMatch(
                e ->
                    e.getLevel().equals(Level.ERROR)
                        && e.getFormattedMessage()
                            .contains("Unexpected error while updating replication summary"));
    org.junit.jupiter.api.Assertions.assertTrue(
        foundError, "Expected an ERROR log for unexpected exception");

    // cleanup
    logger.detachAppender(listAppender);
  }
}
