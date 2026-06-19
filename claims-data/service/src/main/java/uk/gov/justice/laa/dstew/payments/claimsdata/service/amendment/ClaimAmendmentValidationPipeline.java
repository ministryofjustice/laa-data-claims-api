package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

/**
 * Runs the ordered {@link ClaimAmendmentValidationStep}s for the synchronous claim amendment flow,
 * collecting every error found.
 *
 * <p>Each step inspects the in-memory {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState} and returns the
 * errors it found (an empty list means it passed). The pipeline accumulates non-fatal errors and
 * keeps running so the caller can be shown every failure at once; it stops early only when a step
 * returns a {@linkplain ClaimAmendmentValidationError#isFatal() fatal} error, since no later step
 * (and no downstream PDA/FSP call) should run past a show-stopper. The collected errors are
 * returned to the caller; an empty list means every step passed and the amendment may proceed.
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
   * Runs each step in order, collecting all errors and stopping early on the first fatal error.
   *
   * @param state the in-memory amendment state produced by retrieval (DSTEW-1763)
   * @return every error found; an empty list means all steps passed and the amendment may proceed
   */
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    List<ClaimAmendmentValidationError> collected = new ArrayList<>();
    for (ClaimAmendmentValidationStep step : steps) {
      List<ClaimAmendmentValidationError> errors = step.validate(state);
      collected.addAll(errors);
      if (errors.stream().anyMatch(ClaimAmendmentValidationError::isFatal)) {
        log.debug("Claim amendment validation stopped at fatal step '{}'", step.name());
        break;
      }
    }
    return List.copyOf(collected);
  }
}
