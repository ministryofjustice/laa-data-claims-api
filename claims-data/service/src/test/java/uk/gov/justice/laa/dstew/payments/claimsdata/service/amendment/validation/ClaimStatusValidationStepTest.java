package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Tests for {@link ClaimStatusValidationStep}.
 *
 * <p>The step is a pure function over the before-state claim status with no repositories or
 * clients, so "no PDA/FSP call and no persistence on rejection" holds by construction. These tests
 * pin the eligible/ineligible outcomes and error codes.
 */
@DisplayName("ClaimStatusValidationStep Tests")
class ClaimStatusValidationStepTest {

  private final ClaimStatusValidationStep step = new ClaimStatusValidationStep();

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
  @DisplayName("Voided claim is rejected with a fatal INVALID_VOIDED_CLAIM_NOT_AMENDABLE")
  void voidedClaimRejected() {
    List<ClaimAmendmentValidationError> result = step.validate(stateWithStatus(ClaimStatus.VOID));

    assertThat(result).hasSize(1);
    ClaimAmendmentValidationError error = result.getFirst();
    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE.toString());
    assertThat(error.getMessage())
        .isEqualTo(
            ClaimAmendmentValidationCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE.getMessageTemplate());
    assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(error.getSeverity()).isEqualTo(ValidationSeverity.FATAL);
    assertThat(error.isFatal()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ClaimStatus.class,
      names = {"READY_TO_PROCESS", "INVALID"})
  @DisplayName("Other non-VALID statuses are rejected fatally as not amendable")
  void otherNonValidStatusRejected(ClaimStatus status) {
    List<ClaimAmendmentValidationError> result = step.validate(stateWithStatus(status));

    assertThat(result).hasSize(1);
    ClaimAmendmentValidationError error = result.getFirst();
    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_CLAIM_STATE_NOT_AMENDABLE.toString());
    assertThat(error.getMessage()).contains(String.valueOf(status));
    assertThat(error.isFatal()).isTrue();
  }
}
