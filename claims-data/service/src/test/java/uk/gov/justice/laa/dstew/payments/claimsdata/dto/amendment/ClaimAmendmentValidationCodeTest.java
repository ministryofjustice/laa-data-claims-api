package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Guards the {@link ClaimAmendmentValidationCode} catalogue: every code must declare the metadata
 * that defines its effect on the user, so the catalogue stays complete as codes are added.
 */
@DisplayName("ClaimAmendmentValidationCode catalogue")
class ClaimAmendmentValidationCodeTest {

  @ParameterizedTest
  @EnumSource(ClaimAmendmentValidationCode.class)
  @DisplayName("every code declares a severity, an HTTP status and a message template")
  void everyCodeDeclaresCompleteMetadata(ClaimAmendmentValidationCode code) {
    assertThat(code.getSeverity()).as("severity for %s", code).isNotNull();
    assertThat(code.getHttpStatus()).as("HTTP status for %s", code).isNotNull();
    assertThat(code.getMessageTemplate()).as("message template for %s", code).isNotBlank();
  }
}
