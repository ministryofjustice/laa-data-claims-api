package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.fee.scheme.model.BoltOnType;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FeeSchemeMapperTest {

  // Instantiate the generated MapStruct implementation directly (consistent with other mapper tests)
  private final FeeSchemeMapper mapper = new FeeSchemeMapperImpl();

  @Test
  @DisplayName("mapAllFieldsWhenAllInputPresent")
  void mapAllFieldsWhenAllInputPresent() {
    UUID claimUuid = UUID.randomUUID();

    ClaimStateSnapshot claim = ClaimStateSnapshot.builder()
        .feeCode("FEE1")
        .claimId(claimUuid)
        .caseStartDate(LocalDate.of(2020, Month.JANUARY, 1))
        .netProfitCostsAmount(BigDecimal.valueOf(100.5))
        .netDisbursementAmount(BigDecimal.valueOf(20.0))
        .netCounselCostsAmount(BigDecimal.valueOf(30.0))
        .disbursementsVatAmount(BigDecimal.valueOf(4.0))
        .isVatApplicable(true)
        .priorAuthorityReference("PA-123")
        .policeStationCourtPrisonId("PS-1")
        .schemeId("SCH-1")
        .detentionTravelWaitingCostsAmount(BigDecimal.valueOf(5.0))
        .caseConcludedDate(LocalDate.of(2020, Month.FEBRUARY, 1))
        .uniqueFileNumber("UFN")
        .mediationSessionsCount(2)
        .jrFormFillingAmount(BigDecimal.valueOf(1.5))
        .isLondonRate(true)
        .adjournedHearingFeeAmount(3)
        .cmrhOralCount(4)
        .cmrhTelephoneCount(5)
        .hoInterview(6)
        .isSubstantiveHearing(true)
        .travelWaitingCostsAmount(BigDecimal.valueOf(7.0))
        .netWaitingCostsAmount(BigDecimal.valueOf(8.0))
        .travelTime(10)
        .waitingTime(11)
        .representationOrderDate(LocalDate.of(2020, Month.MARCH, 1))
        .build();

    FeeCalculationRequest actual = mapper.mapToFeeCalculationRequest(claim, AreaOfLaw.MEDIATION);

    assertThat(actual.getFeeCode()).isEqualTo("FEE1");
    // claimId mapping in the mapper uses source = "id" - ensure behaviour is as expected
    assertThat(actual.getClaimId()).isEqualTo(claimUuid.toString());
    assertThat(actual.getStartDate()).isEqualTo(LocalDate.of(2020, Month.JANUARY, 1));
    assertThat(actual.getNetProfitCosts()).isEqualTo(100.5);
    assertThat(actual.getNetDisbursementAmount()).isEqualTo(20.0);
    assertThat(actual.getNetCostOfCounsel()).isEqualTo(30.0);
    assertThat(actual.getDisbursementVatAmount()).isEqualTo(4.0);
    assertThat(actual.getVatIndicator()).isTrue();
    assertThat(actual.getImmigrationPriorAuthorityNumber()).isEqualTo("PA-123");
    assertThat(actual.getPoliceStationId()).isEqualTo("PS-1");
    assertThat(actual.getPoliceStationSchemeId()).isEqualTo("SCH-1");
    assertThat(actual.getDetentionTravelAndWaitingCosts()).isEqualTo(5.0);
    assertThat(actual.getCaseConcludedDate()).isEqualTo(LocalDate.of(2020, Month.FEBRUARY, 1));
    assertThat(actual.getUniqueFileNumber()).isEqualTo("UFN");
    assertThat(actual.getNumberOfMediationSessions()).isEqualTo(2);
    assertThat(actual.getJrFormFilling()).isEqualTo(1.5);
    assertThat(actual.getLondonRate()).isTrue();

    // Mapper does not set travelAndWaitingCosts/netTravelCosts/netWaitingCosts in default (MEDIATION) branch
    assertThat(actual.getTravelAndWaitingCosts()).isNull();
    assertThat(actual.getNetTravelCosts()).isNull();
    assertThat(actual.getNetWaitingCosts()).isNull();

    // Bolt on block should be mapped
    BoltOnType boltOns = actual.getBoltOns();
    assertThat(boltOns).isNotNull();
    assertThat(boltOns.getBoltOnAdjournedHearing()).isEqualTo(3);
    assertThat(boltOns.getBoltOnCmrhOral()).isEqualTo(4);
    assertThat(boltOns.getBoltOnCmrhTelephone()).isEqualTo(5);
    assertThat(boltOns.getBoltOnHomeOfficeInterview()).isEqualTo(6);
    assertThat(boltOns.getBoltOnSubstantiveHearing()).isTrue();
  }

  @Test
  @DisplayName("mapConditionalFieldWhenAreaIsCrimeLower")
  void mapConditionalFieldWhenAreaIsCrimeLower() {
    ClaimStateSnapshot claim = ClaimStateSnapshot.builder()
        .feeCode("FEE2")
        .travelWaitingCostsAmount(BigDecimal.valueOf(12.25))
        .netWaitingCostsAmount(BigDecimal.valueOf(4.75))
        .build();

    FeeCalculationRequest actual = mapper.mapToFeeCalculationRequest(claim, AreaOfLaw.CRIME_LOWER);

    assertThat(actual.getNetTravelCosts()).isEqualTo(12.25);
    assertThat(actual.getNetWaitingCosts()).isEqualTo(4.75);
  }

  @Test
  @DisplayName("mapConditionalFieldWhenAreaIsLegalHelp")
  void mapConditionalFieldWhenAreaIsLegalHelp() {
    ClaimStateSnapshot claim = ClaimStateSnapshot.builder()
        .feeCode("FEE3")
        .travelWaitingCostsAmount(BigDecimal.valueOf(21.5))
        .build();

    FeeCalculationRequest actual = mapper.mapToFeeCalculationRequest(claim, AreaOfLaw.LEGAL_HELP);

    assertThat(actual.getTravelAndWaitingCosts()).isEqualTo(21.5);
  }

  @Test
  @DisplayName("mapWithNullOptionalFields")
  void mapWithNullOptionalFields() {
    ClaimStateSnapshot claim = ClaimStateSnapshot.builder()
        .feeCode("FEE4")
        .build();

    FeeCalculationRequest actual = mapper.mapToFeeCalculationRequest(claim, AreaOfLaw.MEDIATION);

    assertThat(actual.getNetProfitCosts()).isNull();
    assertThat(actual.getNetDisbursementAmount()).isNull();
    assertThat(actual.getNetCostOfCounsel()).isNull();
    // BoltOns should not be null (mapper will produce an instance), but its fields will be null
    assertThat(actual.getBoltOns()).isNotNull();
  }

  @Test
  @DisplayName("mapPrecisionBigDecimalConversion")
  void mapPrecisionBigDecimalConversion() {
    ClaimStateSnapshot claim = ClaimStateSnapshot.builder()
        .feeCode("FEE5")
        .netProfitCostsAmount(new BigDecimal("100.123456789"))
        .build();

    FeeCalculationRequest actual = mapper.mapToFeeCalculationRequest(claim, AreaOfLaw.MEDIATION);

    assertThat(actual.getNetProfitCosts()).isEqualTo(claim.getNetProfitCostsAmount().doubleValue());
  }

  @Test
  @DisplayName("mapBoltOnsWhenFieldsNull")
  void mapBoltOnsWhenFieldsNull() {
    ClaimStateSnapshot claim = ClaimStateSnapshot.builder().feeCode("FEE6").build();

    FeeCalculationRequest actual = mapper.mapToFeeCalculationRequest(claim, AreaOfLaw.MEDIATION);

    BoltOnType boltOns = actual.getBoltOns();
    assertThat(boltOns).isNotNull();
    assertThat(boltOns.getBoltOnAdjournedHearing()).isNull();
    assertThat(boltOns.getBoltOnCmrhOral()).isNull();
    assertThat(boltOns.getBoltOnCmrhTelephone()).isNull();
    assertThat(boltOns.getBoltOnHomeOfficeInterview()).isNull();
    assertThat(boltOns.getBoltOnSubstantiveHearing()).isNull();
  }

  @Test
  @DisplayName("mapBoltOnsWhenZerosAndFalse")
  void mapBoltOnsWhenZerosAndFalse() {
    ClaimStateSnapshot claim = ClaimStateSnapshot.builder()
        .feeCode("FEE7")
        .adjournedHearingFeeAmount(0)
        .cmrhOralCount(0)
        .cmrhTelephoneCount(0)
        .hoInterview(0)
        .isSubstantiveHearing(false)
        .build();

    FeeCalculationRequest actual = mapper.mapToFeeCalculationRequest(claim, AreaOfLaw.MEDIATION);

    BoltOnType boltOns = actual.getBoltOns();
    assertThat(boltOns.getBoltOnAdjournedHearing()).isZero();
    assertThat(boltOns.getBoltOnCmrhOral()).isZero();
    assertThat(boltOns.getBoltOnCmrhTelephone()).isZero();
    assertThat(boltOns.getBoltOnHomeOfficeInterview()).isZero();
    assertThat(boltOns.getBoltOnSubstantiveHearing()).isFalse();
  }

  @Test
  @DisplayName("mapDefaultLondonRateWhenNull")
  void mapDefaultLondonRateWhenNull() {
    ClaimStateSnapshot claim = ClaimStateSnapshot.builder().feeCode("FEE8").build();

    FeeCalculationRequest actual = mapper.mapToFeeCalculationRequest(claim, AreaOfLaw.MEDIATION);

    // Mapping sets defaultValue = "false" for londonRate, so null source should map to false
    assertThat(actual.getLondonRate()).isFalse();
  }
}



