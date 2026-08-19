package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.FIELD_BEFORE_STATE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.FIELD_CASE_REFERENCE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.FIELD_CLAIM_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.FIELD_HAS_ASSESSMENT;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.FIELD_POST_AMENDMENT_STATE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.FIELD_REQUEST_PAYLOAD;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.FIELD_SCHEDULE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.FIELD_UNIQUE_FILE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.SCHEDULE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.UPDATED_SCHEDULE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Integration tests that verify the tri-state ({@link JsonNullable}) serialisation and
 * deserialisation behaviour using the application's Spring-configured {@link ObjectMapper}.
 *
 * <p>This guards the wiring the amendment-history JSONB columns depend on: that the {@code
 * JsonNullableModule} is registered on the primary mapper and that {@code JsonInclude.NON_ABSENT}
 * drops omitted fields while preserving an explicit null. Unit tests can't catch a missing module
 * registration on the real bean - this can.
 *
 * <ul>
 *   <li>omitted &rarr; field absent from JSON;
 *   <li>explicit null &rarr; {@code "field": null};
 *   <li>value &rarr; serialised normally;
 *   <li>a fully-omitted payload serialises to an empty object;
 *   <li>the omitted, explicit-null and value distinctions survive a serialise/deserialise round
 *       trip;
 *   <li>the before/after snapshot and the amendment aggregate serialise to the expected shape.
 * </ul>
 */
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Claim amendment Jackson tri-state Integration Test")
class ClaimAmendmentJacksonIntegrationTest extends AbstractIntegrationTest {

  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName("Omitted fields are dropped, explicit null is kept, values are serialised")
  void payload_serialises_omittedNullAndValueCorrectly() throws Exception {
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .scheduleReference(JsonNullable.of(SCHEDULE_REFERENCE)) // value
            .uniqueFileNumber(JsonNullable.of((String) null)) // explicit clear
            // caseReferenceNumber omitted (undefined)
            .build();

    JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(payload));

    assertThat(node.has(FIELD_SCHEDULE_REFERENCE)).isTrue();
    assertThat(node.get(FIELD_SCHEDULE_REFERENCE).asText()).isEqualTo(SCHEDULE_REFERENCE);

    assertThat(node.has(FIELD_UNIQUE_FILE_NUMBER)).isTrue();
    assertThat(node.get(FIELD_UNIQUE_FILE_NUMBER).isNull()).isTrue();

    assertThat(node.has(FIELD_CASE_REFERENCE_NUMBER)).isFalse();
  }

  @Test
  @DisplayName("A fully-omitted payload serialises to an empty object")
  void payload_fullyOmitted_serialisesToEmptyObject() throws Exception {
    ClaimAmendmentPayload payload = ClaimAmendmentPayload.builder().build();

    JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(payload));

    assertThat(node.isObject()).isTrue();
    assertThat(node.isEmpty()).isTrue();
  }

  @Test
  @DisplayName("Tri-state distinctions survive a serialise/deserialise round trip")
  void payload_roundTrip_preservesOmittedNullAndValue() throws Exception {
    ClaimAmendmentPayload original =
        ClaimAmendmentPayload.builder()
            .scheduleReference(JsonNullable.of(SCHEDULE_REFERENCE)) // value
            .uniqueFileNumber(JsonNullable.of((String) null)) // explicit clear
            // caseReferenceNumber omitted (undefined)
            .build();

    String json = objectMapper.writeValueAsString(original);
    ClaimAmendmentPayload restored = objectMapper.readValue(json, ClaimAmendmentPayload.class);

    // value preserved
    assertThat(restored.getScheduleReference().isPresent()).isTrue();
    assertThat(restored.getScheduleReference().get()).isEqualTo(SCHEDULE_REFERENCE);

    // explicit null preserved (present, holding null)
    assertThat(restored.getUniqueFileNumber().isPresent()).isTrue();
    assertThat(restored.getUniqueFileNumber().get()).isNull();

    // omitted restored as undefined (not present), thanks to @Builder.Default via @Jacksonized
    assertThat(restored.getCaseReferenceNumber().isPresent()).isFalse();
  }

  @Test
  @DisplayName("Before-state snapshot serialises with a stable hasAssessment property")
  void snapshot_serialises_withStableHasAssessmentProperty() throws Exception {
    ClaimStateSnapshot snapshot =
        ClaimStateSnapshot.builder()
            .claimId(CLAIM_1_ID)
            .scheduleReference(SCHEDULE_REFERENCE)
            .status(ClaimStatus.READY_TO_PROCESS)
            .hasAssessment(true)
            .build();

    JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(snapshot));

    assertThat(node.has(FIELD_HAS_ASSESSMENT)).isTrue();
    assertThat(node.get(FIELD_HAS_ASSESSMENT).asBoolean()).isTrue();
    assertThat(node.get(FIELD_SCHEDULE_REFERENCE).asText()).isEqualTo(SCHEDULE_REFERENCE);
    assertThat(node.get(FIELD_CLAIM_ID).asText()).isEqualTo(CLAIM_1_ID.toString());
  }

  @Test
  @DisplayName("Amendment aggregate serialises to the before/payload/after diff shape")
  void amendmentState_serialises_withBeforePayloadAndAfter() throws Exception {
    ClaimStateSnapshot before =
        ClaimStateSnapshot.builder()
            .claimId(CLAIM_1_ID)
            .scheduleReference(SCHEDULE_REFERENCE)
            .build();
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .scheduleReference(JsonNullable.of(UPDATED_SCHEDULE_REFERENCE))
            .build();
    ClaimStateSnapshot after =
        before.toBuilder().scheduleReference(UPDATED_SCHEDULE_REFERENCE).build();

    ClaimAmendmentState state =
        ClaimAmendmentState.builder()
            .beforeState(before)
            .requestPayload(payload)
            .postAmendmentState(after)
            .build();

    JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(state));

    assertThat(node.has(FIELD_BEFORE_STATE)).isTrue();
    assertThat(node.has(FIELD_REQUEST_PAYLOAD)).isTrue();
    assertThat(node.has(FIELD_POST_AMENDMENT_STATE)).isTrue();
    assertThat(node.get(FIELD_BEFORE_STATE).get(FIELD_SCHEDULE_REFERENCE).asText())
        .isEqualTo(SCHEDULE_REFERENCE);
    assertThat(node.get(FIELD_REQUEST_PAYLOAD).get(FIELD_SCHEDULE_REFERENCE).asText())
        .isEqualTo(UPDATED_SCHEDULE_REFERENCE);
    assertThat(node.get(FIELD_POST_AMENDMENT_STATE).get(FIELD_SCHEDULE_REFERENCE).asText())
        .isEqualTo(UPDATED_SCHEDULE_REFERENCE);
  }
}
