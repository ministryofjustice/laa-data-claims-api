package uk.gov.justice.laa.dstew.payments.claimsdata.service.confirmation;

import java.util.List;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionResponse;

/** A validation rule that is run only when a provider confirms a draft submission. */
public interface ClaimConfirmationValidator {

  List<ClaimConfirmationError> validate(SubmissionResponse submission);
}
