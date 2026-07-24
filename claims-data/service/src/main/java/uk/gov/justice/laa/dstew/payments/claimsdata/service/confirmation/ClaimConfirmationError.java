package uk.gov.justice.laa.dstew.payments.claimsdata.service.confirmation;

import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;

/** Claim-level validation messages that prevent a draft submission from being confirmed. */
public record ClaimConfirmationError(
    UUID claimId, List<ValidationMessagePatch> validationMessages) {

  public ClaimConfirmationError {
    validationMessages = validationMessages == null ? List.of() : List.copyOf(validationMessages);
  }
}
