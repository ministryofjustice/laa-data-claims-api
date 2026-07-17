package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;

class ClaimHistoryEventRowMapperTest {

  private final ClaimHistoryEventRowMapper mapper =
      new ClaimHistoryEventRowMapper(new ObjectMapper());

  @Test
  void mapsAllColumns_andParsesMetadataJson() throws SQLException {
    UUID sourceId = UUID.randomUUID();
    OffsetDateTime timestamp = OffsetDateTime.parse("2026-04-22T11:26:00Z");
    ResultSet rs = mock(ResultSet.class);
    when(rs.getObject("event_timestamp", OffsetDateTime.class)).thenReturn(timestamp);
    when(rs.getObject("source_id", UUID.class)).thenReturn(sourceId);
    when(rs.getString("event_type")).thenReturn("SUBMISSION");
    when(rs.getString("actor_id")).thenReturn("provider-user-id");
    when(rs.getString("metadata")).thenReturn("{\"submission_period\":\"APR-2026\"}");

    ClaimHistoryEventRow row = mapper.mapRow(rs, 0);

    assertThat(row.eventType()).isEqualTo("SUBMISSION");
    assertThat(row.eventTimestamp()).isEqualTo(timestamp.toInstant());
    assertThat(row.actorId()).isEqualTo("provider-user-id");
    assertThat(row.sourceId()).isEqualTo(sourceId);
    assertThat(row.metadata().get("submission_period").asText()).isEqualTo("APR-2026");
  }

  @Test
  void mapsNullTimestamp_toNullInstant() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getObject("event_timestamp", OffsetDateTime.class)).thenReturn(null);
    when(rs.getObject("source_id", UUID.class)).thenReturn(UUID.randomUUID());
    when(rs.getString("event_type")).thenReturn("SUBMISSION");
    when(rs.getString("actor_id")).thenReturn("SYSTEM");
    when(rs.getString("metadata")).thenReturn("{}");

    ClaimHistoryEventRow row = mapper.mapRow(rs, 0);

    assertThat(row.eventTimestamp()).isNull();
  }

  @Test
  void mapsNullMetadata_toJsonNullNode() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getObject("event_timestamp", OffsetDateTime.class))
        .thenReturn(Instant.now().atOffset(ZoneOffset.UTC));
    when(rs.getObject("source_id", UUID.class)).thenReturn(UUID.randomUUID());
    when(rs.getString("event_type")).thenReturn("SUBMISSION");
    when(rs.getString("actor_id")).thenReturn("SYSTEM");
    when(rs.getString("metadata")).thenReturn(null);

    ClaimHistoryEventRow row = mapper.mapRow(rs, 0);

    assertThat(row.metadata().isNull()).isTrue();
  }

  @Test
  void throwsSqlException_whenMetadataIsInvalidJson() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getObject("event_timestamp", OffsetDateTime.class))
        .thenReturn(Instant.now().atOffset(ZoneOffset.UTC));
    when(rs.getObject("source_id", UUID.class)).thenReturn(UUID.randomUUID());
    when(rs.getString("event_type")).thenReturn("SUBMISSION");
    when(rs.getString("actor_id")).thenReturn("SYSTEM");
    when(rs.getString("metadata")).thenReturn("{not-json");

    assertThatThrownBy(() -> mapper.mapRow(rs, 0))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("claim history metadata");
  }
}
