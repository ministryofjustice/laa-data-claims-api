package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalSystemType.FEE_SCHEME_PLATFORM;
import static uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalSystemType.PROVIDER_DETAILS_API;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalApiCallLogEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalApiCallLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@DisplayName("JdbcExternalApiCallLogRepository Integration Test")
class JdbcExternalApiCallLogRepositoryIntegrationTest extends AbstractIntegrationTest {
  private static final String SELECT_SQL =
      "SELECT * FROM audit.external_api_call_log WHERE id = :id";

  @Autowired private ExternalApiCallLogRepository repository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TransactionTemplate transactionTemplate;

  @AfterEach
  void cleanUp() {
    jdbcClient.sql("DELETE FROM audit.external_api_call_log").update();
  }

  @Test
  @DisplayName("Insert a successful call with every column populated")
  void insertSuccessfulCall() throws Exception {
    UUID id = Uuid7.timeBasedUuid();
    UUID submissionId = Uuid7.timeBasedUuid();
    UUID claimId = Uuid7.timeBasedUuid();
    Instant createdOn = Instant.parse("2026-04-22T11:26:00Z");
    String request = "{\"feeCode\":\"CAPA\", \"netProfitCosts\":100.50, \"boltOns\":[\"a\",\"b\"]}";
    String response = "{\"feeCalculation\":{\"totalAmount\":120.60, \"validationMethod\":[]}}";

    repository.insert(
        new ExternalApiCallLogEntry(
            id,
            FEE_SCHEME_PLATFORM,
            "/api/v1/fee-calculation",
            "POST",
            request,
            response,
            200,
            submissionId,
            claimId,
            createdOn));

    Map<String, Object> row = jdbcClient.sql(SELECT_SQL).param("id", id).query().singleRow();

    assertThat(row.get("id")).isEqualTo(id);
    assertThat(row.get("system_type")).isEqualTo(FEE_SCHEME_PLATFORM.name());
    assertThat(row.get("endpoint")).isEqualTo("/api/v1/fee-calculation");
    assertThat(row.get("http_method")).isEqualTo("POST");
    assertThat(row.get("http_status")).isEqualTo(200);
    assertThat(row.get("submission_id")).isEqualTo(submissionId);
    assertThat(row.get("claim_id")).isEqualTo(claimId);
    assertThat(((Timestamp) row.get("created_on")).toInstant()).isEqualTo(createdOn);

    // compare as JSON as the database may reformat the JSON text (e.g. whitespace, ordering of
    // keys)
    assertThat(asJson(row.get("request_payload"))).isEqualTo(objectMapper.readTree(request));
    assertThat(asJson(row.get("response_payload"))).isEqualTo(objectMapper.readTree(response));
  }

  @Test
  @DisplayName("Insert a no-response call with null response payload and null status (AC6)")
  void insertNoResponseCall() throws Exception {
    UUID id = Uuid7.timeBasedUuid();
    Instant createdOn = Instant.parse("2026-04-22T11:26:00Z");
    String request = "{\"feeCode\":\"CAPA\", \"netProfitCosts\":100.50, \"boltOns\":[\"a\",\"b\"]}";

    repository.insert(
        new ExternalApiCallLogEntry(
            id,
            PROVIDER_DETAILS_API,
            "/api/v1/provider-offices/0A123B/schedules",
            "GET",
            request,
            null,
            null,
            null,
            null,
            createdOn));

    Map<String, Object> row = jdbcClient.sql(SELECT_SQL).param("id", id).query().singleRow();

    assertThat(row.get("id")).isEqualTo(id);
    assertThat(row.get("system_type")).isEqualTo(PROVIDER_DETAILS_API.name());
    assertThat(row.get("endpoint")).isEqualTo("/api/v1/provider-offices/0A123B/schedules");
    assertThat(row.get("http_method")).isEqualTo("GET");
    assertThat(row.get("http_status")).isNull();
    assertThat(row.get("submission_id")).isNull();
    assertThat(row.get("claim_id")).isNull();
    assertThat(((Timestamp) row.get("created_on")).toInstant()).isEqualTo(createdOn);

    // compare as JSON as the database may reformat the JSON text (e.g. whitespace, ordering of
    // keys)
    assertThat(asJson(row.get("request_payload"))).isEqualTo(objectMapper.readTree(request));
    assertThat(row.get("response_payload")).isNull();
  }

  @Test
  @DisplayName("Insert an error response body (AC2)")
  void insertErrorResponse() {
    UUID id = Uuid7.timeBasedUuid();
    UUID claimId = Uuid7.timeBasedUuid();
    Instant createdOn = Instant.parse("2026-04-22T11:26:00Z");
    String errorBody = "{\"status\":400, \"detail\":\"Fee code not found\", \"errors\":[\"x\"]}";

    repository.insert(
        new ExternalApiCallLogEntry(
            id,
            FEE_SCHEME_PLATFORM,
            "/api/v1/fee-details/BAD",
            "GET",
            "{\"feeCode\":\"BAD\"}",
            errorBody,
            400,
            null,
            claimId,
            createdOn));

    Map<String, Object> row = jdbcClient.sql(SELECT_SQL).param("id", id).query().singleRow();

    assertThat(row.get("http_status")).isEqualTo(400);
    assertThat(row.get("submission_id")).isNull();
    assertThat(row.get("claim_id")).isEqualTo(claimId);
    assertThat(((Timestamp) row.get("created_on")).toInstant()).isEqualTo(createdOn);
  }

  @Test
  @DisplayName("Row survives a rolled-back outer transaction (AC5)")
  void rowSurvivesRolledBackOuterTransaction() throws Exception {
    UUID id = Uuid7.timeBasedUuid();
    Instant createdOn = Instant.parse("2026-04-22T11:26:00Z");

    ExternalApiCallLogEntry entry =
        new ExternalApiCallLogEntry(
            id,
            FEE_SCHEME_PLATFORM,
            "/api/v1/fee-calculation",
            "POST",
            "{}",
            "{}",
            200,
            Uuid7.timeBasedUuid(),
            Uuid7.timeBasedUuid(),
            createdOn);

    transactionTemplate.execute(
        status -> {
          repository.insert(entry);
          status.setRollbackOnly();
          return null;
        });

    Integer count =
        jdbcClient
            .sql("SELECT COUNT(*) FROM audit.external_api_call_log WHERE id = :id")
            .param("id", id)
            .query(Integer.class)
            .single();
    assertThat(count).isEqualTo(1);
  }

  private JsonNode asJson(Object jsonColumn) throws Exception {
    return objectMapper.readTree(jsonColumn.toString());
  }
}
