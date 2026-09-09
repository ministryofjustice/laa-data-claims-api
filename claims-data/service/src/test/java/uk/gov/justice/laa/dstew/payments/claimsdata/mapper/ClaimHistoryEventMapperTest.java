package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryChangeEntry;
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
  @DisplayName("Removes derived fee.feeCode when claim.feeCode REQUESTED is present")
  void removesDerivedFeeFeeCodeWhenClaimFeeCodeRequestedPresent() throws Exception {
    String json =
        """
        {
          "changes": [
            {
              "field_identifier": "claim.feeCode",
              "change_source": "REQUESTED",
              "before": "CAPA",
              "after": "CLIN"
            },
            {
              "field_identifier": "fee.feeCode",
              "change_source": "FSP",
              "before": "CAPA",
              "after": "CLIN"
            }
          ]
        }
        """;

    JsonNode node = objectMapper.readTree(json);

    ClaimHistoryEventRow row =
        new ClaimHistoryEventRow(
            "AMENDMENT", Instant.now(), "user1", java.util.UUID.randomUUID(), node, 1L);

    ClaimHistoryEvent event = presenter.toModel(row);

    Object changes = event.getMetadata().get("changes");
    assertNotNull(changes);
    assertInstanceOf(List.class, changes);
    @SuppressWarnings("unchecked")
    List<ClaimHistoryChangeEntry> list = (List<ClaimHistoryChangeEntry>) changes;

    // Expect the derived fee.feeCode FSP entry to be suppressed
    assertEquals(1, list.size());
    assertEquals("claim.feeCode", list.getFirst().getFieldIdentifier());
  }

  @Test
  @DisplayName("Retains fee.feeCode when no claim.feeCode REQUESTED is present")
  void retainsFeeFeeCodeWhenNoClaimFeeCodeRequested() throws Exception {
    String json =
        """
        {
          "changes": [
            {
              "field_identifier": "fee.feeCode",
              "change_source": "FSP",
              "before": "CAPA",
              "after": "CLIN"
            }
          ]
        }
        """;

    JsonNode node = objectMapper.readTree(json);

    ClaimHistoryEventRow row =
        new ClaimHistoryEventRow(
            "AMENDMENT", Instant.now(), "user1", java.util.UUID.randomUUID(), node, 1L);

    ClaimHistoryEvent event = presenter.toModel(row);

    Object changes = event.getMetadata().get("changes");
    assertNotNull(changes);
    assertInstanceOf(List.class, changes);
    @SuppressWarnings("unchecked")
    List<ClaimHistoryChangeEntry> list = (List<ClaimHistoryChangeEntry>) changes;

    // Expect the fee.feeCode FSP entry to remain when no claim.feeCode REQUESTED is present
    assertEquals(1, list.size());
    assertEquals("fee.feeCode", list.getFirst().getFieldIdentifier());
  }
}
