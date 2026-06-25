package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentReferenceData;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AmendmentReferenceDataUnavailableException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.provider.AmendmentReferenceDataProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.UUID7;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimReasonValidationStep")
class ClaimReasonValidationStepTest {

  private static final String VALID_UUID = "0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e";
  private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

  @Mock private AmendmentReferenceDataProvider amendmentReferenceDataProvider;

  @InjectMocks private ClaimReasonValidationStep step;

  private RequestedByReferenceEntity requestedBy(String code, String label, boolean active) {
    return RequestedByReferenceEntity.builder()
        .id(UUID7.timeBasedUuid())
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
        .id(UUID7.timeBasedUuid())
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

  private ValidationMessagePatch onlyIssue(ClaimAmendmentState state) {
    assertThat(state.getValidationIssues()).hasSize(1);
    ValidationMessagePatch issue = state.getValidationIssues().getFirst();
    assertThat(issue.getType()).isEqualTo(ValidationMessageType.ERROR);
    return issue;
  }

  @Nested
  @DisplayName("happy path")
  class HappyPath {

    @Test
    @DisplayName("returns true and collects no issues when all metadata is valid")
    void noIssuesWhenAllValid() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("PROVIDER", "PROVIDER_ERROR", VALID_UUID);

      boolean valid = step.validate(state);

      assertThat(valid).isTrue();
      assertThat(state.getValidationIssues()).isEmpty();
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

      boolean valid = step.validate(state);

      assertThat(valid).isFalse();
      ValidationMessagePatch issue = onlyIssue(state);
      assertThat(issue.getSource()).isEqualTo("INVALID_REQUESTED_BY_MISSING");
      assertThat(issue.getDisplayMessage()).isEqualTo("Requested By is required");
    }

    @Test
    @DisplayName("unknown -> INVALID_REQUESTED_BY_UNKNOWN with the submitted value")
    void unknown() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("MADE_UP", "PROVIDER_ERROR", VALID_UUID);

      step.validate(state);

      ValidationMessagePatch issue = onlyIssue(state);
      assertThat(issue.getSource()).isEqualTo("INVALID_REQUESTED_BY_UNKNOWN");
      assertThat(issue.getDisplayMessage())
          .isEqualTo("Requested By 'MADE_UP' is not a recognised value");
    }

    @Test
    @DisplayName("inactive -> INVALID_REQUESTED_BY_INACTIVE")
    void inactive() {
      stubReferenceData();
      // Reason omitted to isolate the Requested By assertion (LEGACY_PARTY has no reasons).
      ClaimAmendmentState state = stateWith("LEGACY_PARTY", null, VALID_UUID);

      step.validate(state);

      assertThat(state.getValidationIssues())
          .extracting(ValidationMessagePatch::getSource)
          .contains("INVALID_REQUESTED_BY_INACTIVE");
      assertThat(state.getValidationIssues())
          .filteredOn(m -> "INVALID_REQUESTED_BY_INACTIVE".equals(m.getSource()))
          .singleElement()
          .extracting(ValidationMessagePatch::getDisplayMessage)
          .isEqualTo("Requested By 'LEGACY_PARTY' is no longer in use");
    }

    @Test
    @DisplayName("display label rather than code -> INVALID_REQUESTED_BY_NOT_A_CODE")
    void displayLabelNotCode() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("Provider", "PROVIDER_ERROR", VALID_UUID);

      step.validate(state);

      ValidationMessagePatch issue = onlyIssue(state);
      assertThat(issue.getSource()).isEqualTo("INVALID_REQUESTED_BY_NOT_A_CODE");
      assertThat(issue.getDisplayMessage())
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

      ValidationMessagePatch issue = onlyIssue(validateAndReturn(state));

