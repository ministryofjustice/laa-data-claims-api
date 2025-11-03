package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static uk.gov.justice.laa.dstew.payments.claimsdata.util.RateLimitUtils.get429Response;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.BulkSubmissionsApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateBulkSubmission201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.BulkSubmissionService;
import uk.gov.justice.laa.dstew.payments.claimsdata.validator.BulkSubmissionFileValidator;

/**
 * Controller that handles submissions for bulk claims. This REST API controller provides an
 * endpoint to process bulk submission files in CSV or XML format, validate their structure, and
 * save the initial bulk submission data ready for parsing.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class BulkSubmissionController implements BulkSubmissionsApi {
  private final BulkSubmissionService bulkSubmissionService;
  private final BulkSubmissionFileValidator bulkSubmissionFileValidator;

  @Override
  @RateLimiter(name = "bulkSubmissionRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<CreateBulkSubmission201Response> createBulkSubmission(
      final String userId, final List<String> offices, final MultipartFile file) {
    // Validate file
    bulkSubmissionFileValidator.validate(file);

    // Submit bulk submission
    CreateBulkSubmission201Response bulkSubmissionResponse =
        bulkSubmissionService.submitBulkSubmissionFile(userId, file, offices);
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v0/submissions/{id}")
            .buildAndExpand(bulkSubmissionResponse.getSubmissionIds().getFirst())
            .toUri();

    // Return response entity
    return ResponseEntity.created(location).body(bulkSubmissionResponse);
  }

  @Override
  @RateLimiter(name = "bulkSubmissionRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<GetBulkSubmission200Response> getBulkSubmission(UUID id) {
    GetBulkSubmission200Response response = bulkSubmissionService.getBulkSubmission(id);
    return ResponseEntity.ok(response);
  }

  @Override
  @RateLimiter(name = "bulkSubmissionRateLimiter", fallbackMethod = "genericFallback")
  public ResponseEntity<Void> updateBulkSubmission(
      UUID id, BulkSubmissionPatch bulkSubmissionPatch) {
    bulkSubmissionService.updateBulkSubmission(id, bulkSubmissionPatch);
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<String> genericFallback(RequestNotPermitted e) {
    return get429Response();
  }
}
