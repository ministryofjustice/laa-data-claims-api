package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

@DisplayName("AmendmentUserIdValidationStep")
class AmendmentUserIdValidationStepTest {

  private static final String VALID_UUID = "0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e";

  private final AmendmentUserIdValidationStep step = new AmendmentUserIdValidationStep();

  private ClaimAmendmentState stateWithUserId(String userId) {
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder().amendmentUserId(JsonNullable.of(userId)).build();
    return ClaimAmendmentState.builder().requestPayload(payload).build();
  }

  @Test
  @DisplayName("valid UUID -> no errors")
  void validUuidPasses() {
    assertThat(step.validate(stateWithUserId(VALID_UUID))).isEmpty();
  }

  @Test
  @DisplayName("non-UUID -> INVALID_USER_IDENTIFIER_FORMAT")
  void rejectsNonUuidValue() {
    ClaimAmendmentValidationError error = step.validate(stateWithUserId("not-a-uuid")).getFirst();

    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_USER_IDENTIFIER_FORMAT);
    assertThat(error.getMessage()).isEqualTo("The user identifier must be a valid UUID");
  }

  @Test
  @DisplayName("null -> INVALID_USER_IDENTIFIER_MISSING")
  void rejectsNullValue() {
    ClaimAmendmentValidationError error = step.validate(stateWithUserId(null)).getFirst();

    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_USER_IDENTIFIER_MISSING);
    assertThat(error.getMessage()).isEqualTo("The user identifier is required");
  }

  @Test
  @DisplayName("absent JsonNullable -> INVALID_USER_IDENTIFIER_MISSING")
  void rejectsAbsentValue() {
    ClaimAmendmentPayload payload = ClaimAmendmentPayload.builder().build();
    ClaimAmendmentState state = ClaimAmendmentState.builder().requestPayload(payload).build();

    assertThat(step.validate(state))
        .singleElement()
        .extracting(ClaimAmendmentValidationError::getCode)
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_USER_IDENTIFIER_MISSING);
  }
}
