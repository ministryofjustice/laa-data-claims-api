package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryEvent;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryEventType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;

/**
 * Presentation mapper for claim history events. Encapsulates any presentation-only transformations,
 * such as suppressing derived fee.* change entries that are a direct consequence of a provider
 * requested claim.feeCode amendment.
 */
@Component
@RequiredArgsConstructor
public class ClaimHistoryEventMapper {

  private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  /**
   * Map a query projection row to the API model, applying presentation filtering rules.
   */
  public ClaimHistoryEvent toModel(ClaimHistoryEventRow row) {
    Map<String, Object> metadata = toMetadataMap(row.metadata());

    // Presentation rule: when an AMENDMENT contains both a provider-requested change to the
    // claim fee code (claim.feeCode with change_source REQUESTED) and a derived FSP repricing
    // change to the calculated fee (fee.feeCode with change_source FSP), suppress the latter
    // in the API response to avoid confusing users. This does not modify persisted data.
    if (Objects.equals(row.eventType(), "AMENDMENT") && metadata.containsKey("changes")) {
      Object changesObj = metadata.get("changes");
      if (changesObj instanceof List<?> changesList) {
        // Detect whether a provider-requested change to claim.feeCode exists
        boolean hasRequestedClaimFeeCode = changesList.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .anyMatch(change -> {
              Object fieldIdentifier = change.get("field_identifier");
              Object changeSource = change.get("change_source");
              return "claim.feeCode".equals(fieldIdentifier) && "REQUESTED".equals(changeSource);
            });

        if (hasRequestedClaimFeeCode) {
          // Remove only those change entries that represent fee.feeCode changes sourced from FSP.
          List<Object> filtered = new ArrayList<>();
          for (Object item : changesList) {
            if (!(item instanceof Map<?, ?> change)) {
              filtered.add(item);
              continue;
            }
            Object fieldIdentifier = change.get("field_identifier");
            Object changeSource = change.get("change_source");
            if ("fee.feeCode".equals(fieldIdentifier) && "FSP".equals(changeSource)) {
              // drop this derived FSP fee change as it is a consequence of the claim.feeCode amendment
              continue;
            }
            filtered.add(change);
          }
          metadata.put("changes", filtered);
        }
      }
    }

    return ClaimHistoryEvent.builder()
        .eventType(ClaimHistoryEventType.fromValue(row.eventType()))
        .eventTimestamp(row.eventTimestamp() == null ? null : OffsetDateTime.ofInstant(row.eventTimestamp(), ZoneOffset.UTC))
        .actorId(row.actorId())
        .sourceId(row.sourceId())
        .metadata(metadata)
        .build();
  }

  private Map<String, Object> toMetadataMap(JsonNode metadata) {
    if (metadata == null || !metadata.isObject()) {
      return Map.of();
    }
    return objectMapper.convertValue(metadata, METADATA_TYPE);
  }
}

