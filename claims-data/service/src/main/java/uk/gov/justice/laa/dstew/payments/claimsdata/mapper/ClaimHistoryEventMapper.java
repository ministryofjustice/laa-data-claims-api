package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryChangeEntry;
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

  /** Map a query projection row to the API model, applying presentation filtering rules. */
  public ClaimHistoryEvent toModel(ClaimHistoryEventRow row) {
    JsonNode metadataNode = row.metadata();
    Map<String, Object> metadata = toMetadataMap(metadataNode);

    // Presentation rule: when an AMENDMENT contains both a provider-requested change to the
    // claim fee code (claim.feeCode with change_source REQUESTED) and a derived FSP repricing
    // change to the calculated fee (fee.feeCode with change_source FSP), suppress the latter
    // in the API response to avoid confusing users. This does not modify persisted data.
    if (Objects.equals(row.eventType(), "AMENDMENT")
        && metadataNode != null
        && metadataNode.has("changes")) {
      JsonNode changesNode = metadataNode.get("changes");
      if (changesNode != null && changesNode.isArray()) {
        List<ClaimHistoryChangeEntry> changes =
            objectMapper.convertValue(
                changesNode, new TypeReference<List<ClaimHistoryChangeEntry>>() {});

        boolean hasRequestedClaimFeeCode =
            changes.stream()
                .anyMatch(
                    c ->
                        AmendmentFieldIdentifiers.ClaimFields.FEE_CODE.equals(
                                c.getFieldIdentifier())
                            && ClaimHistoryChangeEntry.ChangeSourceEnum.REQUESTED.equals(
                                c.getChangeSource()));

        List<ClaimHistoryChangeEntry> resultList = changes;
        if (hasRequestedClaimFeeCode) {
          resultList =
              changes.stream()
                  .filter(
                      c ->
                          !(AmendmentFieldIdentifiers.FeeFields.FEE_CODE.equals(
                                  c.getFieldIdentifier())
                              && ClaimHistoryChangeEntry.ChangeSourceEnum.FSP.equals(
                                  c.getChangeSource())))
                  .collect(Collectors.toList());
        }

        // Put the typed list back into metadata so the API model carries typed entries
        metadata.put("changes", resultList);
      }
    }

    return ClaimHistoryEvent.builder()
        .eventType(ClaimHistoryEventType.fromValue(row.eventType()))
        .eventTimestamp(
            row.eventTimestamp() == null
                ? null
                : OffsetDateTime.ofInstant(row.eventTimestamp(), ZoneOffset.UTC))
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
