package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

class ClaimVersionValidationStepTest {

  private ClaimVersionValidationStep step;

  @BeforeEach
  void setUp() {
    step = new ClaimVersionValidationStep();
  }

  @Test
  @DisplayName("validate returns empty list when expected and received versions match")
  void shouldReturnEmptyListWhenVersionsMatch() {
    // Arrange: Create a deeply stubbed mock so we don't have to instantiate the nested DTOs
    ClaimAmendmentState state = mock(ClaimAmendmentState.class, RETURNS_DEEP_STUBS);

    when(state.getBeforeState().getVersion()).thenReturn(5L);
    when(state.getRequestPayload().getVersion().get()).thenReturn(5L);

    // Act
    List<ClaimAmendmentValidationError> errors = step.validate(state);

    // Assert
    assertThat(errors).isEmpty();
  }

  @Test
  @DisplayName("validate returns INVALID_CLAIM_VERSION_CONFLICT when versions do not match")
  void shouldReturnErrorWhenVersionsMismatch() {
    // Arrange: Simulate a stale version in the request payload
    ClaimAmendmentState state = mock(ClaimAmendmentState.class, RETURNS_DEEP_STUBS);

    when(state.getBeforeState().getVersion()).thenReturn(5L); // Current DB version
    when(state.getRequestPayload().getVersion().get()).thenReturn(4L); // Stale request version

    // Act
    List<ClaimAmendmentValidationError> errors = step.validate(state);

    // Assert
    assertThat(errors).hasSize(1);

    // Extract the error to verify it returns the correct validation code
    ClaimAmendmentValidationError error = errors.get(0);
    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_CLAIM_VERSION_CONFLICT);
  }

  @Test
  @DisplayName("validate returns INVALID_NULL_VERSION when submitted version is null")
  void shouldReturnErrorWhenSubmittedVersionIsNull() {
    // Arrange: Mock the state with a valid current DB version but a null submitted version
    ClaimAmendmentState state = mock(ClaimAmendmentState.class, RETURNS_DEEP_STUBS);

    when(state.getBeforeState().getVersion()).thenReturn(5L); // Current DB version
    when(state.getRequestPayload().getVersion().get()).thenReturn(null); // Missing request version

    // Act
    List<ClaimAmendmentValidationError> errors = step.validate(state);

    // Assert
    assertThat(errors).hasSize(1);

    // Verify it returns the correct validation code for null inputs
    ClaimAmendmentValidationError error = errors.get(0);
    assertThat(error.getCode()).isEqualTo(ClaimAmendmentValidationCode.INVALID_NULL_VERSION);
  }
}
