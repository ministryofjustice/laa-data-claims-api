package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.ValidationErrorsApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationErrorsResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ValidationErrorService;

/** Controller for handling validation errors. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ValidationController implements ValidationErrorsApi {

  private final ValidationErrorService validationErrorService;

  @Override
  public ResponseEntity<ValidationErrorsResponse> getValidationErrors(
      final UUID submissionId, final UUID claimId, final Pageable pageable) {

    return ResponseEntity.ok(
        validationErrorService.getValidationErrors(submissionId, claimId, pageable));
  }
}
