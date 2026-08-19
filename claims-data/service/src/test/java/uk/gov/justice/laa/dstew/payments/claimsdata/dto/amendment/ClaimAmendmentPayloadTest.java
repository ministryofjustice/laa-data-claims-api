package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CASE_START_DATE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.FIELD_CASE_REFERENCE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.FIELD_SCHEDULE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.FIELD_UNIQUE_FILE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.LINE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.NET_PROFIT_COSTS_AMOUNT;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.SCHEDULE_REFERENCE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.openapitools.jackson.nullable.JsonNullableModule;

/**
 * Unit tests for {@link ClaimAmendmentPayload} Jackson behaviour.
 *
 * <p>Uses a stand-alone {@link ObjectMapper} configured exactly like the application bean
 * (registers {@code JsonNullableModule} and {@code JavaTimeModule}) so the tri-state contract is
 * verified in isolation, independently of the Spring context. The integration test {@code
 * ClaimAmendmentJacksonIntegrationTest} additionally proves the real primary bean is wired the same
 * way.
 */
@DisplayName("ClaimAmendmentPayload Jackson Test")
class ClaimAmendmentPayloadTest {

  private static final String EXPLICIT_NULL_JSON = "{\"" + FIELD_SCHEDULE_REFERENCE + "\":null}";

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.registerModule(new JsonNullableModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Nested
  @DisplayName("Serialisation")
  class Serialisation {

    @Test
    @DisplayName("Drops omitted fields, keeps explicit null, writes values")
    void serialises_omittedNullAndValue() throws Exception {
      ClaimAmendmentPayload payload =
          ClaimAmendmentPayload.builder()
              .scheduleReference(JsonNullable.of(SCHEDULE_REFERENCE)) // value
              .uniqueFileNumber(JsonNullable.of((String) null)) // explicit clear
              // caseReferenceNumber omitted (undefined)
              .build();

      JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(payload));

      assertThat(node.get(FIELD_SCHEDULE_REFERENCE).asText()).isEqualTo(SCHEDULE_REFERENCE);
      assertThat(node.has(FIELD_UNIQUE_FILE_NUMBER)).isTrue();
      assertThat(node.get(FIELD_UNIQUE_FILE_NUMBER).isNull()).isTrue();
      assertThat(node.has(FIELD_CASE_REFERENCE_NUMBER)).isFalse();
    }

    @Test
    @DisplayName("A fully-omitted payload serialises to an empty object")
    void serialises_fullyOmittedToEmptyObject() throws Exception {
      ClaimAmendmentPayload payload = ClaimAmendmentPayload.builder().build();

      JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(payload));

      assertThat(node.isObject()).isTrue();
      assertThat(node.isEmpty()).isTrue();
    }
  }

  @Nested
  @DisplayName("Deserialisation / round trip")
  class Deserialisation {

    @Test
    @DisplayName("Preserves omitted, explicit null and value distinctions")
    void roundTrip_preservesTriState() throws Exception {
      ClaimAmendmentPayload original =
          ClaimAmendmentPayload.builder()
              .scheduleReference(JsonNullable.of(SCHEDULE_REFERENCE)) // value
              .uniqueFileNumber(JsonNullable.of((String) null)) // explicit clear
              // caseReferenceNumber omitted (undefined)
              .build();

      ClaimAmendmentPayload restored =
          objectMapper.readValue(
              objectMapper.writeValueAsString(original), ClaimAmendmentPayload.class);

      assertThat(restored.getScheduleReference().isPresent()).isTrue();
      assertThat(restored.getScheduleReference().get()).isEqualTo(SCHEDULE_REFERENCE);

      assertThat(restored.getUniqueFileNumber().isPresent()).isTrue();
      assertThat(restored.getUniqueFileNumber().get()).isNull();

      assertThat(restored.getCaseReferenceNumber().isPresent()).isFalse();
    }

    @Test
    @DisplayName("Reads explicit null from raw JSON as a present, null-holding value")
    void readsExplicitNullFromRawJson() throws Exception {
      ClaimAmendmentPayload restored =
          objectMapper.readValue(EXPLICIT_NULL_JSON, ClaimAmendmentPayload.class);

      assertThat(restored.getScheduleReference().isPresent()).isTrue();
      assertThat(restored.getScheduleReference().get()).isNull();
      // a property simply absent from the JSON stays undefined
      assertThat(restored.getUniqueFileNumber().isPresent()).isFalse();
    }

    @Test
    @DisplayName("Round-trips typed values (date, integer, decimal, boolean)")
    void roundTrip_preservesTypedValues() throws Exception {
      ClaimAmendmentPayload original =
          ClaimAmendmentPayload.builder()
              .caseStartDate(JsonNullable.of(CASE_START_DATE))
              .lineNumber(JsonNullable.of(LINE_NUMBER))
              .netProfitCostsAmount(JsonNullable.of(NET_PROFIT_COSTS_AMOUNT))
              .isVatApplicable(JsonNullable.of(true))
              .build();

      ClaimAmendmentPayload restored =
          objectMapper.readValue(
              objectMapper.writeValueAsString(original), ClaimAmendmentPayload.class);

      assertThat(restored.getCaseStartDate().get()).isEqualTo(CASE_START_DATE);
      assertThat(restored.getLineNumber().get()).isEqualTo(LINE_NUMBER);
      assertThat(restored.getNetProfitCostsAmount().get())
          .isEqualByComparingTo(NET_PROFIT_COSTS_AMOUNT);
      assertThat(restored.getIsVatApplicable().get()).isTrue();
    }
  }
}
