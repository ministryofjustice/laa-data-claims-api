package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.openapitools.jackson.nullable.JsonNullableModule;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ChangeSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;

/**
 * Tests for {@link AmendmentJsonWriter}, focusing on the presence semantics of the {@code
 * request_payload} JSON and the stable key shape of the {@code diff} JSON.
 */
@DisplayName("AmendmentJsonWriter Tests")
class AmendmentJsonWriterTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .registerModule(new JavaTimeModule())
          .registerModule(new JsonNullableModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final AmendmentJsonWriter writer = new AmendmentJsonWriter(objectMapper);

  @Test
  @DisplayName("request_payload keeps an explicit null but drops an omitted field")
  void requestPayloadPreservesPresence() {
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .feeCode(JsonNullable.of("NEW_FEE")) // value present
            .clientSurname(JsonNullable.of(null)) // explicit null (requested clear)
            // scheduleReference left undefined (omitted)
            .build();

    String json = writer.writeRequestPayload(payload);

    assertThat(json).contains("\"feeCode\":\"NEW_FEE\"");
    assertThat(json).contains("\"clientSurname\":null");
    assertThat(json).doesNotContain("scheduleReference");
  }

  @Test
  @DisplayName("diff serialises with stable snake_case keys and labels")
  void diffSerialisesWithStableKeys() {
    AmendmentDiff diff =
        AmendmentDiff.of(
            List.of(new DiffEntry("claim.feeCode", ChangeSource.REQUESTED, "OLD", "NEW")));

    String json = writer.writeDiff(diff);

    assertThat(json)
        .contains("\"schema_version\":1")
        .contains("\"field_identifier\":\"claim.feeCode\"")
        .contains("\"change_source\":\"Requested\"")
        .contains("\"before\":\"OLD\"")
        .contains("\"after\":\"NEW\"");
  }

  @Test
  @DisplayName("a cleared value serialises as an explicit JSON null")
  void clearedValueSerialisesAsNull() {
    AmendmentDiff diff =
        AmendmentDiff.of(List.of(new DiffEntry("claim.feeCode", ChangeSource.FSP, "OLD", null)));

    String json = writer.writeDiff(diff);

    assertThat(json)
        .contains("\"change_source\":\"FSP\"")
        .contains("\"before\":\"OLD\"")
        .contains("\"after\":null");
  }
}
