package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentChangeDetector;

/**
 * Guard that halts a no-op amendment - one whose payload changes nothing - before any work is done
 * or any {@code claim_amendment} row is written.
 *
 * <p>The change signal is {@link AmendmentChangeDetector#detectChanges(ClaimAmendmentState)}, which
 * compares the before-state against the post-amendment state and returns the provider-requested
 * changed fields. This step runs early - after the before-state is known but before the metadata,
 * PDA and FSP steps - so a genuinely empty change-set short-circuits the pipeline before any
 * external call is made. At this point no FSP recalculation has run, so {@code afterFee} is absent
 * and the detector reports the provider-requested changes only, which is exactly the "did the
 * provider actually request a change" question.
 *
 * <ul>
 *   <li>at least one changed field -&gt; the step passes and amendment processing continues;
 *   <li>no changed fields -&gt; the step fails with a fatal {@link
 *       ClaimAmendmentValidationCode#NO_AMENDMENT_CHANGES_SUBMITTED}, halting the flow so no later
 *       step runs and nothing is saved.
 * </ul>
 *
 * <p>Although a no-op is not a client mistake, it is modelled as a fatal step outcome so it stops
 * the pipeline through the same mechanism as every other stop condition. Its code resolves to a
 * <b>204 No Content</b> - the same success status a genuine amendment returns - so the caller sees
 * a consistent "accepted, nothing to do" response and no phantom history row is created.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AmendmentNoChangeValidationStep implements ClaimAmendmentValidationStep {

  private final AmendmentChangeDetector changeDetector;

  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    if (!changeDetector.detectChanges(state).isEmpty()) {
      return List.of();
    }

    log.debug("Amendment submitted no field changes (no-op); halting with a 204 outcome.");
    return List.of(
        ClaimAmendmentValidationError.of(
            ClaimAmendmentValidationCode.NO_AMENDMENT_CHANGES_SUBMITTED));
  }
}
