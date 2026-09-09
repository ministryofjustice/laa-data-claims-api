package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryChangeEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryEvent;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryEventType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;

/**
 * Presentation mapper for claim history events.
 *
 * <p>This component converts repository projection rows ({@link ClaimHistoryEventRow}) into the API
 * model ({@link ClaimHistoryEvent}) and applies presentation-only transformations. The
 * transformations are intentionally non-destructive: persisted audit records and repository SQL are
 * not modified. The primary behaviour implemented here is a suppression rule for AMENDMENT events:
 * when an amendment contains a provider-requested change to the claim fee code (i.e. a change entry
 * with field identifier {@code claim.feeCode} and change source {@code REQUESTED}) any derived
 * repricing entry that updates {@code fee.feeCode} with change source {@code FSP} will be removed
 * from the metadata returned to API consumers. This avoids presenting a duplicated/ambiguous change
 * to clients while preserving the stored audit rows.
 *
 * <p>Implementation notes:
 *
 * <ul>
 *   <li>Uses Jackson to convert the JSON metadata node into a typed Map and typed {@link
 *       ClaimHistoryChangeEntry} list for reliable inspection.
 *   <li>All rules are applied at presentation time only; no database changes are made.
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ClaimHistoryEventMapper {

  private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {};

  /** JSON metadata key used to hold the amendment change entries. */
  public static final String CHANGES = "changes";

  private final ObjectMapper objectMapper;

  /**
   * Map a repository projection row to the API model, applying presentation filtering rules.
   *
   * <p>The returned {@link ClaimHistoryEvent} contains the same data as the projection row with the
   * following differences:
   *
   * <ul>
   *   <li>When the projection {@code metadata} contains a {@code changes} array, the array is
   *       converted into a typed {@code List<ClaimHistoryChangeEntry>} and placed back into the
   *       returned metadata map so API consumers receive typed entries instead of untyped JSON
   *       maps.
   *   <li>If the event is an AMENDMENT and the changes list contains a provider-requested change to
   *       {@code claim.feeCode}, any derived {@code fee.feeCode} change with source {@code FSP}
   *       will be removed from the list before returning.
   * </ul>
   *
   * <p>Note: this method is defensive and will treat missing or non-object metadata as an empty
   * map.
   *
   * @param row the projection row produced by the repository; must not be null
   * @return a {@link ClaimHistoryEvent} ready for JSON serialization to API clients
   */
  public ClaimHistoryEvent toModel(ClaimHistoryEventRow row) {
    JsonNode metadataNode = row.metadata();
    Map<String, Object> metadata = toMetadataMap(metadataNode);

    // Presentation rule: when an AMENDMENT contains both a provider-requested change to the
    // claim fee code (claim.feeCode with change_source REQUESTED) and a derived FSP repricing
    // change to the calculated fee (fee.feeCode with change_source FSP), suppress the latter
    // in the API response to avoid confusing users. This does not modify persisted data.
    if (Objects.equals(row.eventType(), "AMENDMENT")
        && metadataNode != null
        && metadataNode.has(CHANGES)) {
      JsonNode changesNode = metadataNode.get(CHANGES);
      if (changesNode != null && changesNode.isArray()) {
        List<ClaimHistoryChangeEntry> changes =
            objectMapper.convertValue(
                changesNode, new TypeReference<List<ClaimHistoryChangeEntry>>() {});

        List<ClaimHistoryChangeEntry> resultList = changes;
        if (containsRequestedClaimFeeCode(changes)) {
          resultList =
              changes.stream()
                  .filter(
                      c ->
                          !(AmendmentFieldIdentifiers.FeeFields.FEE_CODE.equals(
                                  c.getFieldIdentifier())
                              && ClaimHistoryChangeEntry.ChangeSourceEnum.FSP.equals(
                                  c.getChangeSource())))
                  .toList();
        }

        // Put the typed list back into metadata so the API model carries typed entries
        metadata.put(CHANGES, resultList);
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

  /**
   * Convert the metadata {@link JsonNode} returned by the repository into a mutable Map.
   *
   * <p>If the provided node is {@code null} or not a JSON object the method returns an empty
   * immutable map. Otherwise, Jackson is used to convert the node into a {@code Map<String,
   * Object>} suitable for attaching typed entries (for example the typed {@code changes} list).
   *
   * @param metadata the metadata node from the projection
   * @return a Map representation of metadata, or an empty map when the node is not an object
   */
  private Map<String, Object> toMetadataMap(JsonNode metadata) {
    if (metadata == null || !metadata.isObject()) {
      return Map.of();
    }
    return objectMapper.convertValue(metadata, METADATA_TYPE);
  }

  /**
   * Return {@code true} when the provided list of change entries contains a provider-requested
   * amendment to the claim fee code.
   *
   * <p>The method is null-safe and returns {@code false} for a {@code null} list. It compares the
   * canonical field identifier constant {@link AmendmentFieldIdentifiers.ClaimFields#FEE_CODE}
   * against the entry {@code fieldIdentifier} and checks that the entry {@code changeSource} is
   * {@link ClaimHistoryChangeEntry.ChangeSourceEnum#REQUESTED}.
   *
   * @param changes typed list of amendment change entries to inspect
   * @return {@code true} when a provider-requested claim.feeCode change is present
   */
  private boolean containsRequestedClaimFeeCode(List<ClaimHistoryChangeEntry> changes) {
    if (changes == null) {
      return false;
    }
    return changes.stream()
        .anyMatch(
            c ->
                AmendmentFieldIdentifiers.ClaimFields.FEE_CODE.equals(c.getFieldIdentifier())
                    && ClaimHistoryChangeEntry.ChangeSourceEnum.REQUESTED.equals(
                        c.getChangeSource()));
  }
}
