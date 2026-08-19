package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;

/**
 * Maps a single row of the unified claim-history {@code UNION ALL} result set into a {@link
 * ClaimHistoryEventRow}.
 *
 * <p>The {@code metadata} column is produced server-side by PostgreSQL {@code jsonb_build_object}
 * and arrives as JSON text; it is parsed into a Jackson {@link JsonNode} here so downstream
 * consumers (notably the amendment {@code diff}) receive the structured document unchanged.
 */
@Component
public class ClaimHistoryEventRowMapper implements RowMapper<ClaimHistoryEventRow> {

  private final ObjectMapper objectMapper;

  public ClaimHistoryEventRowMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public ClaimHistoryEventRow mapRow(ResultSet rs, int rowNum) throws SQLException {
    OffsetDateTime timestamp = rs.getObject("event_timestamp", OffsetDateTime.class);
    UUID sourceId = rs.getObject("source_id", UUID.class);

    return new ClaimHistoryEventRow(
        rs.getString("event_type"),
        timestamp == null ? null : timestamp.toInstant(),
        rs.getString("actor_id"),
        sourceId,
        readJson(rs.getString("metadata")));
  }

  private JsonNode readJson(String json) throws SQLException {
    if (json == null) {
      return objectMapper.nullNode();
    }
    try {
      return objectMapper.readTree(json);
    } catch (Exception ex) {
      throw new SQLException("Failed to parse claim history metadata JSON", ex);
    }
  }
}
