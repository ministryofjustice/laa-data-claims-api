package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentRequestedByReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.UUID7;

@DisplayName("AmendmentReferenceMapper")
class AmendmentReferenceMapperTest {

  private final AmendmentReferenceMapper mapper = new AmendmentReferenceMapperImpl();

  @Nested
  @DisplayName("null handling")
  class NullHandling {

    @Test
    @DisplayName("toRequestedByModel returns null for null input")
    void toRequestedByModel_nullInput_returnsNull() {
      assertNull(mapper.toRequestedByModel(null));
    }

    @Test
    @DisplayName("toReasonModel returns null for null input")
    void toReasonModel_nullInput_returnsNull() {
      assertNull(mapper.toReasonModel(null));
    }
  }

  @Nested
  @DisplayName("field mapping")
  class FieldMapping {

    @Test
    @DisplayName("toRequestedByModel maps code, label and display order")
    void toRequestedByModel_mapsCodeLabelAndOrder() {
      RequestedByReferenceEntity entity =
          RequestedByReferenceEntity.builder()
              .id(UUID7.timeBasedUuid())
              .code("PROVIDER")
              .displayLabel("Provider")
              .isActive(true)
              .displayOrder(10)
              .createdByUserId("actor")
              .createdOn(Instant.now())
              .build();

      AmendmentRequestedByReference model = mapper.toRequestedByModel(entity);

      assertThat(model.getCode()).isEqualTo("PROVIDER");
      assertThat(model.getDisplayLabel()).isEqualTo("Provider");
      assertThat(model.getDisplayOrder()).isEqualTo(10);
    }

    @Test
    @DisplayName("toReasonModel maps code, label and display order")
    void toReasonModel_mapsCodeLabelAndOrder() {
      AmendmentReasonReferenceEntity entity =
          AmendmentReasonReferenceEntity.builder()
              .id(UUID7.timeBasedUuid())
              .requestedByCode("PROVIDER")
              .code("PROVIDER_ERROR")
              .displayLabel("Provider Error")
              .isActive(true)
              .displayOrder(10)
              .createdByUserId("actor")
              .createdOn(Instant.now())
              .build();

      var model = mapper.toReasonModel(entity);

      assertThat(model.getCode()).isEqualTo("PROVIDER_ERROR");
      assertThat(model.getDisplayLabel()).isEqualTo("Provider Error");
      assertThat(model.getDisplayOrder()).isEqualTo(10);
    }
  }
}
