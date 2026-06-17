package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.Optional;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentEligibilityError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;

/**
 * Adapts the real claim-eligibility gate (DSTEW-1764) to the {@link ClaimAmendmentValidationStep}
 * contract by delegating to {@link ClaimAmendmentEligibilityValidator} with the before-state claim
 * status.
 */
public class EligibilityValidationStep implements ClaimAmendmentValidationStep {

  private final ClaimAmendmentEligibilityValidator eligibilityValidator;

  public EligibilityValidationStep(ClaimAmendmentEligibilityValidator eligibilityValidator) {
    this.eligibilityValidator = eligibilityValidator;
  }

  @Override
  public String name() {
    return "DSTEW-1764 claim eligibility";
  }

  @Override
  public Optional<ClaimAmendmentEligibilityError> validate(ClaimAmendmentState state) {
    return eligibilityValidator.checkEligibility(state.getBeforeState().getStatus());
  }
}
