package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
}
