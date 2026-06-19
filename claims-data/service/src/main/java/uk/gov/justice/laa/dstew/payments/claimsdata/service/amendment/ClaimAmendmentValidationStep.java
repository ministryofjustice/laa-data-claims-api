package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.List;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

/**
 * A single validation step in the synchronous claim amendment flow.
 *
 * <p>Each step inspects the in-memory amendment state and returns the errors it found - an empty
 * list means the step passed. A step may return more than one error. Steps are sequenced by {@link
 * ClaimAmendmentValidationPipeline}, which collects non-fatal errors and carries on but stops as
 * soon as a step returns a {@linkplain
 * uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError#isFatal()
 * fatal} error.
 *
 * <p>TODO: once every step in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimAmendmentOrchestrationConfig} is a real
 * implementation, remove the {@link #noop(String)} placeholder factory and make {@link
 * #validate(ClaimAmendmentState)} abstract (drop the default body) so every step must implement it
 * and none can silently pass by omission.
 */
public interface ClaimAmendmentValidationStep {

  /**
   * A human-readable name for the step (used for logging and to make the configured sequence
   * legible). Typically references the owning ticket.
   *
   * @return the step name
   */
  String name();

  /**
   * Validates the amendment state.
   *
   * @param state the in-memory amendment state
   * @return the errors found by this step; an empty list means the step passed
   */
  // TODO: make abstract (remove default body) once all real steps are implemented - see type
  // Javadoc
  default List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    return List.of();
  }

  /**
   * Convenience factory for a labelled no-op step. The returned step reports the provided name and
   * inherits the default no-op {@link #validate(ClaimAmendmentState)} behaviour.
   *
   * @param name label for this placeholder step
   * @return a {@link ClaimAmendmentValidationStep} that always passes
   */
  // TODO: remove this placeholder factory once all real steps are implemented - see type Javadoc
  static ClaimAmendmentValidationStep noop(String name) {
    return new ClaimAmendmentValidationStep() {
      @Override
      public String name() {
        return name;
      }
    };
  }
}
