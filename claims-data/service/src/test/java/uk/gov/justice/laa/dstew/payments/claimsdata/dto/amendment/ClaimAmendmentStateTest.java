package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

@DisplayName("ClaimAmendmentState")
class ClaimAmendmentStateTest {

  private ValidationMessagePatch issue(ValidationMessageType type) {
    return new ValidationMessagePatch().type(type).source("TEST").displayMessage("test");
  }

  @Test
  @DisplayName("hasFatalValidationIssues is false when there are no issues")
  void noIssues() {
    ClaimAmendmentState state = ClaimAmendmentState.builder().build();

    assertThat(state.hasFatalValidationIssues()).isFalse();
  }

  @Test
  @DisplayName("hasFatalValidationIssues is false when only warnings are present")
  void onlyWarnings() {
    ClaimAmendmentState state = ClaimAmendmentState.builder().build();
    state.addValidationIssue(issue(ValidationMessageType.WARNING));

    assertThat(state.hasFatalValidationIssues()).isFalse();
  }

  @Test
  @DisplayName("hasFatalValidationIssues is true when at least one error is present")
  void hasError() {
    ClaimAmendmentState state = ClaimAmendmentState.builder().build();
    state.addValidationIssue(issue(ValidationMessageType.WARNING));
    state.addValidationIssue(issue(ValidationMessageType.ERROR));

    assertThat(state.hasFatalValidationIssues()).isTrue();
  }
}
