package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

/**
 * A single validation step in the synchronous claim amendment flow.
 *
 * <p>Each step inspects the {@link ClaimAmendmentState} and returns the errors it found - an empty
 * list means the step passed. A step may return several errors and may mark an error fatal to stop
 * the flow. Most steps are pure in-memory checks; some additionally make an external (PDA or FSP)
 * call, but functionally they are still just validation steps that collect errors.
 *
 * <p>The {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentValidationService}
 * runs the steps in sequence, stopping on the first fatal error. The sequence is defined centrally
 * in {@code ClaimAmendmentValidationService.STEP_ORDER}; adding a step means writing a new
 * {@code @Component} implementation of this interface and inserting it, at the right position, into
 * that order list.
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
