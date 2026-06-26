package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentReferenceData;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AmendmentReferenceDataUnavailableException;
import uk.gov.justice.laa.dstew.payments.claimsdata.provider.AmendmentReferenceDataProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimReasonValidationStep")
class AmendmentReferenceValidationStepTest {

  private static final String VALID_UUID = "0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e";
  private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

  @Mock private AmendmentReferenceDataProvider amendmentReferenceDataProvider;

  @InjectMocks private AmendmentReferenceValidationStep step;

  private RequestedByReferenceEntity requestedBy(String code, String label, boolean active) {
    return RequestedByReferenceEntity.builder()
        .id(Uuid7.timeBasedUuid())
        .code(code)
        .displayLabel(label)
        .isActive(active)
        .displayOrder(10)
        .createdByUserId("test")
        .createdOn(FIXED_INSTANT)
        .build();
  }

  private AmendmentReasonReferenceEntity reason(
      String requestedByCode, String code, String label, boolean active) {
    return AmendmentReasonReferenceEntity.builder()
        .id(Uuid7.timeBasedUuid())
        .requestedByCode(requestedByCode)
        .code(code)
        .displayLabel(label)
        .isActive(active)
        .displayOrder(10)
        .createdByUserId("test")
        .createdOn(FIXED_INSTANT)
        .build();
  }

  private void stubReferenceData() {
    when(amendmentReferenceDataProvider.getReferenceData())
        .thenReturn(
            new ClaimAmendmentReferenceData(
                List.of(
                    requestedBy("PROVIDER", "Provider", true),
                    requestedBy("ASSURANCE", "Assurance", true),
                    requestedBy("LEGACY_PARTY", "Legacy Party", false)),
                List.of(
                    reason("PROVIDER", "PROVIDER_ERROR", "Provider Error", true),
                    reason(
                        "ASSURANCE",
                        "INCORRECT_MEANS_ASSESSMENT",
                        "Incorrect Means Assessment",
                        true),
                    reason("ASSURANCE", "OLD_REASON", "Old reason", false))));
  }

  private ClaimAmendmentState stateWith(String requestedBy, String reason, String userId) {
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .amendmentRequestedBy(JsonNullable.of(requestedBy))
            .amendmentReasonCode(JsonNullable.of(reason))
            .amendmentUserId(JsonNullable.of(userId))
            .build();
    return ClaimAmendmentState.builder().requestPayload(payload).build();
  }

  private ClaimAmendmentValidationError onlyError(List<ClaimAmendmentValidationError> errors) {
    assertThat(errors).hasSize(1);
    return errors.getFirst();
  }

  @Nested
  @DisplayName("happy path")
  class HappyPath {

    @Test
    @DisplayName("returns no errors when all metadata is valid")
    void noErrorsWhenAllValid() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("PROVIDER", "PROVIDER_ERROR", VALID_UUID);

      assertThat(step.validate(state)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Requested By")
  class RequestedBy {

    @Test
    @DisplayName("missing -> INVALID_REQUESTED_BY_MISSING")
    void missing() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("  ", "PROVIDER_ERROR", VALID_UUID);

      ClaimAmendmentValidationError error = onlyError(step.validate(state));

      assertThat(error.getCode())
          .isEqualTo(ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_MISSING);
      assertThat(error.getMessage()).isEqualTo("Requested By is required");
    }

    @Test
    @DisplayName("unknown -> INVALID_REQUESTED_BY_UNKNOWN with the submitted value")
    void unknown() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("MADE_UP", "PROVIDER_ERROR", VALID_UUID);

      ClaimAmendmentValidationError error = onlyError(step.validate(state));

      assertThat(error.getCode())
          .isEqualTo(ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_UNKNOWN);
      assertThat(error.getMessage()).isEqualTo("Requested By 'MADE_UP' is not a recognised value");
    }

    @Test
    @DisplayName("inactive -> INVALID_REQUESTED_BY_INACTIVE")
    void inactive() {
      stubReferenceData();
      // Reason omitted to isolate the Requested By assertion (LEGACY_PARTY has no reasons).
      ClaimAmendmentState state = stateWith("LEGACY_PARTY", null, VALID_UUID);

      List<ClaimAmendmentValidationError> errors = step.validate(state);

      assertThat(errors)
          .filteredOn(
              error ->
                  error.getCode() == ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_INACTIVE)
          .singleElement()
          .extracting(ClaimAmendmentValidationError::getMessage)
          .isEqualTo("Requested By 'LEGACY_PARTY' is no longer in use");
    }

    @Test
    @DisplayName("display label rather than code -> INVALID_REQUESTED_BY_NOT_A_CODE")
    void displayLabelNotCode() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("Provider", "PROVIDER_ERROR", VALID_UUID);

      ClaimAmendmentValidationError error = onlyError(step.validate(state));

