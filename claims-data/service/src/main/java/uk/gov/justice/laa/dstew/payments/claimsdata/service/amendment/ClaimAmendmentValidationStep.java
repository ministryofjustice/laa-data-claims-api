package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.Optional;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentEligibilityError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;

/**
 * A single validation step in the synchronous claim amendment flow.
 *
 * <p>Each step inspects the in-memory amendment state and either lets the flow continue ({@link
 * Optional#empty()}) or rejects it with a structured error. Steps are sequenced by {@link
 * ClaimAmendmentValidationPipeline}.
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
   * @return {@link Optional#empty()} to continue, or a populated error to reject and short-circuit
   */
  default Optional<ClaimAmendmentEligibilityError> validate(ClaimAmendmentState state) {
    return Optional.empty();
  }

  /**
   * Convenience factory for a labelled no-op step. The returned step reports the provided name and
   * inherits the default no-op {@link #validate(ClaimAmendmentState)} behaviour.
   *
   * @param name label for this placeholder step
   * @return a {@link ClaimAmendmentValidationStep} that always passes
   */
  static ClaimAmendmentValidationStep noop(String name) {
    return new ClaimAmendmentValidationStep() {
      @Override
      public String name() {
        return name;
      }
    };
  }
}
