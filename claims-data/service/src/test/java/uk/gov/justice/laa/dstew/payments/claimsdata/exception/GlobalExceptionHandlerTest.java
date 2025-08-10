package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

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
  void handleGenericException_returnsInternalServerErrorStatusAndErrorMessage() {
    ResponseEntity<String> result =
        globalExceptionHandler.handleGenericException(new RuntimeException("Something went wrong"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody()).isEqualTo("An unexpected application error has occurred.");
  }
}
