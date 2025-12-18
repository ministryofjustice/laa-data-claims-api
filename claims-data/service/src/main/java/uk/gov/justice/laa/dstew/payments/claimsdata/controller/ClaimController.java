package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static uk.gov.justice.laa.dstew.payments.claimsdata.util.RateLimitUtils.get429Response;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.ClaimsApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateClaim201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimService;

/** Controller for handling claims requests. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ClaimController implements ClaimsApi {
  private final ClaimService claimService;

  @Override
  @RateLimiter(name = "claimRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<CreateClaim201Response> createClaim(
      UUID submissionId, ClaimPost claimPost) {
    UUID claimId = claimService.createClaim(submissionId, claimPost);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{claimId}")
            .buildAndExpand(claimId)
            .toUri();
    return ResponseEntity.created(location).body(new CreateClaim201Response().id(claimId));
  }

  @Override
  @RateLimiter(name = "claimRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<ClaimResponse> getClaim(UUID submissionId, UUID claimId) {
    ClaimResponse claimResponse = claimService.getClaim(submissionId, claimId);
    return ResponseEntity.ok(claimResponse);
  }

  @Override
  @RateLimiter(name = "claimRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<ClaimResultSet> getClaims(
      String officeCode,
      String submissionId,
      List<SubmissionStatus> submissionStatuses,
      String feeCode,
      String uniqueFileNumber,
      String uniqueClientNumber,
      String uniqueCaseId,
      List<ClaimStatus> claimStatuses,
      String submissionPeriod,
      String caseReferenceNumber,
      Pageable pageable) {
    return ResponseEntity.ok(
        claimService.getClaimResultSet(
            officeCode,
            submissionId,
            submissionStatuses,
            feeCode,
            uniqueFileNumber,
            uniqueClientNumber,
            uniqueCaseId,
            claimStatuses,
            submissionPeriod,
            caseReferenceNumber,
            pageable));
  }

  @Override
  @RateLimiter(name = "claimRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<Void> updateClaim(UUID submissionId, UUID claimId, ClaimPatch claimPatch) {
    claimService.updateClaim(submissionId, claimId, claimPatch);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/search")
  @RateLimiter(name = "claimRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<ClaimResultSet> searchClaims(
      @RequestParam(value = "office_code") String officeCode,
      @RequestParam(value = "submission_id", required = false) String submissionId,
      @RequestParam(value = "submission_status", required = false)
          List<SubmissionStatus> submissionStatuses,
      @RequestParam(value = "fee_code", required = false) String feeCode,
      @RequestParam(value = "unique_file_number", required = false) String uniqueFileNumber,
      @RequestParam(value = "unique_client_number", required = false) String uniqueClientNumber,
      @RequestParam(value = "unique_case_id", required = false) String uniqueCaseId,
      @RequestParam(value = "claim_status", required = false) List<ClaimStatus> claimStatuses,
      @RequestParam(value = "submission_period", required = false) String submissionPeriod,
      @RequestParam(value = "case_reference_number", required = false) String caseReferenceNumber,
      Pageable pageable) {

    return ResponseEntity.ok(
        claimService.searchClaims(
            officeCode,
            submissionId,
            submissionStatuses,
            feeCode,
            uniqueFileNumber,
            uniqueClientNumber,
            uniqueCaseId,
            claimStatuses,
            submissionPeriod,
            caseReferenceNumber,
            pageable));
  }

  private ResponseEntity<String> genericFallback(RequestNotPermitted e) {
    return get429Response();
  }
}
