package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import jakarta.persistence.OptimisticLockException;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.laa.springboot.export.ExportValidationException;

@DisplayName("DataClaimsExceptionHandler Tests")
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
  @DisplayName("An unhandled RuntimeException maps to a 500 Internal Server Error Problem Detail")
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
  @DisplayName("A ClaimsDataException maps to its carried HTTP status and error type")
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
  @DisplayName("An ExportValidationException maps to a 400 Bad Request carrying its message")
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
    // Arrange: Create a fatal validation error scenario
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
  @DisplayName("a no-op amendment (204 fatal outcome) returns an empty 204 with no body")
  void handleClaimAmendmentValidationException_returnsBodiless204WhenNoChanges() {
    // A no-op amendment halts with a fatal NO_AMENDMENT_CHANGES_SUBMITTED code carrying a 204
    // status; a 204 response must not carry a body per RFC 9110.
    ClaimAmendmentValidationError noChangeError =
        ClaimAmendmentValidationError.of(
            ClaimAmendmentValidationCode.NO_AMENDMENT_CHANGES_SUBMITTED);
    ClaimAmendmentValidationException ex =
        new ClaimAmendmentValidationException(List.of(noChangeError));

    ResponseEntity<ProblemDetail> result =
        dataClaimsExceptionHandler.handleClaimAmendmentValidationException(ex, mockRequest);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(result.getBody()).isNull();
  }

  @Test
  void handleDatabaseOptimisticLockingException_returnsConflictStatusWithPredefinedMessage() {
    // Arrange
    OptimisticLockException ex =
        new OptimisticLockException("Row was updated or deleted by another transaction", null);

    // Act
    ResponseEntity<ProblemDetail> result =
        dataClaimsExceptionHandler.handleDatabaseOptimisticLockException(ex, mockRequest);

    // Assert
    String expectedMessage =
        ClaimAmendmentValidationCode.INVALID_CLAIM_VERSION_CONFLICT.getMessageTemplate();

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(result.getBody().getTitle()).isEqualTo("Conflict");
    assertThat(result.getBody().getDetail()).isEqualTo(expectedMessage);
    assertThat(result.getBody().getType().toString()).contains("optimistic-lock");
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

  @Test
  @DisplayName(
      "Spring's ObjectOptimisticLockingFailureException maps to a 409 Conflict Problem Detail")
  void handleOptimisticLockingFailure_returnsConflict() {
    ObjectOptimisticLockingFailureException ex =
        new ObjectOptimisticLockingFailureException("Claim", new Exception());

    ResponseEntity<ProblemDetail> result =
        dataClaimsExceptionHandler.handleDatabaseOptimisticLockException(ex, mockRequest);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(CONFLICT);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getStatus()).isEqualTo(CONFLICT.value());
    assertThat(result.getBody().getTitle()).isEqualTo("Conflict");
    assertThat(result.getBody().getDetail()).isEqualTo("Claim Version conflict exists");
    assertThat(result.getBody().getType().toString()).contains("optimistic-lock");
    assertThat(result.getBody().getInstance().toString()).isEqualTo(TEST_REQUEST_URI);
  }

  @Test
  @DisplayName("The JPA OptimisticLockException also maps to a 409 Conflict (not a generic 500)")
  void handleOptimisticLockingFailure_returnsConflict_forJpaOptimisticLockException() {
    // Hibernate raises the JPA type when the stale version is caught during merge/flush; it must
    // also map to 409 (not fall through to the generic 500 handler).
    OptimisticLockException ex = new OptimisticLockException("Row was already updated");

    ResponseEntity<ProblemDetail> result =
        dataClaimsExceptionHandler.handleDatabaseOptimisticLockException(ex, mockRequest);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(CONFLICT);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getStatus()).isEqualTo(CONFLICT.value());
    assertThat(result.getBody().getTitle()).isEqualTo("Conflict");
    assertThat(result.getBody().getDetail()).isEqualTo("Claim Version conflict exists");
    assertThat(result.getBody().getType().toString()).contains("optimistic-lock");
    assertThat(result.getBody().getInstance().toString()).isEqualTo(TEST_REQUEST_URI);
  }
}
