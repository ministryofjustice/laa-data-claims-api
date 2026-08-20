package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.fee.scheme.model.BoltOnType;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationRequest;

class FeeSchemeMapperNegativeTest {

  private final FeeSchemeMapper mapper = Mappers.getMapper(FeeSchemeMapper.class);

  @Test
  @DisplayName("Should safely map null travel/waiting amounts for CRIME_LOWER without throwing")
  void map_nullTravelAndWaitingForCrimeLower_mapsToNullFields() {
    ClaimStateSnapshot claim =
        ClaimStateSnapshot.builder()
            .feeCode("FEE1")
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .travelWaitingCostsAmount(null)
            .netWaitingCostsAmount(null)
            .build();

    FeeCalculationRequest req = mapper.mapToFeeCalculationRequest(claim, AreaOfLaw.CRIME_LOWER);

    assertThat(req).isNotNull();
    assertThat(req.getNetTravelCosts()).isNull();
    assertThat(req.getNetWaitingCosts()).isNull();
  }

  @Test
  @DisplayName("Should safely map null travel/waiting amounts for LEGAL_HELP without throwing")
  void map_nullTravelForLegalHelp_mapsToNullField() {
    ClaimStateSnapshot claim =
        ClaimStateSnapshot.builder()
            .feeCode("FEE2")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .travelWaitingCostsAmount(null)
            .build();

    FeeCalculationRequest req = mapper.mapToFeeCalculationRequest(claim, AreaOfLaw.LEGAL_HELP);

    assertThat(req).isNotNull();
    assertThat(req.getTravelAndWaitingCosts()).isNull();
  }

  @Test
  @DisplayName("Should build BoltOnType even when nested bolt-on fields are null")
  void map_nullBoltOnFields_buildsBoltOnTypeWithNulls() {
    ClaimStateSnapshot claim =
        ClaimStateSnapshot.builder()
            .feeCode("FEE3")
            .adjournedHearingFeeAmount(null)
            .cmrhOralCount(null)
            .cmrhTelephoneCount(null)
            .hoInterview(null)
            .isSubstantiveHearing(null)
            .areaOfLaw(AreaOfLaw.MEDIATION)
            .build();

    FeeCalculationRequest req = mapper.mapToFeeCalculationRequest(claim, AreaOfLaw.MEDIATION);

    assertThat(req).isNotNull();
    BoltOnType boltOns = req.getBoltOns();
    assertThat(boltOns).isNotNull();
    assertThat(boltOns.getBoltOnAdjournedHearing()).isNull();
    assertThat(boltOns.getBoltOnCmrhOral()).isNull();
    assertThat(boltOns.getBoltOnCmrhTelephone()).isNull();
    assertThat(boltOns.getBoltOnHomeOfficeInterview()).isNull();
    assertThat(boltOns.getBoltOnSubstantiveHearing()).isNull();
  }

  @Test
  @DisplayName("Should convert BigDecimal precision into Double accurately")
  void map_bigDecimalPrecision_preservedAsDouble() {
    ClaimStateSnapshot claim =
        ClaimStateSnapshot.builder()
            .feeCode("FEE4")
            .netProfitCostsAmount(java.math.BigDecimal.valueOf(123.456))
            .areaOfLaw(AreaOfLaw.MEDIATION)
            .build();

    FeeCalculationRequest req = mapper.mapToFeeCalculationRequest(claim, AreaOfLaw.MEDIATION);

    assertThat(req).isNotNull();
    assertThat(req.getNetProfitCosts()).isEqualTo(123.456);
  }

  @Test
  @DisplayName("Should throw NullPointerException when areaOfLaw context is null")
  void map_nullAreaOfLaw_throwsNpe() {
    ClaimStateSnapshot claim = ClaimStateSnapshot.builder().feeCode("FEE5").build();

    assertThatThrownBy(() -> mapper.mapToFeeCalculationRequest(claim, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should return null when claim is null for main mapper")
  void map_nullClaim_returnsNullForMainMapper() {
    FeeCalculationRequest req = mapper.mapToFeeCalculationRequest(null, AreaOfLaw.MEDIATION);
    assertThat(req).isNull();
  }

  @Test
  @DisplayName("Should return null when claim is null for bolt-on mapper")
  void map_nullClaim_returnsNullForBoltOn() {
    assertThat(mapper.mapToBoltOnType(null)).isNull();
  }
}
