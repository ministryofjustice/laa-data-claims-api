package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ValidationSeverity;

/**
 * Tests for {@link AmendmentFeatureFlagValidationStep}.
 *
 * <p>The step reads the typed {@link ClaimsApiProperties} amendments flag (bound from {@code
 * laa.claims.api.amendments.enabled}). The feature is <b>off by default</b>: it passes only when
 * the configured value is explicitly {@code "true"} (case-insensitive); every other value -
 * absent/null, blank, {@code "false"} or an invalid value - fails fatally.
 */
@DisplayName("AmendmentFeatureFlagValidationStep Tests")
class AmendmentFeatureFlagValidationStepTest {

  private static AmendmentFeatureFlagValidationStep stepWith(String enabled) {
    ClaimsApiProperties properties = new ClaimsApiProperties();
    properties.getAmendments().setEnabled(enabled);
    return new AmendmentFeatureFlagValidationStep(properties);
  }

  private static ClaimAmendmentState anyState() {
    return ClaimAmendmentState.builder().build();
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "TRUE", "True", " true ", "\ttrue\n"})
  @DisplayName("explicitly true (case-insensitive, whitespace-tolerant) -> validation passes")
  void explicitlyTruePasses(String enabled) {
    assertThat(stepWith(enabled).validate(anyState())).isEmpty();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "false", "FALSE", "off", "no", "1", "yes", "banana"})
  @DisplayName("anything other than true (null, blank, false, invalid) -> fatal and off")
  void anythingOtherThanTrueIsOff(String enabled) {
    assertThat(stepWith(enabled).validate(anyState()))
        .singleElement()
        .extracting(ClaimAmendmentValidationError::getCode)
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_AMENDMENTS_FEATURE_DISABLED.toString());
  }

  @Test
  @DisplayName("off by default when the flag is not configured")
  void offByDefaultWhenNotConfigured() {
    AmendmentFeatureFlagValidationStep step =
        new AmendmentFeatureFlagValidationStep(new ClaimsApiProperties());

    assertThat(step.validate(anyState()))
        .singleElement()
        .extracting(ClaimAmendmentValidationError::getCode)
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_AMENDMENTS_FEATURE_DISABLED.toString());
  }

  @Test
  @DisplayName("disabled error carries the agreed message, status and fatal severity")
  void disabledErrorDetails() {
    List<ClaimAmendmentValidationError> result = stepWith("false").validate(anyState());

    assertThat(result).hasSize(1);
    ClaimAmendmentValidationError error = result.getFirst();
    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_AMENDMENTS_FEATURE_DISABLED.toString());
    assertThat(error.getMessage()).isEqualTo("Amendments are not currently enabled.");
    assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(error.getSeverity()).isEqualTo(ValidationSeverity.FATAL);
    assertThat(error.isFatal()).isTrue();
  }
}