      assertThat(issue.getSource()).isEqualTo("INVALID_AMENDMENT_REASON_MISSING");
      assertThat(issue.getDisplayMessage()).isEqualTo("Amendment Reason is required");
    }

    @Test
    @DisplayName("unknown -> INVALID_AMENDMENT_REASON_UNKNOWN")
    void unknown() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("PROVIDER", "MADE_UP", VALID_UUID);

      ValidationMessagePatch issue = onlyIssue(validateAndReturn(state));

      assertThat(issue.getSource()).isEqualTo("INVALID_AMENDMENT_REASON_UNKNOWN");
      assertThat(issue.getDisplayMessage())
          .isEqualTo("Amendment Reason 'MADE_UP' is not a recognised value");
    }

    @Test
    @DisplayName("display label rather than code -> INVALID_AMENDMENT_REASON_NOT_A_CODE")
    void displayLabelNotCode() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("PROVIDER", "Provider Error", VALID_UUID);

      ValidationMessagePatch issue = onlyIssue(validateAndReturn(state));

      assertThat(issue.getSource()).isEqualTo("INVALID_AMENDMENT_REASON_NOT_A_CODE");
      assertThat(issue.getDisplayMessage())
          .isEqualTo("Amendment Reason must be supplied as a code, not a display label");
    }

    @Test
    @DisplayName("inactive for the submitted Requested By -> INVALID_AMENDMENT_REASON_INACTIVE")
    void inactive() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("ASSURANCE", "OLD_REASON", VALID_UUID);

      ValidationMessagePatch issue = onlyIssue(validateAndReturn(state));

      assertThat(issue.getSource()).isEqualTo("INVALID_AMENDMENT_REASON_INACTIVE");
      assertThat(issue.getDisplayMessage())
          .isEqualTo("Amendment Reason 'OLD_REASON' is no longer in use");
    }

    @Test
    @DisplayName("valid code but wrong Requested By -> INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY")
    void notValidForRequestedBy() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("PROVIDER", "INCORRECT_MEANS_ASSESSMENT", VALID_UUID);

      ValidationMessagePatch issue = onlyIssue(validateAndReturn(state));

      assertThat(issue.getSource()).isEqualTo("INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY");
      assertThat(issue.getDisplayMessage())
          .isEqualTo(
              "Amendment Reason 'INCORRECT_MEANS_ASSESSMENT' is not valid for Requested By 'PROVIDER'");
    }

    @Test
    @DisplayName("does not cascade a FOR_REQUESTED_BY error when Requested By is itself invalid")
    void noCascadeWhenRequestedByInvalid() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("MADE_UP", "INCORRECT_MEANS_ASSESSMENT", VALID_UUID);

      step.validate(state);

      assertThat(state.getValidationIssues())
          .extracting(ValidationMessagePatch::getSource)
          .containsExactly("INVALID_REQUESTED_BY_UNKNOWN");
    }

    private ClaimAmendmentState validateAndReturn(ClaimAmendmentState state) {
      step.validate(state);
      return state;
    }
  }

  @Nested
  @DisplayName("submitting user id")
  class UserId {

    @Test
    @DisplayName("non-UUID -> INVALID_USER_IDENTIFIER_FORMAT")
    void rejectsNonUuidValue() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("PROVIDER", "PROVIDER_ERROR", "not-a-uuid");

      step.validate(state);

      ValidationMessagePatch issue = onlyIssue(state);
      assertThat(issue.getSource()).isEqualTo("INVALID_USER_IDENTIFIER_FORMAT");
      assertThat(issue.getDisplayMessage()).isEqualTo("The user identifier must be a valid UUID");
    }

    @Test
    @DisplayName("null -> INVALID_USER_IDENTIFIER_FORMAT")
    void nullUserId() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith("PROVIDER", "PROVIDER_ERROR", null);

      step.validate(state);

      assertThat(onlyIssue(state).getSource()).isEqualTo("INVALID_USER_IDENTIFIER_FORMAT");
    }
  }

  @Nested
  @DisplayName("multiple errors collected together")
  class MultipleErrors {

    @Test
    @DisplayName("missing Requested By, missing Reason and bad UUID all collected")
    void collectsAll() {
      stubReferenceData();
      ClaimAmendmentState state = stateWith(null, null, "bad");

      boolean valid = step.validate(state);

      assertThat(valid).isFalse();
      assertThat(state.getValidationIssues())
          .extracting(ValidationMessagePatch::getSource)
          .containsExactlyInAnyOrder(
              "INVALID_REQUESTED_BY_MISSING",
              "INVALID_AMENDMENT_REASON_MISSING",
              "INVALID_USER_IDENTIFIER_FORMAT");
    }
  }

  @Nested
  @DisplayName("controlled technical failure")
  class TechnicalFailure {

    @Test
    @DisplayName("propagates the unavailable error and saves nothing when the provider fails")
    void propagatesUnavailableError() {
      when(amendmentReferenceDataProvider.getReferenceData())
          .thenThrow(new AmendmentReferenceDataUnavailableException());
      ClaimAmendmentState state = stateWith("PROVIDER", "PROVIDER_ERROR", VALID_UUID);

      assertThatThrownBy(() -> step.validate(state))
          .isInstanceOf(AmendmentReferenceDataUnavailableException.class)
          .hasMessage("A technical error occurred, please try again after some time");
      assertThat(state.getValidationIssues()).isEmpty();
    }

    @Test
    @DisplayName("throws when the Requested By reference data is empty")
    void throwsWhenRequestedByReferenceEmpty() {
      when(amendmentReferenceDataProvider.getReferenceData())
          .thenReturn(
              new ClaimAmendmentReferenceData(
                  List.of(),
                  List.of(reason("PROVIDER", "PROVIDER_ERROR", "Provider Error", true))));
      ClaimAmendmentState state = stateWith("PROVIDER", "PROVIDER_ERROR", VALID_UUID);

      assertThatThrownBy(() -> step.validate(state))
          .isInstanceOf(AmendmentReferenceDataUnavailableException.class);
    }

    @Test
    @DisplayName("throws when the Amendment Reason reference data is empty")
    void throwsWhenReasonReferenceEmpty() {
      when(amendmentReferenceDataProvider.getReferenceData())
          .thenReturn(
              new ClaimAmendmentReferenceData(
                  List.of(requestedBy("PROVIDER", "Provider", true)), List.of()));
      ClaimAmendmentState state = stateWith("PROVIDER", "PROVIDER_ERROR", VALID_UUID);

      assertThatThrownBy(() -> step.validate(state))
          .isInstanceOf(AmendmentReferenceDataUnavailableException.class);
    }
  }
}
