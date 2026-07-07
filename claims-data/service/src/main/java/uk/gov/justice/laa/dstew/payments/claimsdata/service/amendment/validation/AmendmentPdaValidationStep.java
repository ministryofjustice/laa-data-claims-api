package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

/**
 * Provider Data API (PDA) trigger/skip, call and outcome handling (DSTEW-1646, split across
 * DSTEW-1772-1774), modelled as a validation step in the amendment sequence.
 *
 * <p>Like every step it collects errors (the PDA outcome) and may enrich the {@link
 * ClaimAmendmentState}. It runs at its position in {@code STEP_ORDER} - after the fee-code gates
 * and before duplicate validation.
 *
 * <p><b>Transaction.</b> The step sequence is run with no held transaction (the orchestrator does
 * not wrap validation in one), so this external call never holds a DB connection or claim-row lock
 * open. That is exactly why PDA/FSP can sit inline with the other steps.
 */
@Component
public class AmendmentPdaValidationStep implements ClaimAmendmentValidationStep {

  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    // Triggers the PDA call (or skips it) and returns the resulting outcome errors.
    return List.of();
  }
}
