package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static uk.gov.justice.laa.dstew.payments.claimsdata.util.RateLimitUtils.get429Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.ClaimHistoryApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimHistoryEventMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryEvent;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryPage;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimHistoryService;

/** Controller exposing a claim's unified, chronological history timeline. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ClaimHistoryController implements ClaimHistoryApi {

  private final ClaimHistoryService claimHistoryService;
  private final ObjectMapper objectMapper;
  private final ClaimHistoryEventMapper eventMapper;

  @Override
  @RateLimiter(name = "claimRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<ClaimHistoryResultSet> getClaimHistory(UUID claimId, Pageable pageable) {

    ClaimHistoryPage page = claimHistoryService.getTimeline(claimId, pageable);

    List<ClaimHistoryEventRow> rows = page.getEvents() == null ? List.of() : page.getEvents();
    List<ClaimHistoryEvent> events = rows.stream().map(eventMapper::toModel).toList();

    long totalElements = page.getTotalElements();
    int pageSize = page.getPageSize();
    int pageNumber = page.getPageNumber();
    int totalPages = page.getTotalPages();

    ClaimHistoryResultSet result =
        ClaimHistoryResultSet.builder()
            .claimId(claimId)
            .events(events)
            .totalElements((int) totalElements)
            .totalPages(totalPages)
            .number(pageNumber)
            .size(pageSize)
            .build();

    return ResponseEntity.ok(result);
  }

  private ResponseEntity<String> genericFallback(RequestNotPermitted e) {
    return get429Response();
  }
}
