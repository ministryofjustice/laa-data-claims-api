package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentEligibilityError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;

/**
 * Runs the ordered {@link ClaimAmendmentValidationStep}s for the synchronous claim amendment flow,
 * short-circuiting on the first rejection.
 *
 * <p>Each step inspects the in-memory {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState} and either passes
 * ({@link java.util.Optional#empty()}) or returns a structured {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentEligibilityError}. The
 * pipeline stops at the first rejection and returns that error to the caller; if every step passes,
 * {@link java.util.Optional#empty()} is returned.
 *
 * <p>The pipeline is purely in-memory: it has no repositories and makes no external calls. PDA/FSP
 * integration, the transaction boundary and the atomic save are orchestrator-level concerns handled
 * outside this class. The steps themselves are assembled and ordered by {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimAmendmentOrchestrationConfig}.
 */
@Slf4j
public class ClaimAmendmentValidationPipeline {

  private final List<ClaimAmendmentValidationStep> steps;

  public ClaimAmendmentValidationPipeline(List<ClaimAmendmentValidationStep> steps) {
    this.steps = List.copyOf(steps);
  }

  /**
   * Runs each step in order until one rejects.
   *
   * @param state the in-memory amendment state produced by retrieval (DSTEW-1763)
   * @return {@link Optional#empty()} when every step passes; otherwise the first step's rejection
   */
  public Optional<ClaimAmendmentEligibilityError> validate(ClaimAmendmentState state) {
    for (ClaimAmendmentValidationStep step : steps) {
      Optional<ClaimAmendmentEligibilityError> rejection = step.validate(state);
      if (rejection.isPresent()) {
        log.debug("Claim amendment validation rejected at step '{}'", step.name());
        return rejection;
      }
    }
    return Optional.empty();
  }
}
