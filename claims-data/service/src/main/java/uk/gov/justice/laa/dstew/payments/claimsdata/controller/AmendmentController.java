package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.AmendmentApi;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidatedClaimSummary;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.AmendmentService;

/** Controller for handling amendments requests. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AmendmentController implements AmendmentApi {
  private final AmendmentService amendmentService;

  @Override
  public ResponseEntity<ValidatedClaimSummary> getValidatedClaims(
      String officeCode,
      String uniqueFileNumber,
      String uniqueClientNumber,
      String uniqueCaseId,
      LocalDate submitted,
      Pageable pageable) {
    return ResponseEntity.ok(
        amendmentService.getValidClaimSet(
            officeCode, uniqueFileNumber, uniqueClientNumber, uniqueCaseId, submitted, pageable));
  }
}
