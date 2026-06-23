package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentValidationSteps;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;

/**
 * Runs the synchronous claim amendment flow as an ordered list of validation steps executed in
 * sequence. Each step inspects the amendment state and returns the errors it found; the
 * orchestrator collects them and stops as soon as a fatal error is seen.
 *
 * <p>The steps are supplied already in order by {@code AmendmentValidationConfig} (the single
 * source of truth for the sequence, see {@code AmendmentValidationConfig.STEP_ORDER}) via the
 * {@link AmendmentValidationSteps} wrapper. Some steps additionally make an external (PDA or FSP)
 * call, but functionally they are ordinary validation steps that collect errors, so they sit in the
 * same list.
 *
 * <p>Error handling: a <b>fatal</b> error stops the flow immediately (no later step runs);
 * <b>non-fatal</b> errors are collected so the caller can be shown every failure at once. Any
 * non-empty error set means the amendment is not applied and nothing is saved.
 *
 * <p>Claim retrieval (building the {@link ClaimAmendmentState}) and not-found mapping remain the
 * caller's responsibility (see {@link ClaimAmendmentStateService}); this class operates on the
 * already-built before/after state.
 */
@Service
public class ClaimAmendmentService {

  private final List<ClaimAmendmentValidationStep> validationSteps;

  /**
   * Holds the amendment validation sequence, already ordered by {@code AmendmentValidationConfig}.
   *
   * @param validationSteps the validation steps, in canonical order
   */
  public ClaimAmendmentService(AmendmentValidationSteps validationSteps) {
    this.validationSteps = validationSteps.steps();
  }

  /**
   * Runs each validation step in order, stopping as soon as a fatal error is collected, and applies
   * the amendment only when every step passes.
   *
   * @param state the in-memory amendment state produced by retrieval (DSTEW-1763)
   * @return every validation error found; an empty list means validation passed and the amendment
   *     was applied
   */
  public List<ClaimAmendmentValidationError> orchestrate(ClaimAmendmentState state) {
    List<ClaimAmendmentValidationError> errors = new ArrayList<>();

    for (ClaimAmendmentValidationStep step : validationSteps) {
      errors.addAll(step.validate(state));
      if (containsFatal(errors)) {
        return errors;
      }
    }

    if (!errors.isEmpty()) {
      return errors;
    }

    // ----- atomic save: only when every step has passed -----
    // TODO(DSTEW-176x): persist the amendment atomically within the orchestrator transaction.
    return errors;
  }

  /**
   * Reports whether the collected errors include a fatal one, i.e. a show-stopper that must end the
   * flow immediately - no further step runs and nothing is saved.
   *
   * @param errors the running list of collected errors
   * @return {@code true} if any collected error is fatal
   */
  private static boolean containsFatal(List<ClaimAmendmentValidationError> errors) {
    return errors.stream().anyMatch(ClaimAmendmentValidationError::isFatal);
  }
}
