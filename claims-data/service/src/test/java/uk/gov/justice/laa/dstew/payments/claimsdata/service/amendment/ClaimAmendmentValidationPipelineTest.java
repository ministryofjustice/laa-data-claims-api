package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentEligibilityError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Tests for {@link ClaimAmendmentValidationPipeline}.
 *
 * <p>Proves the seam mechanics in isolation (no Spring) using fake steps: steps run in the
 * configured order, the first rejection short-circuits the remainder, a step's result is propagated
 * to the caller, and an empty pipeline passes. Individual step behaviour (e.g. eligibility) is
 * covered by each step's own test.
 */
@DisplayName("ClaimAmendmentValidationPipeline Tests")
class ClaimAmendmentValidationPipelineTest {

  private static ClaimAmendmentState anyState() {
    return ClaimAmendmentState.builder().beforeState(ClaimStateSnapshot.builder().build()).build();
  }

  @Test
  @DisplayName("runs every step in order when all pass")
  void runsAllStepsInOrderWhenAllPass() {
    List<String> invoked = new ArrayList<>();
    ClaimAmendmentValidationPipeline pipeline =
        new ClaimAmendmentValidationPipeline(
            List.of(
                new RecordingStep("a", invoked, null),
                new RecordingStep("b", invoked, null),
                new RecordingStep("c", invoked, null)));

    Optional<ClaimAmendmentEligibilityError> result = pipeline.validate(anyState());

    assertThat(result).isEmpty();
    assertThat(invoked).containsExactly("a", "b", "c");
  }

  @Test
  @DisplayName("short-circuits on the first rejection and skips later steps")
  void shortCircuitsOnFirstRejection() {
    List<String> invoked = new ArrayList<>();
    ClaimAmendmentEligibilityError rejection =
        new ClaimAmendmentEligibilityError(
            ClaimAmendmentErrorCode.INVALID_CLAIM_STATE_NOT_AMENDABLE,
            ClaimStatus.INVALID,
            "rejected by b");
    ClaimAmendmentValidationPipeline pipeline =
        new ClaimAmendmentValidationPipeline(
            List.of(
                new RecordingStep("a", invoked, null),
                new RecordingStep("b", invoked, rejection),
                new RecordingStep("c", invoked, null)));

    Optional<ClaimAmendmentEligibilityError> result = pipeline.validate(anyState());

    assertThat(result).containsSame(rejection);
    // "c" must not run once "b" rejects
    assertThat(invoked).containsExactly("a", "b");
  }

  @Test
  @DisplayName("an empty pipeline passes")
  void emptyPipelinePasses() {
    ClaimAmendmentValidationPipeline pipeline = new ClaimAmendmentValidationPipeline(List.of());

    assertThat(pipeline.validate(anyState())).isEmpty();
  }

  /** A step that records its invocation order and returns a preset result. */
  private static final class RecordingStep implements ClaimAmendmentValidationStep {
    private final String name;
    private final List<String> invoked;
    private final ClaimAmendmentEligibilityError result;

    RecordingStep(String name, List<String> invoked, ClaimAmendmentEligibilityError result) {
      this.name = name;
      this.invoked = invoked;
      this.result = result;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public Optional<ClaimAmendmentEligibilityError> validate(ClaimAmendmentState state) {
      invoked.add(name);
      return Optional.ofNullable(result);
    }
  }
}
