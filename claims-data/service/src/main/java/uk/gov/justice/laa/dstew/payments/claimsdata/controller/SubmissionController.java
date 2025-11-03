package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static uk.gov.justice.laa.dstew.payments.claimsdata.util.RateLimitUtils.get429Response;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionsResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.SubmissionService;

/** Controller for handling submissions requests. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class SubmissionController implements SubmissionsApi {
  private final SubmissionService submissionService;

  @Override
  @RateLimiter(name = "submissionRateLimiter", fallbackMethod = "createSubmissionFallback")
  public ResponseEntity<CreateSubmission201Response> createSubmission(
      SubmissionPost submissionPost) {
    UUID id = submissionService.createSubmission(submissionPost);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();

    return ResponseEntity.created(location).body(new CreateSubmission201Response().id(id));
  }

  private ResponseEntity<?> createSubmissionFallback(
      SubmissionPost submissionPost, RequestNotPermitted e) {
    return get429Response();
  }

  @Override
  @RateLimiter(name = "submissionRateLimiter", fallbackMethod = "getSubmissionFallback")
  public ResponseEntity<SubmissionResponse> getSubmission(UUID id) {
    SubmissionResponse response = submissionService.getSubmission(id);
    return ResponseEntity.ok(response);
  }

  private ResponseEntity<?> getSubmissionFallback(UUID id, RequestNotPermitted e) {
    return get429Response();
  }

  @Override
  @RateLimiter(name = "submissionRateLimiter", fallbackMethod = "updateSubmissionFallback")
  public ResponseEntity<Void> updateSubmission(UUID id, SubmissionPatch submissionPatch) {
    submissionService.updateSubmission(id, submissionPatch);
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<?> updateSubmissionFallback(
      UUID id, SubmissionPatch submissionPatch, RequestNotPermitted e) {
    return get429Response();
  }

  @Override
  @RateLimiter(name = "submissionRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<SubmissionsResultSet> getSubmissions(
      List<String> offices,
      String submissionId,
      LocalDate submittedDateFrom,
      LocalDate submittedDateTo,
      String areaOfLaw,
      String submissionPeriod,
      List<SubmissionStatus> submissionStatuses,
      Pageable pageable) {
    return ResponseEntity.ok(
        submissionService.getSubmissionsResultSet(
            offices,
            submissionId,
            submittedDateFrom,
            submittedDateTo,
            areaOfLaw,
            submissionPeriod,
            submissionStatuses,
            pageable));
  }

  private ResponseEntity<?> getSubmissionsFallback(
      List<String> offices,
      String submissionId,
      LocalDate submittedDateFrom,
      LocalDate submittedDateTo,
      String areaOfLaw,
      String submissionPeriod,
      List<SubmissionStatus> submissionStatuses,
      Pageable pageable,
      RequestNotPermitted e) {
    return get429Response();
  }

  private ResponseEntity<String> genericFallback(RequestNotPermitted e) {
    return get429Response();
  }
}
