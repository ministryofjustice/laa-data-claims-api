package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MediationType;

@ExtendWith(MockitoExtension.class)
class MatterStartMapperTest {

  @InjectMocks private MatterStartMapper mapper = new MatterStartMapperImpl();

  @Spy private GlobalStringMapper globalStringMapper = new GlobalStringMapperImpl();

  @Test
  void toMatterStart_null_returnsNull() {
    assertNull(mapper.toMatterStart(null));
  }

  @Nested
  @DisplayName("toMatterStart tests")
  class ToMatterStart {

    @Test
    void toMatterStart_mapsAllFields() {
      final MatterStartPost request =
          new MatterStartPost()
              .scheduleReference("SCH-001")
              .categoryCode("CAT-A")
              .procurementAreaCode("PA-10")
              .accessPointCode("AP-01")
              .deliveryLocation("DL-XYZ")
              .mediationType(MediationType.MDAC_ALL_ISSUES_CO);

      final MatterStart result = mapper.toMatterStart(request);

      assertNotNull(result);

      SoftAssertions.assertSoftly(
          softly -> {
            softly.assertThat(result.getScheduleReference()).isEqualTo("SCH-001");
            softly.assertThat(result.getCategoryCode()).isEqualTo("CAT-A");
            softly.assertThat(result.getProcurementAreaCode()).isEqualTo("PA-10");
            softly.assertThat(result.getAccessPointCode()).isEqualTo("AP-01");
            softly.assertThat(result.getDeliveryLocation()).isEqualTo("DL-XYZ");
            softly
                .assertThat(result.getMediationType())
                .isEqualTo(MediationType.MDAC_ALL_ISSUES_CO);
          });
    }
  }

  @Nested
  @DisplayName("toMatterStartGet tests")
  class ToMatterStartGet {

    @Test
    void toMatterStart_mapsAllFields() {

      final MatterStart request =
          MatterStart.builder()
              .scheduleReference("SCH-001")
              .categoryCode("CAT-A")
              .procurementAreaCode("PA-10")
              .accessPointCode("AP-01")
              .deliveryLocation("DL-XYZ")
              .mediationType(MediationType.MDAC_ALL_ISSUES_CO)
              .build();

      final MatterStartGet result = mapper.toMatterStartGet(request);

      assertNotNull(result);
      SoftAssertions.assertSoftly(
          softly -> {
            softly.assertThat(result.getScheduleReference()).isEqualTo("SCH-001");
            softly.assertThat(result.getCategoryCode()).isEqualTo("CAT-A");
            softly.assertThat(result.getProcurementAreaCode()).isEqualTo("PA-10");
            softly.assertThat(result.getAccessPointCode()).isEqualTo("AP-01");
            softly.assertThat(result.getDeliveryLocation()).isEqualTo("DL-XYZ");
            softly
                .assertThat(result.getMediationType())
                .isEqualTo(MediationType.MDAC_ALL_ISSUES_CO);
          });
    }
  }
}
