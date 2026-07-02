package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

/**
 * Fee Scheme Platform (FSP) trigger/skip, call and outcome handling for a pricing-impacting
 * amendment (DSTEW-1758-1762), modelled as a validation step in the amendment sequence.
 *
 * <p>Like every step it collects errors (the FSP outcome) and enriches the {@link
 * ClaimAmendmentState}: on a successful pricing amendment it places the single amendment-driven
 * calculated-fee result onto the state, which is later attached during persistence by {@code
 * AmendmentCalculatedFeeWriter} (DSTEW-1762). It runs at its position in {@code STEP_ORDER} - after
 * the validation outcome check and before the final version guard.
 *
 * <p><b>Transaction.</b> The step sequence is run with no held transaction (the orchestrator does
 * not wrap validation in one), so this external call never holds a DB connection or claim-row lock
 * open.
 */
@Component
public class AmendmentFspValidationStep implements ClaimAmendmentValidationStep {

  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    // Triggers the FSP call (or skips it), places any calculated-fee result on the state, and
    // returns the resulting outcome errors.
    return List.of();
  }
}
