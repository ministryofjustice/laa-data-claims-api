package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static uk.gov.justice.laa.dstew.payments.claimsdata.util.RateLimitUtils.get429Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.ClaimHistoryApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;
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
  public ResponseEntity<ClaimHistoryResultSet> getClaimHistory(UUID claimId, Pageable pageable) {
    // Validate pageable parameters (raw request params and resolved Pageable).
    // Extracted to a helper to keep controller logic concise and reuseable.
    validatePageableParameters(pageable);

    List<ClaimHistoryEventRow> rows = claimHistoryService.getTimeline(claimId, pageable);

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

  /**
   * Validate raw request "page" and "size" parameters where possible and also validate the resolved
   * {@link Pageable} when it's concrete. This ensures invalid client input is surfaced as a 400
   * (via {@link ClaimBadRequestException}) rather than causing framework resolution to produce an
   * unpaged Pageable or later throw an internal exception.
   */
  private void validatePageableParameters(Pageable pageable) {
    ServletRequestAttributes attrs =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs != null) {
      HttpServletRequest req = attrs.getRequest();
      String pageParam = req.getParameter("page");
      String sizeParam = req.getParameter("size");
      if (pageParam != null) {
        try {
          int p = Integer.parseInt(pageParam);
          if (p < 0) {
            throw new ClaimBadRequestException("page must be >= 0 and size must be >= 1");
          }
        } catch (NumberFormatException e) {
          throw new ClaimBadRequestException("page must be an integer >= 0");
        }
      }
      if (sizeParam != null) {
        try {
          int s = Integer.parseInt(sizeParam);
          if (s < 1) {
            throw new ClaimBadRequestException("page must be >= 0 and size must be >= 1");
          }
        } catch (NumberFormatException e) {
          throw new ClaimBadRequestException("size must be an integer >= 1");
        }
      }
    }

    // Validate resolved pageable (covers concrete pageable instances)
    if (pageable != null
        && !pageable.isUnpaged()
        && (pageable.getPageNumber() < 0 || pageable.getPageSize() < 1)) {
      throw new ClaimBadRequestException("page must be >= 0 and size must be >= 1");
    }
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
