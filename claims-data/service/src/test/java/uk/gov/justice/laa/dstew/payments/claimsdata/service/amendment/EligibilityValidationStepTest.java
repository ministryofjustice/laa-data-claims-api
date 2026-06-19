package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Tests for {@link EligibilityValidationStep}.
 *
 * <p>The step is a pure function over the before-state claim status with no repositories or
 * clients, so "no PDA/FSP call and no persistence on rejection" holds by construction - there is
 * nothing for it to invoke. These tests pin the eligible/ineligible outcomes and error codes.
 */
@DisplayName("EligibilityValidationStep Tests")
class EligibilityValidationStepTest {

  private final EligibilityValidationStep step = new EligibilityValidationStep();

  private static ClaimAmendmentState stateWithStatus(ClaimStatus status) {
    return ClaimAmendmentState.builder()
        .beforeState(ClaimStateSnapshot.builder().status(status).build())
        .build();
  }

  @Test
  @DisplayName("VALID claim is eligible and can proceed")
  void validClaimIsEligible() {
    assertThat(step.validate(stateWithStatus(ClaimStatus.VALID))).isEmpty();
  }

  @Test
  @DisplayName("Voided claim is rejected with INVALID_VOIDED_CLAIM_NOT_AMENDABLE")
  void voidedClaimRejected() {
    List<ClaimAmendmentValidationError> result = step.validate(stateWithStatus(ClaimStatus.VOID));

    assertThat(result).hasSize(1);
    ClaimAmendmentValidationError error = result.getFirst();
    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentErrorCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE);
    assertThat(error.getClaimStatus()).isEqualTo(ClaimStatus.VOID);
    assertThat(error.getMessage()).isEqualTo(EligibilityValidationStep.VOIDED_CLAIM_MESSAGE);
    // eligibility is a hard gate: its errors are fatal and stop the pipeline
    assertThat(error.isFatal()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ClaimStatus.class,
      names = {"READY_TO_PROCESS", "INVALID"})
  @DisplayName("Other non-VALID statuses are rejected with INVALID_CLAIM_STATE_NOT_AMENDABLE")
  void otherNonValidStatusRejected(ClaimStatus status) {
    List<ClaimAmendmentValidationError> result = step.validate(stateWithStatus(status));

    assertThat(result).hasSize(1);
    ClaimAmendmentValidationError error = result.getFirst();
    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentErrorCode.INVALID_CLAIM_STATE_NOT_AMENDABLE);
    // the current status is carried through for the structured response
    assertThat(error.getClaimStatus()).isEqualTo(status);
    assertThat(error.getMessage()).contains(String.valueOf(status));
    assertThat(error.isFatal()).isTrue();
  }
}
