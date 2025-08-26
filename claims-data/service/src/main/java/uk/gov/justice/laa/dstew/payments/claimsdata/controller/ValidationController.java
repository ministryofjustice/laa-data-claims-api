package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.ValidationApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimValidationError;

/**
 * Controller for validation endpoints.
 */
@RestController
@Slf4j
public class ValidationController implements ValidationApi {

  @Override
  public ResponseEntity<List<ClaimValidationError>> getValidationErrors(UUID submissionId) {
    throw new UnsupportedOperationException("Not yet implemented: DSTEW-213");
  }
}
