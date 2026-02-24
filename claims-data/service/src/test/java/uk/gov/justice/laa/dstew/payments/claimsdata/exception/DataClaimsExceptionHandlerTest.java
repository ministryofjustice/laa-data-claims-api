package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class DataClaimsExceptionHandlerTest {
  DataClaimsExceptionHandler dataClaimsExceptionHandler = new DataClaimsExceptionHandler();

  @Test
  void handleGenericException_returnsProblemDetailWithInternalServerErrorStatus() {
    ResponseEntity<ProblemDetail> result =
        dataClaimsExceptionHandler.handleGenericException(
            new RuntimeException("Something went wrong"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getStatus()).isEqualTo(INTERNAL_SERVER_ERROR.value());
    assertThat(result.getBody().getTitle()).isEqualTo("Internal Server Error");
    assertThat(result.getBody().getDetail())
        .isEqualTo("An unexpected application error has occurred.");
    assertThat(result.getBody().getType().toString())
        .isEqualTo("https://claimsdata.payments.laa.justice.gov.uk/errors/internal-server-error");
    // Verify backward compatibility property
    assertThat(result.getBody().getProperties())
        .containsEntry("message", "An unexpected application error has occurred.");
  }

  @Test
  void handleClaimsDataException_withBulkSubmissionValidationException_returnsBadRequest() {
    BulkSubmissionValidationException ex =
        new BulkSubmissionValidationException("Validation failed for field X");

    ResponseEntity<ProblemDetail> result = dataClaimsExceptionHandler.handleClaimsDataException(ex);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(BAD_REQUEST);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getStatus()).isEqualTo(BAD_REQUEST.value());
    assertThat(result.getBody().getTitle()).isEqualTo("Bad Request");
    assertThat(result.getBody().getDetail()).isEqualTo("Validation failed for field X");
    assertThat(result.getBody().getType().toString()).contains("bulk-submission-validation");
    // Verify backward compatibility property
    assertThat(result.getBody().getProperties())
        .containsEntry("message", "Validation failed for field X");
  }

  @Test
  void handleClaimsDataException_returnsProblemDetailWithCorrectStatus() {
    ClaimsDataException ex = new ClaimsDataException("Resource not found", HttpStatus.NOT_FOUND);

    ResponseEntity<ProblemDetail> result = dataClaimsExceptionHandler.handleClaimsDataException(ex);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(NOT_FOUND);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getStatus()).isEqualTo(NOT_FOUND.value());
    assertThat(result.getBody().getTitle()).isEqualTo("Not Found");
    assertThat(result.getBody().getDetail()).isEqualTo("Resource not found");
    assertThat(result.getBody().getType().toString()).contains("claims-data");
    // Verify backward compatibility property
    assertThat(result.getBody().getProperties()).containsEntry("message", "Resource not found");
  }
}
