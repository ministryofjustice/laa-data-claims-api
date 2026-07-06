package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.laa.springboot.export.ExportValidationException;

class DataClaimsExceptionHandlerTest {
  private static final String TEST_REQUEST_URI = "/api/v1/test";

  DataClaimsExceptionHandler dataClaimsExceptionHandler = new DataClaimsExceptionHandler();
  HttpServletRequest mockRequest;

  @BeforeEach
  void setUp() {
    mockRequest = mock(HttpServletRequest.class);
    when(mockRequest.getRequestURI()).thenReturn(TEST_REQUEST_URI);
  }

  @Test
  void handleGenericException_returnsProblemDetailWithInternalServerErrorStatus() {
    ResponseEntity<ProblemDetail> result =
        dataClaimsExceptionHandler.handleGenericException(
            new RuntimeException("Something went wrong"), mockRequest);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getStatus()).isEqualTo(INTERNAL_SERVER_ERROR.value());
    assertThat(result.getBody().getTitle()).isEqualTo("Internal Server Error");
    assertThat(result.getBody().getDetail())
        .isEqualTo("An unexpected application error has occurred.");
    assertThat(result.getBody().getType().toString())
        .isEqualTo("https://claimsdata.payments.laa.justice.gov.uk/errors/runtime");
    assertThat(result.getBody().getInstance().toString()).isEqualTo(TEST_REQUEST_URI);
    // Verify backward compatibility property
    assertThat(result.getBody().getProperties())
        .containsEntry("message", "An unexpected application error has occurred.");
  }

  @ParameterizedTest(name = "{0} returns {1} status")
  @MethodSource("claimsDataExceptionTestCases")
  void handleClaimsDataException_returnsProblemDetailWithCorrectStatus(
      ClaimsDataException exception, HttpStatus expectedStatus, String expectedTypeFragment) {

    ResponseEntity<ProblemDetail> result =
        dataClaimsExceptionHandler.handleClaimsDataException(exception, mockRequest);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(expectedStatus);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getStatus()).isEqualTo(expectedStatus.value());
    assertThat(result.getBody().getTitle()).isEqualTo(expectedStatus.getReasonPhrase());
    assertThat(result.getBody().getDetail()).isEqualTo(exception.getMessage());
    assertThat(result.getBody().getType().toString()).contains(expectedTypeFragment);
    assertThat(result.getBody().getInstance().toString()).isEqualTo(TEST_REQUEST_URI);
    assertThat(result.getBody().getProperties()).containsEntry("message", exception.getMessage());
  }

  private static Stream<Arguments> claimsDataExceptionTestCases() {
    return Stream.of(
        Arguments.of(
            new BulkSubmissionValidationException("Validation failed for field X"),
            BAD_REQUEST,
            "bulk-submission-validation"),
        Arguments.of(
            new ClaimsDataException("Resource not found", NOT_FOUND), NOT_FOUND, "claims-data"),
        Arguments.of(
            new ClaimsDataException("Server error occurred", INTERNAL_SERVER_ERROR),
            INTERNAL_SERVER_ERROR,
            "claims-data"),
        Arguments.of(
            new ClaimsDataException("Unauthorized access", HttpStatus.UNAUTHORIZED),
            HttpStatus.UNAUTHORIZED,
            "claims-data"),
        Arguments.of(
            new ClaimsDataException("Forbidden resource", HttpStatus.FORBIDDEN),
            HttpStatus.FORBIDDEN,
            "claims-data"),
        // Test fallback to INTERNAL_SERVER_ERROR when HttpStatus.resolve() returns null
        Arguments.of(
            new ClaimsDataException("Unknown status code", HttpStatusCode.valueOf(999)),
            INTERNAL_SERVER_ERROR,
            "claims-data"));
  }

  @Test
  void handleExportValidationException_returnsBadRequestWithMessage() {
    ExportValidationException ex =
        new ExportValidationException("Filter submissionId must be a UUID");

    ResponseEntity<ProblemDetail> result =
        dataClaimsExceptionHandler.handleExportValidationException(ex, mockRequest);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(BAD_REQUEST);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getStatus()).isEqualTo(BAD_REQUEST.value());
    assertThat(result.getBody().getTitle()).isEqualTo("Bad Request");
    assertThat(result.getBody().getDetail()).isEqualTo("Filter submissionId must be a UUID");
    assertThat(result.getBody().getType().toString()).contains("export-validation");
    assertThat(result.getBody().getInstance().toString()).isEqualTo(TEST_REQUEST_URI);
    // Verify backward compatibility property
    assertThat(result.getBody().getProperties())
        .containsEntry("message", "Filter submissionId must be a UUID");
  }

  @Test
  void handleClaimAmendmentValidationException_returnsBadRequestWithErrorsPropertyWhenNonFatal() {
    // Arrange: Create a non-fatal validation error scenario
    ClaimAmendmentValidationError nonFatalError =
        ClaimAmendmentValidationError.of(
            ClaimAmendmentValidationCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE); // isFatal = false
    ClaimAmendmentValidationException ex =
        new ClaimAmendmentValidationException(List.of(nonFatalError));

    // Act
    ResponseEntity<ProblemDetail> result =
        dataClaimsExceptionHandler.handleClaimAmendmentValidationException(ex, mockRequest);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(BAD_REQUEST);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getStatus()).isEqualTo(BAD_REQUEST.value());
    assertThat(result.getBody().getTitle()).isEqualTo("Bad Request");
    assertThat(result.getBody().getType().toString()).contains("claim-amendment-validation");
    assertThat(result.getBody().getInstance().toString()).isEqualTo(TEST_REQUEST_URI);

    // Verify our custom properties structure
    assertThat(result.getBody().getProperties()).containsEntry("errors", ex.getErrors());
    assertThat(result.getBody().getProperties()).containsEntry("message", ex.getMessage());
  }

  @Test
  void handleClaimAmendmentVersionValidationException_returnsCustomStatusWhenFatal() {
    // Arrange: Create a fatal validation error scenario (e.g., using one of your new structural
    // checks)
    ClaimAmendmentValidationError fatalError =
        ClaimAmendmentValidationError.of(
            ClaimAmendmentValidationCode
                .INVALID_NULL_VERSION); // isFatal = true, status maps to 400 or custom status if
    // applicable
    ClaimAmendmentValidationException ex =
        new ClaimAmendmentValidationException(List.of(fatalError));

    // Act
    ResponseEntity<ProblemDetail> result =
        dataClaimsExceptionHandler.handleClaimAmendmentValidationException(ex, mockRequest);

    // Assert
    HttpStatus expectedStatus = HttpStatus.resolve(fatalError.getHttpStatus().value());
    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(expectedStatus);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getStatus()).isEqualTo(expectedStatus.value());
    assertThat(result.getBody().getProperties()).containsEntry("errors", ex.getErrors());
  }

  @Test
  void handleDatabaseOptimisticLockingException_returnsConflictStatusWithPredefinedMessage() {
    // Arrange
    org.springframework.orm.ObjectOptimisticLockingFailureException ex =
        new org.springframework.orm.ObjectOptimisticLockingFailureException(
            "Row was updated or deleted by another transaction", null);

    // Act
    ResponseEntity<ProblemDetail> result =
        dataClaimsExceptionHandler.handleDatabaseOptimisticLockingException(ex, mockRequest);

    // Assert
    String expectedMessage =
        ClaimAmendmentValidationCode.INVALID_CLAIM_VERSION_CONFLICT.getMessageTemplate();

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(result.getBody().getTitle()).isEqualTo("Conflict");
    assertThat(result.getBody().getDetail()).isEqualTo(expectedMessage);
    assertThat(result.getBody().getType().toString()).contains("object-optimistic-locking-failure");
    assertThat(result.getBody().getInstance().toString()).isEqualTo(TEST_REQUEST_URI);
    assertThat(result.getBody().getProperties()).containsEntry("message", expectedMessage);
  }

  @Test
  @DisplayName(
      "Constructor automatically sorts errors by fatality first, then by HTTP status code descending")
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
    List<ClaimAmendmentValidationError> unsortedErrors =
        List.of(nonFatal400, fatal400, nonFatal500, fatal500, fatal409);

    // Act
    ClaimAmendmentValidationException exception =
        new ClaimAmendmentValidationException(unsortedErrors);

    List<ClaimAmendmentValidationError> sortedResult =
        dataClaimsExceptionHandler.sortValidationErrorsByFatalAndStatus(exception);

    assertThat(sortedResult).hasSize(5);

    // Priority 1: Fatal errors take precedence, sorted highest HTTP status code first (500 -> 409
    // -> 400)
    assertThat(sortedResult.get(0)).isSameAs(fatal500);
    assertThat(sortedResult.get(1)).isSameAs(fatal409);
    assertThat(sortedResult.get(2)).isSameAs(fatal400);

    // Priority 2: Non-fatal errors follow, sorted highest HTTP status code first (500 -> 400)
    assertThat(sortedResult.get(3)).isSameAs(nonFatal500);
    assertThat(sortedResult.get(4)).isSameAs(nonFatal400);
  }
}
