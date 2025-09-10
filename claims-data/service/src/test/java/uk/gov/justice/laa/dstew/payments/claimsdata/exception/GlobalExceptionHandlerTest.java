package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {
  GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

  @Test
  void handleClaimNotFound_returnsNotFoundStatusAndErrorMessage() {
    ResponseEntity<String> result =
        globalExceptionHandler.handleClaimNotFound(new ClaimNotFoundException("Claim not found"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(NOT_FOUND);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody()).isEqualTo("Claim not found");
  }

  @Test
  @DisplayName("Handle BulkSubmissionValidationException")
  void handleBulkSubmissionValidationException_returnsBadRequestStatusAndErrorMessage() {
    ResponseEntity<String> result =
        globalExceptionHandler.handleValidationException(
            new BulkSubmissionValidationException("Field is required"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(BAD_REQUEST);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody()).isEqualTo("Field is required");
  }

  @Test
  @DisplayName("Handle BulkSubmissionInvalidFileException")
  void handleBulkSubmissionInvalidFileException_returnsBadRequestStatusAndErrorMessage() {
    ResponseEntity<String> result =
        globalExceptionHandler.handleUnsupportedMediaTypeValidationException(
            new BulkSubmissionInvalidFileException("Unsupported media type"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(UNSUPPORTED_MEDIA_TYPE);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody()).isEqualTo("Unsupported media type");
  }

  @Test
  void handleSubmissionNotFound_returnsNotFoundStatusAndErrorMessage() {
    ResponseEntity<String> result =
        globalExceptionHandler.handleSubmissionNotFound(
            new SubmissionNotFoundException("Submission not found"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(NOT_FOUND);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody()).isEqualTo("Submission not found");
  }

  @Test
  @DisplayName("Handle SubmissionBadRequestException")
  void handleSubmissionBadRequestException_returnsBadRequestStatusAndErrorMessage() {
    ResponseEntity<String> result =
        globalExceptionHandler.handleSubmissionBadRequestException(
            new SubmissionBadRequestException("Missing arguments"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(BAD_REQUEST);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody()).isEqualTo("Missing arguments");
  }

  @Test
  void handleGenericException_returnsInternalServerErrorStatusAndErrorMessage() {
    ResponseEntity<String> result =
        globalExceptionHandler.handleGenericException(new RuntimeException("Something went wrong"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody()).isEqualTo("An unexpected application error has occurred.");
  }
}
