package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;

@DisplayName("BeforeStatePresenceValidationStep")
class BeforeStatePresenceValidationStepTest {

  private final BeforeStatePresenceValidationStep step = new BeforeStatePresenceValidationStep();

  @Test
  @DisplayName("absent before-state yields a fatal technical error")
  void absentBeforeStateYieldsFatalTechnicalError() {
    ClaimAmendmentState state = ClaimAmendmentState.builder().beforeState(null).build();

    List<ClaimAmendmentValidationError> errors = step.validate(state);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).getCode())
        .isEqualTo(ClaimAmendmentValidationCode.TECHNICAL_ERROR_MISSING_CLAIM_STATE);
    assertThat(errors.get(0).isFatal()).isTrue();
  }

  @Test
  @DisplayName("present before-state passes with no errors")
  void presentBeforeStatePasses() {
    ClaimAmendmentState state =
        ClaimAmendmentState.builder().beforeState(ClaimStateSnapshot.builder().build()).build();

    assertThat(step.validate(state)).isEmpty();
  }
}
