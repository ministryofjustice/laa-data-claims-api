package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.AssessmentsApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateAssessment201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.AssessmentService;

/** Controller for handling assessments requests. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AssessmentController implements AssessmentsApi {

  private final AssessmentService assessmentService;

  @Override
  public ResponseEntity<CreateAssessment201Response> createAssessment(
      UUID claimId, AssessmentPost assessmentPost) {
    UUID assessmentId = assessmentService.createAssessment(claimId, assessmentPost);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{assessmentId}")
            .buildAndExpand(assessmentId)
            .toUri();

    CreateAssessment201Response response =
        CreateAssessment201Response.builder().id(assessmentId).build();

    return ResponseEntity.created(location).body(response);
  }
}
