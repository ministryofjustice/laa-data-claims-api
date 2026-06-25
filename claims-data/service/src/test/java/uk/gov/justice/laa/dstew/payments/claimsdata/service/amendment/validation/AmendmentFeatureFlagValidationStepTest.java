package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ValidationSeverity;

/**
 * Tests for {@link AmendmentFeatureFlagValidationStep}.
 *
 * <p>The step reads {@code claims.api.amendments.enabled} from the Spring {@link
 * org.springframework.core.env.Environment} (here a {@link MockEnvironment}). These tests pin the
 * three behaviours required by the ticket: flag present and {@code true}, flag present and {@code
 * false}, and flag missing (defaults to {@code false}).
 */
@DisplayName("AmendmentFeatureFlagValidationStep Tests")
class AmendmentFeatureFlagValidationStepTest {

  private static final String FEATURE_FLAG_PROPERTY = "claims.api.amendments.enabled";

  private static AmendmentFeatureFlagValidationStep stepWith(MockEnvironment environment) {
    return new AmendmentFeatureFlagValidationStep(environment);
  }

  private static ClaimAmendmentState anyState() {
    return ClaimAmendmentState.builder().build();
  }

  @Test
  @DisplayName("flag present and true -> validation passes")
  void flagPresentAndTruePasses() {
    MockEnvironment environment = new MockEnvironment().withProperty(FEATURE_FLAG_PROPERTY, "true");

    assertThat(stepWith(environment).validate(anyState())).isEmpty();
  }

  @Test
  @DisplayName("flag present and false -> fatal INVALID_AMENDMENTS_FEATURE_DISABLED")
  void flagPresentAndFalseFailsFatally() {
    MockEnvironment environment =
        new MockEnvironment().withProperty(FEATURE_FLAG_PROPERTY, "false");

    List<ClaimAmendmentValidationError> result = stepWith(environment).validate(anyState());

    assertThat(result).hasSize(1);
    ClaimAmendmentValidationError error = result.getFirst();
    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_AMENDMENTS_FEATURE_DISABLED);
    assertThat(error.getMessage()).isEqualTo("Amendments are not currently enabled.");
    assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(error.getSeverity()).isEqualTo(ValidationSeverity.FATAL);
    assertThat(error.isFatal()).isTrue();
  }

  @Test
  @DisplayName("flag missing -> defaults to false -> fatal INVALID_AMENDMENTS_FEATURE_DISABLED")
  void flagMissingDefaultsToDisabled() {
    List<ClaimAmendmentValidationError> result =
        stepWith(new MockEnvironment()).validate(anyState());

    assertThat(result)
        .singleElement()
        .extracting(ClaimAmendmentValidationError::getCode)
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_AMENDMENTS_FEATURE_DISABLED);
  }
}
