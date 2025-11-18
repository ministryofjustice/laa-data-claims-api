package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class DataClaimsExceptionHandlerTest {
  DataClaimsExceptionHandler dataClaimsExceptionHandler = new DataClaimsExceptionHandler(null);

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
}
