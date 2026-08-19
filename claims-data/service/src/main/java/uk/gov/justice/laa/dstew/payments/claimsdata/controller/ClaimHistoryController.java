package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static uk.gov.justice.laa.dstew.payments.claimsdata.util.RateLimitUtils.get429Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.ClaimHistoryApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryEvent;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryEventType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimHistoryService;

/** Controller exposing a claim's unified, chronological history timeline. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ClaimHistoryController implements ClaimHistoryApi {

  private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {};

  private final ClaimHistoryService claimHistoryService;
  private final ObjectMapper objectMapper;

  @Override
  @RateLimiter(name = "claimRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<ClaimHistoryResultSet> getClaimHistory(UUID claimId, Integer limit) {
    List<ClaimHistoryEventRow> rows =
        limit == null
            ? claimHistoryService.getTimeline(claimId)
            : claimHistoryService.getTimeline(claimId, limit);

    List<ClaimHistoryEvent> events = rows.stream().map(this::toModel).toList();

    ClaimHistoryResultSet result =
        ClaimHistoryResultSet.builder().claimId(claimId).events(events).build();

    return ResponseEntity.ok(result);
  }

  private ClaimHistoryEvent toModel(ClaimHistoryEventRow row) {
    return ClaimHistoryEvent.builder()
        .eventType(ClaimHistoryEventType.fromValue(row.eventType()))
        .eventTimestamp(
            row.eventTimestamp() == null
                ? null
                : OffsetDateTime.ofInstant(row.eventTimestamp(), ZoneOffset.UTC))
        .actorId(row.actorId())
        .sourceId(row.sourceId())
        .metadata(toMetadataMap(row.metadata()))
        .build();
  }

  private Map<String, Object> toMetadataMap(JsonNode metadata) {
    if (metadata == null || !metadata.isObject()) {
      return Map.of();
    }
    return objectMapper.convertValue(metadata, METADATA_TYPE);
  }

  private ResponseEntity<String> genericFallback(RequestNotPermitted e) {
    return get429Response();
  }
}
