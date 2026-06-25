package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * In-memory aggregate describing a claim amendment in progress, passed from the retrieval/build
 * step to downstream validation, history and persistence tasks.
 *
 * <p>It bundles the three pieces the amendment flow needs:
 *
 * <ul>
 *   <li>{@link #beforeState} - the current stored values (basis for the {@code beforeState} JSONB);
 *   <li>{@link #requestPayload} - the sparse, presence-aware submission (basis for the {@code
 *       requestPayload} JSONB);
 *   <li>{@link #postAmendmentState} - the proposed amended values, built by applying the sparse
 *       payload onto the before-state (omitted fields retain stored values; explicit nulls are
 *       retained as requested clears).
 * </ul>
 *
 * <p>The before/after snapshots plus the payload's field presence are sufficient to compute the
 * {@code diff} JSONB.
 *
 * <p>It also carries the running {@link #errors} collected as the validation steps run, so the
 * accumulating result travels with the state rather than being threaded separately through the
 * orchestrator.
 */
@Data
@Builder
public class ClaimAmendmentState {

  private ClaimStateSnapshot beforeState;

  private ClaimAmendmentPayload requestPayload;

  private ClaimStateSnapshot postAmendmentState;

  /**
   * The validation errors collected so far as the amendment validation steps run.
   */
  @Builder.Default private final List<ClaimAmendmentValidationError> errors = new ArrayList<>();

  /**
   * Adds the errors a validation step found to the running collection.
   *
   * @param newErrors the errors returned by a step; may be empty
   */
  public void addErrors(List<ClaimAmendmentValidationError> newErrors) {
    errors.addAll(newErrors);
  }

  /**
   * Reports whether the collected errors include a fatal one, i.e. a show-stopper that must end the
   * flow immediately - no further step runs and nothing is saved.
   *
   * @return {@code true} if any collected error is fatal
   */
  public boolean containsFatal() {
    return errors.stream().anyMatch(ClaimAmendmentValidationError::isFatal);
  }
}
