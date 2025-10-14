package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.MatterStartsApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateMatterStart201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStarterResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.MatterStartService;

/** Controller for handling matter starts requests. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class MatterStartsController implements MatterStartsApi {

  private final MatterStartService matterStartService;

  @Override
  public ResponseEntity<CreateMatterStart201Response> createMatterStart(
      UUID id, MatterStartPost matterStartsPost) {
    UUID matterStartId = matterStartService.createMatterStart(id, matterStartsPost);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{matterStartId}")
            .buildAndExpand(matterStartId)
            .toUri();
    return ResponseEntity.created(location)
        .body(new CreateMatterStart201Response().id(matterStartId));
  }

  @Override
  public ResponseEntity<MatterStartGet> getMatterStart(UUID submissionId, UUID matterStartId) {
    return matterStartService
        .getMatterStart(submissionId, matterStartId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<MatterStarterResultSet> getAllMatterStartsForSubmission(UUID id) {
    return ResponseEntity.ok(matterStartService.getAllMatterStartsForSubmission(id));
  }
}
