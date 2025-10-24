package uk.gov.justice.laa.dstew.payments.claimsdata.runner;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class ReplicationSummaryRunnerTest {

  @Mock
  private JdbcTemplate jdbcTemplate;

  @InjectMocks
  private ReplicationSummaryRunner runner;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void run_shouldProcessTablesAndUpdateSummary() {
    // Arrange
    LocalDate yesterday = LocalDate.now().minusDays(1);

    // Mock the list of tables returned by queryForList
    List<Map<String, Object>> mockTables = List.of(
        Map.of("schemaname", "claims", "tablename", "case_data"),
        Map.of("schemaname", "claims", "tablename", "payments")
    );

    when(jdbcTemplate.queryForList(anyString())).thenReturn(mockTables);

    // Mock counts for each table
    when(jdbcTemplate.queryForObject(startsWith("SELECT count(*) FROM claims.case_data WHERE created_on < ?"), eq(Long.class)))
        .thenReturn(10L);
    when(jdbcTemplate.queryForObject(
        startsWith("SELECT count(*) FROM claims.case_data WHERE updated_on BETWEEN ? AND ?"),
        eq(Long.class), any(Timestamp.class), any(Timestamp.class)))
        .thenReturn(2L);

    when(jdbcTemplate.queryForObject(startsWith("SELECT count(*) FROM claims.payments"), eq(Long.class)))
        .thenReturn(20L);
    when(jdbcTemplate.queryForObject(
        startsWith("SELECT count(*) FROM claims.payments WHERE updated_on BETWEEN ? AND ?"),
        eq(Long.class), any(Timestamp.class), any(Timestamp.class)))
        .thenReturn(5L);

    // Act
    assertThatNoException().isThrownBy(() -> runner.run(mock(ApplicationArguments.class)));

    // Assert
    // Verify the upsert was executed twice (once per table)
    verify(jdbcTemplate, times(2)).update(
        argThat(sql -> sql.contains("INSERT INTO claims.replication_summary")),
        any(), eq(yesterday), any(), any()
    );

    // Verify we queried the table list
    verify(jdbcTemplate).queryForList(
        contains("FROM pg_publication_tables t\nWHERE t.pubname = 'claims_reporting_service_pub'")
    );
  }

  @Test
  void run_shouldDoNothingWhenNoTablesFound() {
    when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

    assertThatNoException().isThrownBy(() -> runner.run(mock(ApplicationArguments.class)));

    // verify no insert/update
    verify(jdbcTemplate, never()).update(anyString(), any(), any());
  }
}