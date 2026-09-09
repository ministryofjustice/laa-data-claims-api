package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryEvent;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;

class ClaimHistoryEventMapperTest {

  private ObjectMapper objectMapper;
  private ClaimHistoryEventMapper presenter;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    presenter = new ClaimHistoryEventMapper(objectMapper);
  }

  @Test
  void removesDerivedFeeFeeCode_whenClaimFeeCodeRequestedPresent() throws Exception {
    String json = "{\n"
        + "  \"changes\": [\n"
        + "    {\n"
        + "      \"field_identifier\": \"claim.feeCode\",\n"
        + "      \"change_source\": \"REQUESTED\",\n"
        + "      \"before\": \"CAPA\",\n"
        + "      \"after\": \"CLIN\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"field_identifier\": \"fee.feeCode\",\n"
        + "      \"change_source\": \"FSP\",\n"
        + "      \"before\": \"CAPA\",\n"
        + "      \"after\": \"CLIN\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    JsonNode node = objectMapper.readTree(json);

    ClaimHistoryEventRow row = new ClaimHistoryEventRow("AMENDMENT", Instant.now(), "user1", java.util.UUID.randomUUID(), node, 1L);

    ClaimHistoryEvent event = presenter.toModel(row);

    Object changes = event.getMetadata().get("changes");
    assertNotNull(changes);
    assertTrue(changes instanceof List);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> list = (List<Map<String, Object>>) changes;

    // Expect the derived fee.feeCode FSP entry to be suppressed
    assertEquals(1, list.size());
    assertEquals("claim.feeCode", list.get(0).get("field_identifier"));
  }

  @Test
  void retainsFeeFeeCode_whenNoClaimFeeCodeRequested() throws Exception {
    String json = "{\n"
        + "  \"changes\": [\n"
        + "    {\n"
        + "      \"field_identifier\": \"fee.feeCode\",\n"
        + "      \"change_source\": \"FSP\",\n"
        + "      \"before\": \"CAPA\",\n"
        + "      \"after\": \"CLIN\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    JsonNode node = objectMapper.readTree(json);

    ClaimHistoryEventRow row = new ClaimHistoryEventRow("AMENDMENT", Instant.now(), "user1", java.util.UUID.randomUUID(), node, 1L);

    ClaimHistoryEvent event = presenter.toModel(row);

    Object changes = event.getMetadata().get("changes");
    assertNotNull(changes);
    assertTrue(changes instanceof List);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> list = (List<Map<String, Object>>) changes;

    // Expect the fee.feeCode FSP entry to remain when no claim.feeCode REQUESTED is present
    assertEquals(1, list.size());
    assertEquals("fee.feeCode", list.get(0).get("field_identifier"));
  }
}

