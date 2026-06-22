package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

/**
 * A single in-memory validation step in the synchronous claim amendment flow.
 *
 * <p>Each step inspects the {@link ClaimAmendmentState} and returns the errors it found - an empty
 * list means the step passed. A step may return several errors and may mark an error fatal to stop
 * the flow. Steps are pure: no repositories, no persistence and no external (PDA/FSP) calls.
 *
 * <p>Steps are deliberately <b>not</b> collected into a configurable list. The {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentService} injects
 * each step it needs and calls them explicitly, in a hardcoded order, so it can interleave the
 * external calls and the atomic save between them - something a flat, in-memory list cannot do.
 * Adding a step therefore means writing a new implementation of this interface and invoking it at
 * the right point in the orchestrator.
 *
 * <p>This is a functional interface ({@link #validate} is its single abstract method), so tests can
 * supply lightweight step stubs as lambdas, e.g. {@code state -> List.of()} for a step that passes
 * or {@code state -> List.of(error)} for one that fails, without a full implementation class.
 */
@FunctionalInterface
public interface ClaimAmendmentValidationStep {

  /**
   * Validates the amendment state.
   *
   * @param state the in-memory amendment state
   * @return the errors found by this step; an empty list means the step passed
   */
  List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state);
}
