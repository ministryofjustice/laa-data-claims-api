package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimStatusValidationStep;

/**
 * Runs the synchronous claim amendment flow as an ordered list of validation steps executed in
 * sequence. Each step inspects the amendment state and returns the errors it found; the
 * orchestrator collects them and stops as soon as a fatal error is seen.
 *
 * <p>{@link #STEP_ORDER} is the single source of truth for the sequence. Spring discovers every
 * {@link ClaimAmendmentValidationStep} bean and this service sorts them into that order at
 * construction. Each declared step must have a corresponding bean (startup fails fast otherwise);
 * any extra discovered steps that are not declared are ignored. Some steps additionally make an
 * external (PDA or FSP) call, but functionally they are ordinary validation steps that collect
 * errors, so they sit in the same list.
 *
 * <p>The full canonical sequence (steps are added as their tickets land) is:
 *
 * <ol>
 *   <li>DSTEW-1751/1752 claim-version contract
 *   <li>DSTEW-1764 claim-status eligibility
 *   <li>DSTEW-1765 metadata validation
 *   <li>DSTEW-1766 changed-field classification
 *   <li>DSTEW-1767 amendability / assessed-claim
 *   <li>DSTEW-1768 fee-code lookup and gates
 *   <li>DSTEW-176x PDA call (external)
 *   <li>DSTEW-1769 duplicate validation
 *   <li>DSTEW-1770 validation outcome check
 *   <li>DSTEW-176x FSP trigger/call (external)
 *   <li>DSTEW-1753/1754 final version guard
 * </ol>
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

  /** Canonical amendment validation order; add each step here, in position, as it is built. */
  static final List<Class<? extends ClaimAmendmentValidationStep>> STEP_ORDER =
      List.of(ClaimStatusValidationStep.class);

  private final List<ClaimAmendmentValidationStep> validationSteps;

  /**
   * Sorts the discovered validation step beans into {@link #STEP_ORDER}.
   *
   * @param discoveredSteps every validation step bean, in arbitrary (Spring-determined) order
   */
  @Autowired
  public ClaimAmendmentService(List<ClaimAmendmentValidationStep> discoveredSteps) {
    this.validationSteps = ordered(discoveredSteps);
  }

  /**
   * Holds an already-ordered sequence of steps directly, for tests that exercise the orchestration
   * loop without going through {@link #STEP_ORDER}.
   *
   * @param orderedSteps the validation steps, in the order they should run
   */
  ClaimAmendmentService(ClaimAmendmentValidationStep... orderedSteps) {
    this.validationSteps = List.of(orderedSteps);
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
   * Picks each step declared in {@link #STEP_ORDER}, in that order, from the discovered beans.
   * Every declared step must have a matching bean; any extra discovered steps are ignored.
   *
   * @param discoveredSteps every validation step bean, in arbitrary order
   * @return the declared steps in canonical order
   * @throws IllegalStateException if a declared step has no matching bean
   */
  private static List<ClaimAmendmentValidationStep> ordered(
      List<ClaimAmendmentValidationStep> discoveredSteps) {
    List<ClaimAmendmentValidationStep> orderedSteps = new ArrayList<>();
    for (Class<? extends ClaimAmendmentValidationStep> stepClass : STEP_ORDER) {
      orderedSteps.add(
          discoveredSteps.stream()
              .filter(step -> step.getClass().equals(stepClass))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "No bean found for declared amendment validation step "
                              + stepClass.getName()
                              + " (declared in ClaimAmendmentService.STEP_ORDER).")));
    }
    return List.copyOf(orderedSteps);
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
