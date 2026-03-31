package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static uk.gov.justice.laa.dstew.payments.claimsdata.util.RateLimitUtils.get429Response;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.ClaimAuditEvent;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimAuditService;

/** Controller exposing audit endpoints for claims using Javers. */
@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
@Slf4j
public class ClaimAuditController {

  private final ClaimAuditService claimAuditService;

  /** Get the Javers audit events for a given claim id, ordered by date ascending. */
  @GetMapping("/{claimId}/audit")
  @RateLimiter(name = "claimRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<List<ClaimAuditEvent>> getClaimAudit(
      @PathVariable("claimId") String claimId) {
    log.debug("Requesting audit for claim id: {}", claimId);
    List<ClaimAuditEvent> events = claimAuditService.getAuditEventsForClaim(claimId);
    return ResponseEntity.ok(events);
  }

  private ResponseEntity<String> genericFallback(RequestNotPermitted e) {
    return get429Response();
  }
}