      assertThat(error.getCode())
          .isEqualTo(ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_NOT_A_CODE);
      assertThat(error.getMessage())
          .isEqualTo("Requested By must be supplied as a code, not a display label");
    }
  }

  @Nested
  @DisplayName("Amendment Reason")
  class AmendmentReason {

    @Test
    @DisplayName("missing -> INVALID_AMENDMENT_REASON_MISSING")
    void missing() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("PROVIDER", null, VALID_UUID);

      ClaimAmendmentValidationError error = onlyError(step.validate(state));

      assertThat(error.getCode())
          .isEqualTo(ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_MISSING);
      assertThat(error.getMessage()).isEqualTo("Amendment Reason is required");
    }

    @Test
    @DisplayName("unknown -> INVALID_AMENDMENT_REASON_UNKNOWN")
    void unknown() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("PROVIDER", "MADE_UP", VALID_UUID);

      ClaimAmendmentValidationError error = onlyError(step.validate(state));

      assertThat(error.getCode())
          .isEqualTo(ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_UNKNOWN);
      assertThat(error.getMessage())
          .isEqualTo("Amendment Reason 'MADE_UP' is not a recognised value");
    }

    @Test
    @DisplayName("display label rather than code -> INVALID_AMENDMENT_REASON_NOT_A_CODE")
    void displayLabelNotCode() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("PROVIDER", "Provider Error", VALID_UUID);

      ClaimAmendmentValidationError error = onlyError(step.validate(state));

      assertThat(error.getCode())
          .isEqualTo(ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_NOT_A_CODE);
      assertThat(error.getMessage())
          .isEqualTo("Amendment Reason must be supplied as a code, not a display label");
    }

    @Test
    @DisplayName("inactive for the submitted Requested By -> INVALID_AMENDMENT_REASON_INACTIVE")
    void inactive() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("ASSURANCE", "OLD_REASON", VALID_UUID);

      ClaimAmendmentValidationError error = onlyError(step.validate(state));

      assertThat(error.getCode())
          .isEqualTo(ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_INACTIVE);
      assertThat(error.getMessage()).isEqualTo("Amendment Reason 'OLD_REASON' is no longer in use");
    }

    @Test
    @DisplayName("valid code but wrong Requested By -> INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY")
    void notValidForRequestedBy() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("PROVIDER", "INCORRECT_MEANS_ASSESSMENT", VALID_UUID);

      ClaimAmendmentValidationError error = onlyError(step.validate(state));

      assertThat(error.getCode())
          .isEqualTo(ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY);
      assertThat(error.getMessage())
          .isEqualTo(
              "Amendment Reason 'INCORRECT_MEANS_ASSESSMENT' is not valid for Requested By 'PROVIDER'");
    }

    @Test
    @DisplayName("does not cascade a FOR_REQUESTED_BY error when Requested By is itself invalid")
    void noCascadeWhenRequestedByInvalid() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("MADE_UP", "INCORRECT_MEANS_ASSESSMENT", VALID_UUID);

      assertThat(step.validate(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly(ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_UNKNOWN);
    }
  }

  @Nested
  @DisplayName("multiple errors collected together")
  class MultipleErrors {

    @Test
    @DisplayName("missing Requested By and missing Reason are both collected")
    void collectsAll() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith(null, null, VALID_UUID);

      assertThat(step.validate(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactlyInAnyOrder(
              ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_MISSING,
              ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_MISSING);
    }
  }

  @Nested
  @DisplayName("controlled technical failure")
  class TechnicalFailure {

    @Test
    @DisplayName("returns a single fatal technical error when the provider fails")
    void returnsFatalErrorWhenProviderFails() {
      when(amendmentReferenceDataProvider.getReferenceData())
          .thenThrow(new AmendmentReferenceDataUnavailableException());
      ClaimAmendmentState state = stateWith("PROVIDER", "PROVIDER_ERROR", VALID_UUID);

      List<ClaimAmendmentValidationError> errors = step.validate(state);

      assertThat(errors)
          .singleElement()
          .satisfies(
              error -> {
                assertThat(error.getCode())
                    .isEqualTo(
                        ClaimAmendmentValidationCode
                            .TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA);
                assertThat(error.isFatal()).isTrue();
              });
    }

    @Test
    @DisplayName(
        "returns a single fatal technical error when the Requested By reference data is empty")
    void returnsFatalErrorWhenRequestedByReferenceEmpty() {
      when(amendmentReferenceDataProvider.getReferenceData())
          .thenReturn(
              new ClaimAmendmentReferenceData(
                  List.of(),
                  List.of(reason("PROVIDER", "PROVIDER_ERROR", "Provider Error", true))));
      ClaimAmendmentState state = stateWith("PROVIDER", "PROVIDER_ERROR", VALID_UUID);

      assertThat(step.validate(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly(
              ClaimAmendmentValidationCode.TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA);
    }

    @Test
    @DisplayName(
        "returns a single fatal technical error when the Amendment Reason reference data is empty")
    void returnsFatalErrorWhenReasonReferenceEmpty() {
      when(amendmentReferenceDataProvider.getReferenceData())
          .thenReturn(
              new ClaimAmendmentReferenceData(
                  List.of(requestedBy("PROVIDER", "Provider", true)), List.of()));
      ClaimAmendmentState state = stateWith("PROVIDER", "PROVIDER_ERROR", VALID_UUID);

      assertThat(step.validate(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly(
              ClaimAmendmentValidationCode.TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA);
    }
  }
}
