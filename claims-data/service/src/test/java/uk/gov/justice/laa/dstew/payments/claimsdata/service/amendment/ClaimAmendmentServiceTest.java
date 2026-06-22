package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.EligibilityValidationStep;

/**
 * Tests for {@link ClaimAmendmentService}.
 *
 * <p>Exercises the orchestration mechanics with the validation steps mocked: the orchestrator
 * delegates to each step, returns the collected errors, and short-circuits on a fatal error before
 * any external call or save. Each step rule is covered by that step's own test (e.g. the test for
 * {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.EligibilityValidationStep}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimAmendmentService Tests")
class ClaimAmendmentServiceTest {

  @Mock private EligibilityValidationStep eligibilityValidationStep;

  @InjectMocks private ClaimAmendmentService orchestrator;

  private static ClaimAmendmentState anyState() {
    return ClaimAmendmentState.builder().beforeState(ClaimStateSnapshot.builder().build()).build();
  }

  @Test
  @DisplayName("returns no errors when every step passes")
  void passesWhenAllStepsPass() {
    when(eligibilityValidationStep.validate(any())).thenReturn(List.of());

    assertThat(orchestrator.orchestrate(anyState())).isEmpty();
  }

  @Test
  @DisplayName("returns the fatal error and stops when a step rejects")
  void shortCircuitsOnFatalError() {
    ClaimAmendmentValidationError fatal =
        new ClaimAmendmentValidationError(
            ClaimAmendmentErrorCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE,
            ClaimStatus.VOID,
            "rejected",
            true);
    when(eligibilityValidationStep.validate(any())).thenReturn(List.of(fatal));

    assertThat(orchestrator.orchestrate(anyState())).containsExactly(fatal);
  }
}
