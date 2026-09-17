package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
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
 * <p><b>Why field identifiers are rewritten:</b> amendment diffs are persisted using canonical,
 * internal identifiers of the form {@code <entity>.<field>} (e.g. {@code claim.feeCode}, {@code
 * claimSummaryFee.netProfitCostsAmount}). These identifiers are the vocabulary shared by amendment
 * processing, PDA matching, FSP matching and diff persistence, but they leak internal entity
 * structure that API consumers should not need to know about. To keep the public API stable and
 * decoupled from that internal structure, every {@link
 * ClaimHistoryChangeEntry#getFieldIdentifier()} returned to clients is rewritten into a flattened,
 * snake_case field name before it leaves this mapper (e.g. {@code claim.feeCode} becomes {@code
 * fee_code}).
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

  /** Matches a lower-case (or digit) character immediately followed by an upper-case character. */
  private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

  private final ObjectMapper objectMapper;

  /**
   * Map a repository projection row to the API model, applying presentation filtering rules.
   *
   * <p>The returned {@link ClaimHistoryEvent} contains the same data as the projection row with the
   * following differences:
   *
   * <ul>
   *   <li>When the projection {@code metadata} contains a {@code changes} array and the event is an
   *       AMENDMENT, the array is converted into a typed {@code List<ClaimHistoryChangeEntry>} and
   *       placed back into the returned metadata map so API consumers receive typed entries instead
   *       of untyped JSON maps.
   *   <li>If the event is an AMENDMENT and the changes list contains a provider-requested change to
   *       {@code claim.feeCode}, any derived {@code fee.feeCode} change with source {@code FSP}
   *       will be removed from the list before returning. This suppression check runs against the
   *       original, internal identifiers, before the rewrite described below.
   *   <li>Every remaining change entry's {@code fieldIdentifier} is rewritten from its internal
   *       {@code <entity>.<field>} form into a flattened, snake_case, consumer-facing name (e.g.
   *       {@code claim.feeCode} &rarr; {@code fee_code}), so API consumers never see the internal
   *       amendment entity vocabulary. See {@link #toApiFieldIdentifier(String)} for the exact
   *       rule. This is a presentation-only rewrite: it happens last, has no bearing on the
   *       suppression rule above, and never touches the persisted data.
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
            objectMapper.convertValue(changesNode, new TypeReference<>() {});

        List<ClaimHistoryChangeEntry> resultList =
            !containsRequestedClaimFeeCode(changes)
                ? changes
                : changes.stream()
                    .filter(
                        c ->
                            !(AmendmentFieldIdentifiers.FeeFields.FEE_CODE.equals(
                                    c.getFieldIdentifier())
                                && ClaimHistoryChangeEntry.ChangeSourceEnum.FSP.equals(
                                    c.getChangeSource())))
                    .toList();

        // Put the typed list back into metadata so the API model carries typed entries.
        // Filtering above operates on the original, internal field identifiers. Only now that
        // filtering is complete do we translate identifiers into the consumer-facing format, so
        // the transformation never affects the filtering decision.
        List<ClaimHistoryChangeEntry> presentationList =
            CollectionUtils.isEmpty(resultList)
                ? List.of()
                : resultList.stream().map(this::toPresentationEntry).toList();
        metadata.put(CHANGES, presentationList);
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

  /**
   * Create a new {@link ClaimHistoryChangeEntry} whose {@code fieldIdentifier} has been converted
   * from the internal amendment identifier vocabulary into the flattened, snake_case,
   * consumer-facing format used in API responses.
   *
   * <p><b>Why:</b> amendment diff entries are persisted (and consumed by amendment processing, PDA
   * matching and FSP matching) using canonical {@code <entity>.<field>} identifiers such as {@code
   * client.clientForename} or {@code claimSummaryFee.netProfitCostsAmount}. Those identifiers
   * expose internal entity structure that API consumers have no need to know about, so this method
   * is the single point at which that internal vocabulary is translated into a stable,
   * presentation-only field name just before an entry leaves the mapper.
   *
   * <p>This method does not mutate the supplied entry; it returns a new instance built from a copy
   * of the original, with only the {@code fieldIdentifier} changed. All other fields ({@code
   * changeSource}, {@code before}, {@code after}) are preserved unchanged.
   *
   * @param entry the internal change entry, as persisted/produced by amendment diffing
   * @return a new {@link ClaimHistoryChangeEntry} with a consumer-facing {@code fieldIdentifier}
   */
  private ClaimHistoryChangeEntry toPresentationEntry(ClaimHistoryChangeEntry entry) {
    return entry.toBuilder()
        .fieldIdentifier(toApiFieldIdentifier(entry.getFieldIdentifier()))
        .build();
  }

  /**
   * Convert an internal amendment field identifier (e.g. {@code
   * claimSummaryFee.netProfitCostsAmount}) into the flattened, consumer-facing form (e.g. {@code
   * net_profit_costs_amount}) expected in API responses.
   *
   * <p><b>How:</b> the identifier is split on {@code "."} and only the final segment (the field
   * name) is retained, discarding the leading entity name(s); if there is no {@code "."} the whole
   * value is treated as the field name. That final segment is then converted from camelCase to
   * snake_case via {@link #toSnakeCase(String)}. Worked examples:
   *
   * <ul>
   *   <li>{@code client.clientForename} &rarr; {@code client_forename}
   *   <li>{@code claim.feeCode} &rarr; {@code fee_code}
   *   <li>{@code claimCase.caseStageCode} &rarr; {@code case_stage_code}
   *   <li>{@code claimSummaryFee.netProfitCostsAmount} &rarr; {@code net_profit_costs_amount}
   *   <li>{@code fee.totalAmount} &rarr; {@code total_amount}
   *   <li>{@code totalAmount} (no dot) &rarr; {@code total_amount}
   * </ul>
   *
   * <p>This method is null-safe and returns {@code null} for a {@code null} input.
   *
   * @param identifier the internal field identifier
   * @return the flattened, snake_case, consumer-facing field identifier, or {@code null}
   */
  private String toApiFieldIdentifier(String identifier) {
    if (identifier == null) {
      return null;
    }
    String[] segments = identifier.split("\\.");
    return toSnakeCase(segments[segments.length - 1]);
  }

  /**
   * Convert a camelCase string into snake_case, e.g. {@code "clientForename"} becomes {@code
   * "client_forename"}.
   *
   * <p>A {@code "_"} is inserted at every boundary where a lower-case letter or digit is
   * immediately followed by an upper-case letter (see {@link #CAMEL_CASE_BOUNDARY}), and the whole
   * string is then lower-cased. This is the final step used by {@link
   * #toApiFieldIdentifier(String)} to produce the snake_case field names expected by API consumers.
   *
   * @param value the camelCase value to convert
   * @return the snake_case equivalent, or {@code null} when the input is {@code null}
   */
  private String toSnakeCase(String value) {
    if (value == null) {
      return null;
    }
    return CAMEL_CASE_BOUNDARY.matcher(value).replaceAll("$1_$2").toLowerCase();
  }
}
