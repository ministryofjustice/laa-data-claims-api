package uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit;

import java.sql.Types;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A repository for logging external API calls to the audit database.
 *
 * <p>This is deliberately not a JPA entity/repository for three reasons:
 *
 * <ol>
 *   <li>The insert must execute immediately so it can be caught by the caller (AC5: non-blocking).
 *       A JPA repository would delay the insert until the transaction commits, after the try/catch
 *       resolves.
 *   <li>Javers snapshots every {@code save} on the repositories in the {@code repository} package.
 *       Audit rows are themselves an audit record, so we don't want them snapshot again.
 *   <li>Payloads must be stored as received. The JSON text is passed through JDBC with no
 *       transformation.
 * </ol>
 *
 * <p>The REQUIRES_NEW transaction propagation ensures that the insert is committed immediately,
 * even if the caller's transaction rolls back.
 */
@Repository
public class JdbcExternalApiCallLogRepository implements ExternalApiCallLogRepository {

  private static final String INSERT_SQL =
      """
        INSERT INTO audit.external_api_call_log
            (id, system_type, endpoint, http_method, request_payload, response_payload,
            http_status, submission_id, claim_id, created_on)
        VALUES
            (:id, :systemType, :endpoint, :httpMethod, :requestPayload, :responsePayload,
            :httpStatus, :submissionId, :claimId, :createdOn)""";

  private final JdbcClient jdbcClient;

  public JdbcExternalApiCallLogRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void insert(ExternalApiCallLogEntry entry) {
    jdbcClient
        .sql(INSERT_SQL)
        .param("id", entry.id())
        .param("systemType", entry.systemType().name())
        .param("endpoint", entry.endpoint())
        .param("httpMethod", entry.httpMethod())
        .param("requestPayload", entry.requestPayload(), Types.OTHER)
        .param("responsePayload", entry.responsePayload(), Types.OTHER)
        .param("httpStatus", entry.httpStatus(), Types.INTEGER)
        .param("submissionId", entry.submissionId(), Types.OTHER)
        .param("claimId", entry.claimId(), Types.OTHER)
        .param(
            "createdOn", entry.createdOn().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
  }
}
