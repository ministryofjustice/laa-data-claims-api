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
  @DisplayName("Constructor automatically sorts errors by fatality first, then by HTTP status code descending")
  void shouldSortErrorsByFatalityThenHttpStatusDescending() {
    // Arrange: Create a mixed pool of fatal and non-fatal errors with various HTTP statuses

    // 1. Non-fatal errors
    ClaimAmendmentValidationError nonFatal400 = mock(ClaimAmendmentValidationError.class);
    when(nonFatal400.isFatal()).thenReturn(false);
    when(nonFatal400.getHttpStatus()).thenReturn(HttpStatus.BAD_REQUEST); // 400

    ClaimAmendmentValidationError nonFatal500 = mock(ClaimAmendmentValidationError.class);
    when(nonFatal500.isFatal()).thenReturn(false);
    when(nonFatal500.getHttpStatus()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR); // 500

    // 2. Fatal errors
    ClaimAmendmentValidationError fatal400 = mock(ClaimAmendmentValidationError.class);
    when(fatal400.isFatal()).thenReturn(true);
    when(fatal400.getHttpStatus()).thenReturn(HttpStatus.BAD_REQUEST); // 400

    ClaimAmendmentValidationError fatal409 = mock(ClaimAmendmentValidationError.class);
    when(fatal409.isFatal()).thenReturn(true);
    when(fatal409.getHttpStatus()).thenReturn(HttpStatus.CONFLICT); // 409

    ClaimAmendmentValidationError fatal500 = mock(ClaimAmendmentValidationError.class);
    when(fatal500.isFatal()).thenReturn(true);
    when(fatal500.getHttpStatus()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR); // 500

    // Supply them completely out of order to the constructor
    List<ClaimAmendmentValidationError> unsortedErrors = List.of(
        nonFatal400,
        fatal400,
        nonFatal500,
        fatal500,
        fatal409
    );

    // Act
    ClaimAmendmentValidationException exception = new ClaimAmendmentValidationException(unsortedErrors);

    // Assert: Verify strict priority ordering constraints are met
    List<ClaimAmendmentValidationError> sortedResult = exception.getErrors();

    assertThat(sortedResult).hasSize(5);

    // Priority 1: Fatal errors take precedence, sorted highest HTTP status code first (500 -> 409 -> 400)
    assertThat(sortedResult.get(0)).isSameAs(fatal500);
    assertThat(sortedResult.get(1)).isSameAs(fatal409);
    assertThat(sortedResult.get(2)).isSameAs(fatal400);

    // Priority 2: Non-fatal errors follow, sorted highest HTTP status code first (500 -> 400)
    assertThat(sortedResult.get(3)).isSameAs(nonFatal500);
    assertThat(sortedResult.get(4)).isSameAs(nonFatal400);
  }

  @Test
  @DisplayName("Constructor sets standard RuntimeException failure message with total error count")
  void shouldSetCorrectExceptionMessage() {
    // Arrange
    ClaimAmendmentValidationError error = mock(ClaimAmendmentValidationError.class);
    when(error.isFatal()).thenReturn(false);
    when(error.getHttpStatus()).thenReturn(HttpStatus.BAD_REQUEST);

    // Act
    ClaimAmendmentValidationException exception = new ClaimAmendmentValidationException(List.of(error, error));

    // Assert
    assertThat(exception.getMessage())
        .isEqualTo("Claim amendment validation failed with 2 errors");
  }
}