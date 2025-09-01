package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.ClaimsApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateClaim201Response;
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
  public ResponseEntity<ClaimFields> getClaim(UUID submissionId, UUID claimId) {
    ClaimFields fields = claimService.getClaim(submissionId, claimId);
    return ResponseEntity.ok(fields);
  }

  @Override
  public ResponseEntity<Void> updateClaim(UUID submissionId, UUID claimId, ClaimPatch claimPatch) {
    claimService.updateClaim(submissionId, claimId, claimPatch);
    return ResponseEntity.noContent().build();
  }
}
