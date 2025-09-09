package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

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
  public ResponseEntity<ClaimResponse> getClaim(UUID submissionId, UUID claimId) {
    ClaimResponse claimResponse = claimService.getClaim(submissionId, claimId);
    return ResponseEntity.ok(claimResponse);
  }

  @Override
  public ResponseEntity<ClaimResultSet> getClaims(
      String officeCode,
      String submissionId,
      List<SubmissionStatus> submissionStatuses,
      String feeCode,
      String uniqueFileNumber,
      String uniqueClientNumber,
      List<ClaimStatus> claimStatuses,
      Pageable pageable) {
    // TODO: Implement search claims endpoint https://dsdmoj.atlassian.net/browse/DSTEW-350
    return ResponseEntity.ok(new ClaimResultSet());
  }

  @Override
  public ResponseEntity<Void> updateClaim(UUID submissionId, UUID claimId, ClaimPatch claimPatch) {
    claimService.updateClaim(submissionId, claimId, claimPatch);
    return ResponseEntity.noContent().build();
  }
}
