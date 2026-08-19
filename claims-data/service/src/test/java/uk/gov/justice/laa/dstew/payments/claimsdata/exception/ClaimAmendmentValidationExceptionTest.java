package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

class ClaimAmendmentValidationExceptionTest {

  @Test
  @DisplayName("Constructor sets standard RuntimeException failure message with total error count")
  void shouldSetCorrectExceptionMessage() {
    // Arrange
    ClaimAmendmentValidationError error = mock(ClaimAmendmentValidationError.class);
    when(error.isFatal()).thenReturn(false);
    when(error.getHttpStatus()).thenReturn(HttpStatus.BAD_REQUEST);

    // Act
    ClaimAmendmentValidationException exception =
        new ClaimAmendmentValidationException(List.of(error, error));

    // Assert
    assertThat(exception.getMessage()).isEqualTo("Claim amendment validation failed with 2 errors");
  }
}
