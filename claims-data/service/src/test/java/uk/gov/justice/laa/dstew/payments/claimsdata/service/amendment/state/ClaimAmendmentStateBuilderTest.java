package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.state;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.AMENDED_CASE_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.AMENDED_CLIENT_DATE_OF_BIRTH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.AMENDED_CLIENT_FORENAME;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.AMENDED_CLIENT_SURNAME;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.AMENDED_FEE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.AMENDED_UNIQUE_CLIENT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.AMENDED_UNIQUE_FILE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CASE_CONCLUDED_DATE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CASE_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CASE_REFERENCE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CASE_START_DATE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CATEGORY_OF_LAW;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CLAIM_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CLIENT_DATE_OF_BIRTH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CLIENT_FORENAME;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CLIENT_SURNAME;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.EXEMPTION_CRITERIA_SATISFIED;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.FEE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.MATTER_TYPE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.OFFICE_ACCOUNT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.REPRESENTATION_ORDER_DATE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.SCHEDULE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.SUBMISSION_PERIOD;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.UNIQUE_CLIENT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.UNIQUE_FILE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.VERSION;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

@DisplayName("ClaimAmendmentStateBuilder Tests")
class ClaimAmendmentStateBuilderTest {

  private final ClaimAmendmentStateBuilder builder = new ClaimAmendmentStateBuilder();

  private static ClaimStateSnapshot beforeState() {
    return ClaimStateSnapshot.builder()
        .claimId(CLAIM_ID)
        .submissionId(SUBMISSION_ID)
        .status(ClaimStatus.READY_TO_PROCESS)
        .version(VERSION)
        .hasAssessment(false)
        .amended(false)
        .areaOfLaw(AreaOfLaw.CRIME_LOWER)
        .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
        .submissionPeriod(SUBMISSION_PERIOD)
        .scheduleReference(SCHEDULE_REFERENCE)
        .caseReferenceNumber(CASE_REFERENCE_NUMBER)
        .uniqueFileNumber(UNIQUE_FILE_NUMBER)
        .caseStartDate(CASE_START_DATE)
        .caseConcludedDate(CASE_CONCLUDED_DATE)
        .representationOrderDate(REPRESENTATION_ORDER_DATE)
        .matterTypeCode(MATTER_TYPE_CODE)
        .feeCode(FEE_CODE)
        .categoryOfLaw(CATEGORY_OF_LAW)
        .clientForename(CLIENT_FORENAME)
        .clientSurname(CLIENT_SURNAME)
        .clientDateOfBirth(CLIENT_DATE_OF_BIRTH)
        .uniqueClientNumber(UNIQUE_CLIENT_NUMBER)
        .caseId(CASE_ID)
        .exemptionCriteriaSatisfied(EXEMPTION_CRITERIA_SATISFIED)
        .build();
  }

  @Nested
  @DisplayName("Sparse payload semantics")
  class SparsePayloadSemantics {

    @Test
    @DisplayName("omitted field retains the stored value")
    void omittedFieldRetainsStoredValue() {
      // empty payload: every field undefined
      ClaimAmendmentPayload payload = ClaimAmendmentPayload.builder().build();

      ClaimStateSnapshot after = builder.buildPostAmendmentState(beforeState(), payload);

      assertThat(after.getFeeCode()).isEqualTo(FEE_CODE);
      assertThat(after.getCaseReferenceNumber()).isEqualTo(CASE_REFERENCE_NUMBER);
      assertThat(after.getClientForename()).isEqualTo(CLIENT_FORENAME);
    }

    @Test
    @DisplayName("explicit null clears the field for later validation")
    void explicitNullClearsField() {
      ClaimAmendmentPayload payload =
          ClaimAmendmentPayload.builder().feeCode(JsonNullable.of(null)).build();

      ClaimStateSnapshot after = builder.buildPostAmendmentState(beforeState(), payload);

      assertThat(after.getFeeCode()).isNull();
      // unrelated field untouched
      assertThat(after.getCaseReferenceNumber()).isEqualTo(CASE_REFERENCE_NUMBER);
    }

    @Test
    @DisplayName("changed scalar value is applied")
    void changedScalarValueApplied() {
      ClaimAmendmentPayload payload =
          ClaimAmendmentPayload.builder().feeCode(JsonNullable.of(AMENDED_FEE_CODE)).build();

      ClaimStateSnapshot after = builder.buildPostAmendmentState(beforeState(), payload);

      assertThat(after.getFeeCode()).isEqualTo(AMENDED_FEE_CODE);
    }

    @Test
    @DisplayName("no-op same-value payload leaves the field unchanged")
    void noOpSameValueLeavesFieldUnchanged() {
      ClaimAmendmentPayload payload =
          ClaimAmendmentPayload.builder().feeCode(JsonNullable.of(FEE_CODE)).build();

      ClaimStateSnapshot after = builder.buildPostAmendmentState(beforeState(), payload);

      assertThat(after.getFeeCode()).isEqualTo(FEE_CODE);
    }
  }

  @Nested
  @DisplayName("UCN/UFN handling")
  class UcnUfnHandling {

