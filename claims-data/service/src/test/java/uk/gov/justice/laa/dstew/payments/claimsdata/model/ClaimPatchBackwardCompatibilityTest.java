package uk.gov.justice.laa.dstew.payments.claimsdata.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullableModule;

/**
 * Proves backward compatibility between the legacy {@link ClaimPatch} model (plain nullable fields,
 * {@code openApiNullable = false}) and the new {@link ClaimAmendmentPatch} model (tri-state {@code
 * JsonNullable<T>} fields, {@code openApiNullable = true}).
 *
 * <p>Both models are generated from the same shared claim schema, so they expose the <b>identical
 * JSON wire format</b> (same snake_case property names). The same request body therefore
 * deserializes correctly into either model, and objects round-trip across the two models. The only
 * behavioural difference is the extra capability {@link ClaimAmendmentPatch} adds: distinguishing
 * an omitted field from an explicit {@code null}.
 */
@DisplayName("ClaimPatch <-> ClaimAmendmentPatch backward compatibility")
class ClaimPatchBackwardCompatibilityTest {

  /**
   * A representative request body that is valid for the existing (standard) ClaimPatch contract.
   */
  private static final String STANDARD_PAYLOAD =
      """
      {
        "fee_code": "ABC123",
        "matter_type_code": "MTC",
        "line_number": 5,
        "amendment_requested_by": "PROVIDER"
      }
      """;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    // Mirrors the application's primary ObjectMapper (JacksonMappingConfig): the JsonNullableModule
    // is required for the tri-state model and is harmless for the standard model.
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JsonNullableModule());
  }

  @Test
  @DisplayName("The same JSON body deserializes into ClaimPatch with the expected values")
  void standardPayload_deserializesIntoClaimPatch() throws Exception {
    ClaimPatch patch = objectMapper.readValue(STANDARD_PAYLOAD, ClaimPatch.class);

    assertThat(patch.getFeeCode()).isEqualTo("ABC123");
    assertThat(patch.getMatterTypeCode()).isEqualTo("MTC");
    assertThat(patch.getLineNumber()).isEqualTo(5);
    assertThat(patch.getAmendmentRequestedBy()).isEqualTo("PROVIDER");
  }

  @Test
  @DisplayName("The exact same JSON body deserializes into ClaimAmendmentPatch with equal values")
  void standardPayload_deserializesIntoClaimAmendmentPatch() throws Exception {
    ClaimAmendmentPatch patch = objectMapper.readValue(STANDARD_PAYLOAD, ClaimAmendmentPatch.class);

    // Populated fields expose exactly the same values, just wrapped in JsonNullable.
    assertThat(patch.getFeeCode().get()).isEqualTo("ABC123");
    assertThat(patch.getMatterTypeCode().get()).isEqualTo("MTC");
    assertThat(patch.getLineNumber().get()).isEqualTo(5);
    assertThat(patch.getAmendmentRequestedBy().get()).isEqualTo("PROVIDER");
  }

  @Test
  @DisplayName("Both models expose the identical snake_case JSON wire format")
  void bothModels_shareTheSameWireFormat() throws Exception {
    ClaimPatch legacy = objectMapper.readValue(STANDARD_PAYLOAD, ClaimPatch.class);
    ClaimAmendmentPatch triState =
        objectMapper.readValue(STANDARD_PAYLOAD, ClaimAmendmentPatch.class);

    JsonNode legacyJson = objectMapper.readTree(objectMapper.writeValueAsString(legacy));
    JsonNode triStateJson = objectMapper.readTree(objectMapper.writeValueAsString(triState));

    // Same property names and values on the wire for the fields that were set.
    for (String field :
        new String[] {"fee_code", "matter_type_code", "line_number", "amendment_requested_by"}) {
      assertThat(legacyJson.has(field)).as("ClaimPatch should expose %s", field).isTrue();
      assertThat(triStateJson.has(field))
          .as("ClaimAmendmentPatch should expose %s", field)
          .isTrue();
      assertThat(triStateJson.get(field)).isEqualTo(legacyJson.get(field));
    }
  }

  @Test
  @DisplayName("An object serialized from ClaimAmendmentPatch is readable by ClaimPatch")
  void triStateModel_serialisesToBodyReadableByClaimPatch() throws Exception {
    ClaimAmendmentPatch triState =
        objectMapper.readValue(STANDARD_PAYLOAD, ClaimAmendmentPatch.class);

    String json = objectMapper.writeValueAsString(triState);
    ClaimPatch legacy = objectMapper.readValue(json, ClaimPatch.class);

    assertThat(legacy.getFeeCode()).isEqualTo("ABC123");
    assertThat(legacy.getMatterTypeCode()).isEqualTo("MTC");
    assertThat(legacy.getLineNumber()).isEqualTo(5);
    assertThat(legacy.getAmendmentRequestedBy()).isEqualTo("PROVIDER");
  }

  @Test
  @DisplayName("An object serialized from ClaimPatch is readable by ClaimAmendmentPatch")
  void legacyModel_serialisesToBodyReadableByClaimAmendmentPatch() throws Exception {
    ClaimPatch legacy = objectMapper.readValue(STANDARD_PAYLOAD, ClaimPatch.class);

    String json = objectMapper.writeValueAsString(legacy);
    ClaimAmendmentPatch triState = objectMapper.readValue(json, ClaimAmendmentPatch.class);

    assertThat(triState.getFeeCode().get()).isEqualTo("ABC123");
    assertThat(triState.getMatterTypeCode().get()).isEqualTo("MTC");
    assertThat(triState.getLineNumber().get()).isEqualTo(5);
    assertThat(triState.getAmendmentRequestedBy().get()).isEqualTo("PROVIDER");
  }

  @Test
  @DisplayName("Legacy behaviour is preserved: ClaimPatch cannot distinguish omitted from null")
  void claimPatch_cannotDistinguishOmittedFromNull() throws Exception {
    ClaimPatch omitted = objectMapper.readValue("{}", ClaimPatch.class);
    ClaimPatch explicitNull = objectMapper.readValue("{\"fee_code\": null}", ClaimPatch.class);

    // Both collapse to null - this is exactly the existing, backward-compatible behaviour.
    assertThat(omitted.getFeeCode()).isNull();
    assertThat(explicitNull.getFeeCode()).isNull();
  }

  @Test
  @DisplayName("New capability: ClaimAmendmentPatch DOES distinguish omitted from null")
  void claimAmendmentPatch_distinguishesOmittedFromNull() throws Exception {
    ClaimAmendmentPatch omitted = objectMapper.readValue("{}", ClaimAmendmentPatch.class);
    ClaimAmendmentPatch explicitNull =
        objectMapper.readValue("{\"fee_code\": null}", ClaimAmendmentPatch.class);

    // omitted -> undefined
    assertThat(omitted.getFeeCode().isPresent()).isFalse();

    // explicit null -> present, holding null
    assertThat(explicitNull.getFeeCode().isPresent()).isTrue();
    assertThat(explicitNull.getFeeCode().get()).isNull();
  }
}
