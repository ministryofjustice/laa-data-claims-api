package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.SubmissionsApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateSubmission201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetSubmission200Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionsResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.SubmissionService;

/** Controller for handling submissions requests. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class SubmissionController implements SubmissionsApi {
  private final SubmissionService submissionService;

  @Override
  public ResponseEntity<CreateSubmission201Response> createSubmission(
      SubmissionPost submissionPost) {
    UUID id = submissionService.createSubmission(submissionPost);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();

    return ResponseEntity.created(location).body(new CreateSubmission201Response().id(id));
  }

  @Override
  public ResponseEntity<GetSubmission200Response> getSubmission(UUID id) {
    GetSubmission200Response response = submissionService.getSubmission(id);
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<Void> updateSubmission(UUID id, SubmissionPatch submissionPatch) {
    submissionService.updateSubmission(id, submissionPatch);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<SubmissionsResultSet> getSubmissions(
      List<String> offices,
      String submissionId,
      LocalDate submittedDateFrom,
      LocalDate submittedDateTo,
      Pageable pageable) {

    return ResponseEntity.ok(
        submissionService.getSubmissionsResultSet(
            offices, submissionId, submittedDateFrom, submittedDateTo, pageable));
  }
}
