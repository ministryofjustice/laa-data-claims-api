package uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcExternalApiCallLogRepository unit tests")
class JdbcExternalApiCallLogRepositoryTest {
  @Mock private JdbcClient jdbcClient;
  @Mock private JdbcClient.StatementSpec spec;

  private JdbcExternalApiCallLogRepository repository;

  @BeforeEach
  void setUp() {
    when(jdbcClient.sql(anyString())).thenReturn(spec);
    when(spec.param(anyString(), any())).thenReturn(spec);
    when(spec.param(anyString(), any(), anyInt())).thenReturn(spec);
    when(spec.update()).thenReturn(1);

    repository = new JdbcExternalApiCallLogRepository(jdbcClient);
  }

  @Test
  @DisplayName("insert targets audit.external_api_call_log table")
  void insertTargetsAuditTable() {
    repository.insert(fullEntry());

    verify(jdbcClient).sql(contains("INSERT INTO audit.external_api_call_log"));
    verify(spec).update();
  }

  @Test
  @DisplayName("Binds every column by name")
  void bindsAllColumns() {
    ExternalApiCallLogEntry e = fullEntry();

    repository.insert(e);

    verify(spec).param("id", e.id());
    verify(spec).param("systemType", e.systemType().name());
    verify(spec).param("endpoint", e.endpoint());
    verify(spec).param("httpMethod", e.httpMethod());
    verify(spec).param("requestPayload", e.requestPayload(), Types.OTHER);
    verify(spec).param("responsePayload", e.responsePayload(), Types.OTHER);
    verify(spec).param("httpStatus", e.httpStatus(), Types.INTEGER);
    verify(spec).param("submissionId", e.submissionId(), Types.OTHER);
    verify(spec).param("claimId", e.claimId(), Types.OTHER);
    verify(spec)
        .param("createdOn", e.createdOn().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
  }

  private ExternalApiCallLogEntry fullEntry() {
    return new ExternalApiCallLogEntry(
        UUID.randomUUID(),
        ExternalSystemType.FEE_SCHEME_PLATFORM,
        "/api/v1/fee-calculation",
        "POST",
        "{\"feeCode\":\"CAPA\"}",
        "{\"feeCalculation\":{}}",
        200,
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instant.parse("2026-09-11T10:15:30Z"));
  }
}
