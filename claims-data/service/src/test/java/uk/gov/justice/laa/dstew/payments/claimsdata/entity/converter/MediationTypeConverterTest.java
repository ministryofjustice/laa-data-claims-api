package uk.gov.justice.laa.dstew.payments.claimsdata.entity.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MediationType;

class MediationTypeConverterTest {

  private final MediationTypeConverter converter = new MediationTypeConverter();

  @Test
  @DisplayName("convertToDatabaseColumn should return the enum display value and be reversible")
  void convertToDatabaseColumn_and_back_isReversible() {
    for (MediationType mt : MediationType.values()) {
      String dbValue = converter.convertToDatabaseColumn(mt);
      // database representation must be non-null for non-null enum
      assertThat(dbValue).isNotNull();

      MediationType restored = converter.convertToEntityAttribute(dbValue);
      assertThat(restored).isEqualTo(mt);
    }
  }

  @Test
  @DisplayName("convertToDatabaseColumn and convertToEntityAttribute handle null gracefully")
  void nullHandling() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }
}
