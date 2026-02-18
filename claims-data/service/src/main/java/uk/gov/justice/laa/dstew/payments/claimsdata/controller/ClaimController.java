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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.ClaimsApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.ClaimSearchRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSetV2;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateClaim201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimService;
import uk.gov.laa.springboot.sqlscanner.ScanForSql;

/** Controller for handling claims requests. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ClaimController implements ClaimsApi {
  private final ClaimService claimService;

  @Override
  @RateLimiter(name = "claimRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<CreateClaim201Response> createClaim(
      UUID submissionId, @ScanForSql ClaimPost claimPost) {
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
  public ResponseEntity<ClaimResultSetV2> getClaimsV2(
      String officeCode,
      String submissionId,
      List<SubmissionStatus> submissionStatuses,
      AreaOfLaw areaOfLaw,
      String feeCode,
      String uniqueFileNumber,
      String uniqueClientNumber,
      String uniqueCaseId,
      List<ClaimStatus> claimStatuses,
      String submissionPeriod,
      String caseReferenceNumber,
      Boolean escapedCaseFlag,
      Pageable pageable) {

    return ResponseEntity.ok(
        claimService.getClaimResultSetV2(
            ClaimSearchRequest.builder()
                .officeCode(officeCode)
                .submissionId(submissionId)
                .submissionStatuses(submissionStatuses)
                .areaOfLaw(areaOfLaw)
                .feeCode(feeCode)
                .uniqueFileNumber(uniqueFileNumber)
                .uniqueClientNumber(uniqueClientNumber)
                .uniqueCaseId(uniqueCaseId)
                .claimStatuses(claimStatuses)
                .submissionPeriod(submissionPeriod)
                .caseReferenceNumber(caseReferenceNumber)
                .escapedCaseFlag(escapedCaseFlag)
                .build(),
            pageable));
  }

  @Override
  @RateLimiter(name = "claimRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<Void> updateClaim(
      UUID submissionId,
      UUID claimId,
      @ScanForSql(ignoreClasses = ValidationMessagePatch.class) ClaimPatch claimPatch) {
    claimService.updateClaim(submissionId, claimId, claimPatch);
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<String> genericFallback(RequestNotPermitted e) {
    return get429Response();
  }
}
