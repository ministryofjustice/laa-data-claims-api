package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import uk.gov.laa.springboot.export.ExportValidationException;

class DataClaimsExceptionHandlerTest {
  DataClaimsExceptionHandler dataClaimsExceptionHandler = new DataClaimsExceptionHandler();

  @Test
  void handleGenericException_returnsInternalServerErrorStatusAndErrorMessage() {
    ResponseEntity<String> result =
        dataClaimsExceptionHandler.handleGenericException(
            new RuntimeException("Something went wrong"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody()).isEqualTo("An unexpected application error has occurred.");
  }

  @Test
  void handleExportValidationException_returnsBadRequestWithMessage() {
    ResponseEntity<String> result =
        dataClaimsExceptionHandler.handleExportValidationException(
            new ExportValidationException("Filter submissionId must be a UUID"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode().value()).isEqualTo(400);
    assertThat(result.getBody()).isEqualTo("Filter submissionId must be a UUID");
  }
}
