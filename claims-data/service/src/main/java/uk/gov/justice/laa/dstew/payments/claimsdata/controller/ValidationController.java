package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.ValidationMessagesApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagesResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ValidationMessageService;

/** Controller for handling validation errors. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ValidationController implements ValidationMessagesApi {

  private final ValidationMessageService validationMessageService;

  @Override
  public ResponseEntity<ValidationMessagesResponse> getValidationMessages(
      final UUID submissionId,
      final UUID claimId,
      final ValidationMessageType type,
      final String source,
      final Pageable pageable) {

    return ResponseEntity.ok(
        validationMessageService.getValidationErrors(
            submissionId, claimId, type, source, pageable));
  }
}
