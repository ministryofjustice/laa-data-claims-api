package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static uk.gov.justice.laa.dstew.payments.claimsdata.util.RateLimitUtils.get429Response;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.SystemReferencesApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentRequestedByReferenceList;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.InquestDepartmentReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.AmendmentReferenceService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.InquestDepartmentReferenceService;

/** Controller exposing read-only governed amendment reference data lookups. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AmendmentReferenceController implements SystemReferencesApi {

  private final AmendmentReferenceService amendmentReferenceService;
  private final InquestDepartmentReferenceService inquestDepartmentReferenceService;

  @Override
  @RateLimiter(name = "referenceRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<AmendmentRequestedByReferenceList> getAmendmentRequestedByReferences() {
    return ResponseEntity.ok(amendmentReferenceService.getAmendmentRequestedByReferences());
  }

  @Override
  @RateLimiter(name = "referenceRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<List<InquestDepartmentReference>> getInquestDepartments() {
    return ResponseEntity.ok(inquestDepartmentReferenceService.getAll());
  }

  private ResponseEntity<String> genericFallback(RequestNotPermitted e) {
    return get429Response();
  }
}
