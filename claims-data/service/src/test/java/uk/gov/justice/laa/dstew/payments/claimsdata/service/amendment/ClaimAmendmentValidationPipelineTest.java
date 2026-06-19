package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Tests for {@link ClaimAmendmentValidationPipeline}.
 *
 * <p>Proves the seam mechanics in isolation (no Spring) using fake steps: steps run in the
 * configured order, non-fatal errors are collected from every step, a fatal error stops the
 * pipeline so later steps do not run, each step's errors are propagated to the caller, and an empty
 * pipeline passes. Individual step behaviour (e.g. eligibility) is covered by each step's own test.
 */
@DisplayName("ClaimAmendmentValidationPipeline Tests")
class ClaimAmendmentValidationPipelineTest {

  private static ClaimAmendmentState stateWithStatus(ClaimStatus status) {
    return ClaimAmendmentState.builder()
        .beforeState(ClaimStateSnapshot.builder().status(status).build())
        .build();
  }

  private static ClaimAmendmentValidationError error(String message, boolean fatal) {
    return new ClaimAmendmentValidationError(
        ClaimAmendmentErrorCode.INVALID_CLAIM_STATE_NOT_AMENDABLE,
        ClaimStatus.INVALID,
        message,
        fatal);
  }

  @Test
  @DisplayName("runs every step in order when all pass")
  void runsAllStepsInOrderWhenAllPass() {
    List<String> invoked = new ArrayList<>();
    ClaimAmendmentValidationPipeline pipeline =
        new ClaimAmendmentValidationPipeline(
            List.of(
                new RecordingStep("a", invoked, List.of()),
                new RecordingStep("b", invoked, List.of()),
                new RecordingStep("c", invoked, List.of())));

    List<ClaimAmendmentValidationError> result =
        pipeline.validate(stateWithStatus(ClaimStatus.VALID));

    assertThat(result).isEmpty();
    assertThat(invoked).containsExactly("a", "b", "c");
  }

  @Test
  @DisplayName("collects non-fatal errors from every step and still runs them all")
  void collectsNonFatalErrorsFromEveryStep() {
    List<String> invoked = new ArrayList<>();
    ClaimAmendmentValidationError errorA = error("rejected by a", false);
    ClaimAmendmentValidationError errorC = error("rejected by c", false);
    ClaimAmendmentValidationPipeline pipeline =
        new ClaimAmendmentValidationPipeline(
            List.of(
                new RecordingStep("a", invoked, List.of(errorA)),
                new RecordingStep("b", invoked, List.of()),
                new RecordingStep("c", invoked, List.of(errorC))));

    List<ClaimAmendmentValidationError> result =
        pipeline.validate(stateWithStatus(ClaimStatus.VALID));

    assertThat(result).containsExactly(errorA, errorC);
    // every step runs because none of the errors are fatal
    assertThat(invoked).containsExactly("a", "b", "c");
  }

  @Test
  @DisplayName("a fatal error stops the pipeline and skips later steps")
  void fatalErrorStopsPipeline() {
    List<String> invoked = new ArrayList<>();
    ClaimAmendmentValidationError nonFatal = error("collected from a", false);
    ClaimAmendmentValidationError fatal = error("fatal from b", true);
    ClaimAmendmentValidationPipeline pipeline =
        new ClaimAmendmentValidationPipeline(
            List.of(
                new RecordingStep("a", invoked, List.of(nonFatal)),
                new RecordingStep("b", invoked, List.of(fatal)),
                new RecordingStep("c", invoked, List.of())));

    List<ClaimAmendmentValidationError> result =
        pipeline.validate(stateWithStatus(ClaimStatus.VALID));

    // the error collected before the fatal one is retained, and the fatal one is included
    assertThat(result).containsExactly(nonFatal, fatal);
    // "c" must not run once "b" returns a fatal error
    assertThat(invoked).containsExactly("a", "b");
  }

  @Test
  @DisplayName("an empty pipeline passes")
  void emptyPipelinePasses() {
    ClaimAmendmentValidationPipeline pipeline = new ClaimAmendmentValidationPipeline(List.of());

    assertThat(pipeline.validate(stateWithStatus(ClaimStatus.VALID))).isEmpty();
  }

  @Test
  @DisplayName("a real eligibility rejection short-circuits before any downstream step runs")
  void eligibilityRejectionShortCircuitsBeforeDownstreamSteps() {
    List<String> invoked = new ArrayList<>();
    // The eligibility gate sits before the (not-yet-real) PDA/FSP steps. A spy step stands in for
    // anything downstream; it must never run once the gate rejects a non-VALID claim (AC4).
    ClaimAmendmentValidationStep downstream = new RecordingStep("downstream", invoked, List.of());
    ClaimAmendmentValidationPipeline pipeline =
        new ClaimAmendmentValidationPipeline(List.of(new EligibilityValidationStep(), downstream));

    List<ClaimAmendmentValidationError> result =
        pipeline.validate(stateWithStatus(ClaimStatus.VOID));

    assertThat(result)
        .singleElement()
        .extracting(ClaimAmendmentValidationError::getCode)
        .isEqualTo(ClaimAmendmentErrorCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE);
    assertThat(invoked).doesNotContain("downstream");
  }

  /** A step that records its invocation order and returns a preset list of errors. */
  private static final class RecordingStep implements ClaimAmendmentValidationStep {
    private final String name;
    private final List<String> invoked;
    private final List<ClaimAmendmentValidationError> result;

    RecordingStep(String name, List<String> invoked, List<ClaimAmendmentValidationError> result) {
      this.name = name;
      this.invoked = invoked;
      this.result = result;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
      invoked.add(name);
      return result;
    }
  }
}
