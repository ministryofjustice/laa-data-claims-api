package uk.gov.justice.laa.dstew.payments.claimsdata.service.confirmation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionResponse;

/** Runs the validators registered for the draft-confirmation transition. */
@Service
@RequiredArgsConstructor
public class ConfirmationValidationService {

  private final List<ClaimConfirmationValidator> validators;

  public List<ClaimConfirmationError> validate(SubmissionResponse submission) {
    return validators.stream()
        .flatMap(validator -> validator.validate(submission).stream())
        .toList();
  }
}
