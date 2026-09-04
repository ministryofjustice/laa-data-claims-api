package uk.gov.justice.laa.dstew.payments.claimsdata.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullableModule;

/**
 * Proves that the generated {@link ClaimAmendmentPatch} model preserves true PATCH tri-state
 * semantics via {@code JsonNullable<T>}.
 *
 * <p>The model is produced by the dedicated {@code openApiGenerateAmendment} Gradle task ({@code
 * openApiNullable = true}) so it can distinguish:
 *
 * <ul>
 *   <li>omitted &rarr; {@code JsonNullable.undefined()};
 *   <li>explicit null &rarr; {@code JsonNullable.of(null)};
 *   <li>populated value &rarr; {@code JsonNullable.of(value)}.
 * </ul>
 */
@DisplayName("ClaimAmendmentPatch JsonNullable tri-state")
class ClaimAmendmentPatchTriStateTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JsonNullableModule());
  }

  @Test
  @DisplayName("Omitted field -> JsonNullable.undefined() (not present)")
  void omittedField_isUndefined() throws Exception {
    ClaimAmendmentPatch patch = objectMapper.readValue("{}", ClaimAmendmentPatch.class);

    assertThat(patch.getFeeCode().isPresent()).isFalse();
  }

  @Test
  @DisplayName("Explicit null -> JsonNullable.of(null) (present, holding null)")
  void explicitNull_isPresentHoldingNull() throws Exception {
    ClaimAmendmentPatch patch =
        objectMapper.readValue("{\"fee_code\": null}", ClaimAmendmentPatch.class);

    assertThat(patch.getFeeCode().isPresent()).isTrue();
    assertThat(patch.getFeeCode().get()).isNull();
  }

  @Test
  @DisplayName("Populated value -> JsonNullable.of(value)")
  void populatedValue_isPresentWithValue() throws Exception {
    ClaimAmendmentPatch patch =
        objectMapper.readValue("{\"fee_code\": \"ABC123\"}", ClaimAmendmentPatch.class);

    assertThat(patch.getFeeCode().isPresent()).isTrue();
    assertThat(patch.getFeeCode().get()).isEqualTo("ABC123");
  }

  @Test
  @DisplayName("Omitted and explicit-null are distinguishable on the same model")
  void omittedAndExplicitNull_areDistinguishable() throws Exception {
    ClaimAmendmentPatch patch =
        objectMapper.readValue(
            "{\"fee_code\": null, \"matter_type_code\": \"MTC\"}", ClaimAmendmentPatch.class);

    // explicitly cleared
    assertThat(patch.getFeeCode().isPresent()).isTrue();
    assertThat(patch.getFeeCode().get()).isNull();

    // populated
    assertThat(patch.getMatterTypeCode().isPresent()).isTrue();
    assertThat(patch.getMatterTypeCode().get()).isEqualTo("MTC");

    // omitted
    assertThat(patch.getCaseReferenceNumber().isPresent()).isFalse();
  }
}
