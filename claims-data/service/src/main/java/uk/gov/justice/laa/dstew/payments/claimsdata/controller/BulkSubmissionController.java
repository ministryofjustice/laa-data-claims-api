package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.BulkSubmissionsApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateBulkSubmission201Response;
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
  public ResponseEntity<CreateBulkSubmission201Response> createBulkSubmission(
      String userId, MultipartFile file) {
    // Validate file
    bulkSubmissionFileValidator.validate(file);

    // Submit bulk submission
    CreateBulkSubmission201Response bulkSubmissionResponse =
        bulkSubmissionService.submitBulkSubmissionFile(userId, file);
    URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                       .path("/api/v0/submissions/{id}")
                       .buildAndExpand(bulkSubmissionResponse.getSubmissionIds().getFirst())
                       .toUri();

    // Return response entity
    return ResponseEntity.created(location).body(bulkSubmissionResponse);
  }

  @Override
  public ResponseEntity<Object> getBulkSubmission(UUID id) {
    return null;
  }
}
