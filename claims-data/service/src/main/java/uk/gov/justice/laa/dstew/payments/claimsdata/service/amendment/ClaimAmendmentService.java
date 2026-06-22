package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.EligibilityValidationStep;

/**
 * Runs the synchronous claim amendment flow as a single linear sequence: the in-memory validation
 * steps and the external (PDA/FSP) calls and atomic save are executed directly, in the agreed
 * order, by this one class.
 *
 * <p>The order is encoded by the body of {@link #orchestrate(ClaimAmendmentState)} rather than by a
 * configurable list of steps, because the external calls and the save have to be interleaved
 * between the validation steps and a flat, in-memory list cannot pause to perform I/O. Each
 * validation step is its own class implementing {@link ClaimAmendmentValidationStep}; the
 * orchestrator injects the steps it needs and calls them explicitly, in order, with the
 * external-call and save insertion points marked inline.
 *
 * <p>Error handling mirrors the agreed contract: a <b>fatal</b> error stops the flow immediately;
 * <b>non-fatal</b> errors are collected so the caller can be shown every failure at once. Any
 * non-empty error set means no PDA call, no FSP call and no save - the gates before each external
 * call and before the save enforce this.
 *
 * <p>Claim retrieval (building the {@link ClaimAmendmentState}) and not-found mapping remain the
 * caller's responsibility (see {@link ClaimAmendmentStateService}); this class operates on the
 * already-built before/after state.
 *
 * <p>Only the DSTEW-1764 {@link EligibilityValidationStep} is implemented today; the remaining
 * steps and the PDA/FSP/save operations are marked with {@code TODO}s at their place in the
 * sequence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimAmendmentService {

  private final EligibilityValidationStep eligibilityValidationStep;

  /**
   * Runs validation, the external calls and the save in order, stopping as soon as the flow cannot
   * proceed.
   *
   * @param state the in-memory amendment state produced by retrieval (DSTEW-1763)
   * @return every validation error found; an empty list means validation passed and the amendment
   *     was applied
   */
  public List<ClaimAmendmentValidationError> orchestrate(ClaimAmendmentState state) {
    List<ClaimAmendmentValidationError> errors = new ArrayList<>();

    // ----- in-memory validation: after retrieval, before any external call -----
    // TODO(DSTEW-1751/1752): claim-version contract and early gate.
    // DSTEW-1764: claim-status eligibility gate.
    if (shouldStopAfterCollecting(eligibilityValidationStep, state, errors)) {
      return errors;
    }
    // TODO(DSTEW-1765): metadata validation.
    // TODO(DSTEW-1766): changed-field classification.
    // TODO(DSTEW-1767): amendability and assessed-claim gates.
    // TODO(DSTEW-1768): fee-code lookup and fee-code-enriched gates.

    // ----- external: PDA call or skip (orchestrator-owned) -----
    // TODO(DSTEW-176x): call PDA (or skip) and fold the response into the state.

    // TODO(DSTEW-1769): duplicate validation.
    // TODO(DSTEW-1770): validation outcome check.

    // ----- external: FSP trigger / call / outcome (orchestrator-owned) -----
    // TODO(DSTEW-176x): trigger FSP, make the call and fold the outcome into the state.

    // TODO(DSTEW-1753/1754): final version guard and conflict response.

    // ----- atomic save: only when every check has passed -----
    // TODO(DSTEW-176x): persist the amendment atomically within the orchestrator transaction.
    return errors;
  }

  /**
   * Runs one validation step, appends its errors to {@code errors}, and reports whether the flow
   * must stop now because a fatal error has been collected.
   *
   * @param step the validation step to run
   * @param state the in-memory amendment state
   * @param errors the running error list, mutated in place by appending this step's errors
   * @return {@code true} if the collected errors now include a fatal one (caller should return)
   */
  private boolean shouldStopAfterCollecting(
      ClaimAmendmentValidationStep step,
      ClaimAmendmentState state,
      List<ClaimAmendmentValidationError> errors) {
    errors.addAll(step.validate(state));
    return containsFatalError(errors);
  }

  /**
   * Reports whether the collected errors include a fatal one, i.e. a show-stopper that must end the
   * flow immediately (no further steps, no external call, no save).
   *
   * @param errors the running list of collected errors
   * @return {@code true} if any collected error is fatal
   */
  private static boolean containsFatalError(List<ClaimAmendmentValidationError> errors) {
    return errors.stream().anyMatch(ClaimAmendmentValidationError::isFatal);
  }

  private List<ClaimAmendmentValidationError> stopBeforeExternalCalls(
      List<ClaimAmendmentValidationError> errors) {
    log.debug(
        "Claim amendment stopped with {} validation error(s); no external call or save run",
        errors.size());
    return errors;
  }
}