    @Test
    @DisplayName("UCN/UFN remain unchanged when name, date of birth and case id change")
    void ucnUfnUnchangedWhenIdentityFieldsChange() {
      ClaimAmendmentPayload payload =
          ClaimAmendmentPayload.builder()
              .clientForename(JsonNullable.of(AMENDED_CLIENT_FORENAME))
              .clientSurname(JsonNullable.of(AMENDED_CLIENT_SURNAME))
              .clientDateOfBirth(JsonNullable.of(AMENDED_CLIENT_DATE_OF_BIRTH))
              .caseId(JsonNullable.of(AMENDED_CASE_ID))
              .build();

      ClaimStateSnapshot after = builder.buildPostAmendmentState(beforeState(), payload);

      assertThat(after.getClientForename()).isEqualTo(AMENDED_CLIENT_FORENAME);
      assertThat(after.getClientSurname()).isEqualTo(AMENDED_CLIENT_SURNAME);
      assertThat(after.getClientDateOfBirth()).isEqualTo(AMENDED_CLIENT_DATE_OF_BIRTH);
      assertThat(after.getCaseId()).isEqualTo(AMENDED_CASE_ID);
      // UCN/UFN must not be recomputed
      assertThat(after.getUniqueClientNumber()).isEqualTo(UNIQUE_CLIENT_NUMBER);
      assertThat(after.getUniqueFileNumber()).isEqualTo(UNIQUE_FILE_NUMBER);
    }

    @Test
    @DisplayName("UCN/UFN change only when explicitly submitted")
    void ucnUfnChangeWhenExplicitlySubmitted() {
      ClaimAmendmentPayload payload =
          ClaimAmendmentPayload.builder()
              .uniqueClientNumber(JsonNullable.of(AMENDED_UNIQUE_CLIENT_NUMBER))
              .uniqueFileNumber(JsonNullable.of(AMENDED_UNIQUE_FILE_NUMBER))
              .build();

      ClaimStateSnapshot after = builder.buildPostAmendmentState(beforeState(), payload);

      assertThat(after.getUniqueClientNumber()).isEqualTo(AMENDED_UNIQUE_CLIENT_NUMBER);
      assertThat(after.getUniqueFileNumber()).isEqualTo(AMENDED_UNIQUE_FILE_NUMBER);
    }
  }

  @Nested
  @DisplayName("Identity and context fields")
  class IdentityAndContext {

    @Test
    @DisplayName("non-amendable identity/context fields are carried over unchanged")
    void identityAndContextCarriedOver() {
      ClaimStateSnapshot before = beforeState();
      ClaimAmendmentPayload payload =
          ClaimAmendmentPayload.builder().feeCode(JsonNullable.of(AMENDED_FEE_CODE)).build();

      ClaimStateSnapshot after = builder.buildPostAmendmentState(before, payload);

      assertThat(after.getClaimId()).isEqualTo(before.getClaimId());
      assertThat(after.getSubmissionId()).isEqualTo(before.getSubmissionId());
      assertThat(after.getVersion()).isEqualTo(before.getVersion());
      assertThat(after.getAreaOfLaw()).isEqualTo(before.getAreaOfLaw());
      assertThat(after.getOfficeAccountNumber()).isEqualTo(before.getOfficeAccountNumber());
      assertThat(after.getSubmissionPeriod()).isEqualTo(before.getSubmissionPeriod());
      assertThat(after.getCategoryOfLaw()).isEqualTo(before.getCategoryOfLaw());
      assertThat(after.getExemptionCriteriaSatisfied())
          .isEqualTo(before.getExemptionCriteriaSatisfied());
    }

    @Test
    @DisplayName("status is read-only and carried over unchanged from the before-state")
    void statusCarriedOverUnchanged() {
      ClaimStateSnapshot before = beforeState();
      // An amendment touching an unrelated field must never affect the (read-only) status.
      ClaimAmendmentPayload payload =
          ClaimAmendmentPayload.builder().feeCode(JsonNullable.of(AMENDED_FEE_CODE)).build();

      ClaimStateSnapshot after = builder.buildPostAmendmentState(before, payload);

      assertThat(after.getStatus()).isEqualTo(ClaimStatus.READY_TO_PROCESS);
      assertThat(after.getStatus()).isEqualTo(before.getStatus());
    }
  }

  @Nested
  @DisplayName("Aggregate building")
  class AggregateBuilding {

    @Test
    @DisplayName("buildAmendmentState bundles before-state, payload and post-amendment state")
    void buildAmendmentStateBundlesEverything() {
      ClaimStateSnapshot before = beforeState();
      ClaimAmendmentPayload payload =
          ClaimAmendmentPayload.builder().feeCode(JsonNullable.of(AMENDED_FEE_CODE)).build();

      ClaimAmendmentState state = builder.buildAmendmentState(before, payload, 1L);

      assertThat(state.getBeforeState()).isSameAs(before);
      assertThat(state.getRequestPayload()).isSameAs(payload);
      assertThat(state.getPostAmendmentState().getFeeCode()).isEqualTo(AMENDED_FEE_CODE);
      // before-state remains untouched (immutability)
      assertThat(before.getFeeCode()).isEqualTo(FEE_CODE);
    }

    @Test
    @DisplayName("AC3: an omitted field stays undefined in the retained request payload")
    void omittedFieldRemainsUndefinedInRequestPayload() {
      ClaimStateSnapshot before = beforeState();
      // caseReferenceNumber is omitted; feeCode is the only submitted change.
      ClaimAmendmentPayload payload =
          ClaimAmendmentPayload.builder().feeCode(JsonNullable.of(AMENDED_FEE_CODE)).build();

      ClaimAmendmentState state = builder.buildAmendmentState(before, payload, 1L);

      // An omitted field must not be recorded as a requested change.
      assertThat(state.getRequestPayload().getCaseReferenceNumber().isPresent()).isFalse();
      // ...while the submitted field is present.
      assertThat(state.getRequestPayload().getFeeCode().isPresent()).isTrue();
    }
  }
}
