package uk.gov.justice.laa.dstew.payments.claimsdata.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;

@SpringBootTest(properties = "replication.summary.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class ReplicationSummaryRunnerIntegrationTest extends AbstractIntegrationTest {

  public static final int BULK_SUBMISSION_INDEX = 0;
  public static final int SUBMISSION_INDEX = 7;
  public static final int REPLICATED_TABLES_COUNT = 8;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ReplicationSummaryRunner runner;

  @BeforeEach
  void resetDatabase() {
    jdbcTemplate.execute("TRUNCATE claims.replication_summary CASCADE");
    jdbcTemplate.execute("TRUNCATE claims.bulk_submission CASCADE");
    jdbcTemplate.execute("TRUNCATE claims.submission CASCADE");

    jdbcTemplate.update(
        """
    INSERT INTO claims.bulk_submission (
        id, data, status, error_code, error_description, created_by_user_id,
        created_on, updated_by_user_id, updated_on, authorised_offices
    ) VALUES
        ('11111111-1111-1111-1111-111111111111', '{"ID": "1"}', 'READY_FOR_PARSING', NULL, NULL,
         'test_user', NOW() - interval '3 day', NULL, NOW() - interval '1 day', 'OfficeA,OfficeB'),
        ('11111111-1111-1111-1111-111111111112', '{"ID": "2"}', 'READY_FOR_PARSING', NULL, NULL,
         'test_user', NOW() - interval '4 day', NULL, NOW() - interval '2 day', 'OfficeA,OfficeB');
    """);

    jdbcTemplate.update(
        """
    INSERT INTO claims.submission (
        id, bulk_submission_id, office_account_number, submission_period, area_of_law, status,
        crime_schedule_number, previous_submission_id, is_nil_submission, number_of_claims,
        error_messages, created_by_user_id, created_on, updated_on, provider_user_id
    ) VALUES
        ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111',
         'OA001', '2025-04', 'Crime', 'VALIDATION_SUCCEEDED', 'CSN001', NULL, FALSE, 1,
         NULL, 'test_user', NOW() - interval '3 day', NOW() - interval '3 day', 'test provider user'),
        ('22222222-2222-2222-2222-222222222223', '11111111-1111-1111-1111-111111111112',
         'OA001', '2025-04', 'Crime', 'VALIDATION_SUCCEEDED', 'CSN001', NULL, FALSE, 1,
         NULL, 'test_user', NOW() - interval '1 day', NOW() - interval '1 day', 'test provider user'),
        ('22222222-2222-2222-2222-222222222224', '11111111-1111-1111-1111-111111111112',
         'OA001', '2025-04', 'Crime', 'VALIDATION_SUCCEEDED', 'CSN001', NULL, FALSE, 1,
         NULL, 'test_user', NOW() - interval '1 day', NOW() - interval '1 day', 'test provider user');
    """);
  }

  @Test
  void shouldInsertReplicationSummaryForAllTables() throws Exception {
    runner.run((ApplicationArguments) null);

    List<Map<String, Object>> summaries =
        jdbcTemplate.queryForList(
            """
            SELECT * FROM claims.replication_summary ORDER BY table_name
        """);

    assertThat(summaries).hasSize(REPLICATED_TABLES_COUNT);

    Map<String, Object> bulkSubmission = summaries.get(BULK_SUBMISSION_INDEX);
    Map<String, Object> submission = summaries.get(SUBMISSION_INDEX);

    assertThat(bulkSubmission.get("table_name")).isEqualTo("claims.bulk_submission");
    assertThat(((Number) bulkSubmission.get("record_count")).longValue()).isEqualTo(2L);
    assertThat(((Number) bulkSubmission.get("updated_count")).longValue()).isEqualTo(1L);

    assertThat(submission.get("table_name")).isEqualTo("claims.submission");
    assertThat(((Number) submission.get("record_count")).longValue()).isEqualTo(3L);
    assertThat(((Number) submission.get("updated_count")).longValue()).isEqualTo(2L);
  }
}
