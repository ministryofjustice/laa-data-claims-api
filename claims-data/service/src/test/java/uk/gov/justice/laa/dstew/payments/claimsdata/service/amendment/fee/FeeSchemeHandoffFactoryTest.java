package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ResolvedClaimData;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimMapperImpl;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.GlobalDateTimeMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.GlobalDateTimeMapperImpl;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.GlobalStringMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.GlobalStringMapperImpl;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BoltOnPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.fee.scheme.model.BoltOnFeeDetails;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculation;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;
import uk.gov.justice.laa.fee.scheme.model.FeeDetailsResponseV2;

/**
 * Unit coverage for {@link FeeSchemeHandoffFactory} - the single component that maps a successful
 * FSP repricing response into a storable {@link CalculatedFeeDetail} and establishes the {@code
 * calculated_fee_detail -> claim_amendment} tracking link (DSTEW-1762 / 1595-F).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeeSchemeHandoffFactory (DSTEW-1762 / 1595-F) unit tests")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class FeeSchemeHandoffFactoryTest {

  private static final String AMENDING_USER = "user-1762";

  @InjectMocks private ClaimMapperImpl claimMapper = new ClaimMapperImpl();

  @Spy private GlobalStringMapper globalStringMapper = new GlobalStringMapperImpl();

  @Spy private GlobalDateTimeMapper globalDateTimeMapper = new GlobalDateTimeMapperImpl();

  private FeeSchemeHandoffFactory factory;

  @BeforeEach
  void setUp() {
    factory = new FeeSchemeHandoffFactory(claimMapper, new FeeCalculationMetadataResolver());
  }

  // ---------------------------------------------------------------------------
  // Null / not-repriced inputs
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("returns null (no fee row) when there is nothing to persist")
  class NoFeeRow {

    @Test
    void returnsNull_whenResponseIsNull() {
      Claim claim = claimWithSummaryFee(Instant.now());
      assertThat(factory.prepareCalculatedFeeDetail(claim, state(null), null, amendment()))
          .isNull();
    }

    @Test
    void returnsNull_whenFeeCalculationBlockIsNull() {
      Claim claim = claimWithSummaryFee(Instant.now());
      FeeCalculationResponse response = new FeeCalculationResponse().feeCode("FEE001");

      assertThat(
              factory.prepareCalculatedFeeDetail(
                  claim, state(snapshot(BigDecimal.valueOf(250))), response, amendment()))
          .isNull();
    }
  }

  // ---------------------------------------------------------------------------
  // Happy path - mapping + link
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "maps every supported FSP field plus fee metadata, inherits the amending user and links the amendment")
  void mapsAllFields_andEstablishesAmendmentLink() {
    Claim claim = claimWithSummaryFee(Instant.now());
    ClaimAmendment amendment = amendment();
    ClaimAmendmentState state = state(snapshot(BigDecimal.valueOf(100.00)));
    state.setResolvedClaimDataContext(new ResolvedClaimData("HOURLY", "AREA-A", "CATEGORY-A"));
    state.setFeeSchemeDetailsContext(
        new FeeDetailsResponseV2()
            .feeCodeDescription("Hourly work fee")
            .feeType("HOURLY")
            .categoryOfLawCodes(List.of("CATEGORY-A")));

    FeeCalculationResponse response =
        new FeeCalculationResponse()
            .feeCode("FEE-123")
            .schemeId("SCHEME-TEST")
            .escapeCaseFlag(true)
            .feeCalculation(
                new FeeCalculation()
                    .totalAmount(650.00)
                    .vatRateApplied(20.00)
                    .calculatedVatAmount(108.33)
                    .disbursementAmount(50.25)
                    .requestedNetDisbursementAmount(45.15)
                    .disbursementVatAmount(10.05)
                    .hourlyTotalAmount(250.10)
                    .fixedFeeAmount(75.35)
                    .netProfitCostsAmount(450.00)
                    .requestedNetProfitCostsAmount(400.40)
                    .netCostOfCounselAmount(35.99)
                    .netTravelCostsAmount(12.10)
                    .netWaitingCostsAmount(8.80)
                    .detentionTravelAndWaitingCostsAmount(5.70)
                    .jrFormFillingAmount(18.75)
                    .travelAndWaitingCostAmount(20.90)
                    .vatIndicator(true)
                    .boltOnFeeDetails(
                        new BoltOnFeeDetails()
                            .boltOnTotalFeeAmount(33.45)
                            .boltOnAdjournedHearingCount(1)
                            .boltOnAdjournedHearingFee(11.11)
                            .boltOnCmrhTelephoneCount(2)
                            .boltOnCmrhTelephoneFee(12.12)
                            .boltOnCmrhOralCount(3)
                            .boltOnCmrhOralFee(13.13)
                            .boltOnHomeOfficeInterviewCount(4)
                            .boltOnHomeOfficeInterviewFee(14.14)
                            .boltOnSubstantiveHearingFee(15.15)));

    CalculatedFeeDetail result =
        factory.prepareCalculatedFeeDetail(claim, state, response, amendment);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isNotNull();
    assertThat(result.getClaim()).isSameAs(claim);
    // The tracking link that the history-endpoint FSP flags depend on.
    assertThat(result.getClaimAmendment()).isSameAs(amendment);
    assertThat(result.getCreatedByUserId()).isEqualTo(AMENDING_USER);
    assertThat(result.getCreatedOn()).isNotNull();

    assertThat(result.getFeeCode()).isEqualTo("FEE-123");
    assertThat(result.getFeeType()).isEqualTo(FeeCalculationType.HOURLY);
    assertThat(result.getFeeCodeDescription()).isEqualTo("Hourly work fee");
    assertThat(result.getCategoryOfLaw()).isEqualTo("CATEGORY-A");
    assertThat(result.getSchemeId()).isEqualTo("SCHEME-TEST");
    assertThat(result.getEscapeCaseFlag()).isTrue();
    assertThat(result.getTotalAmount()).isEqualByComparingTo("650.00");
    assertThat(result.getVatRateApplied()).isEqualByComparingTo("20.0");
    assertThat(result.getCalculatedVatAmount()).isEqualByComparingTo("108.33");
    assertThat(result.getDisbursementAmount()).isEqualByComparingTo("50.25");
    assertThat(result.getRequestedNetDisbursementAmount()).isEqualByComparingTo("45.15");
    assertThat(result.getDisbursementVatAmount()).isEqualByComparingTo("10.05");
    assertThat(result.getHourlyTotalAmount()).isEqualByComparingTo("250.10");
    assertThat(result.getFixedFeeAmount()).isEqualByComparingTo("75.35");
    assertThat(result.getNetProfitCostsAmount()).isEqualByComparingTo("450.00");
    assertThat(result.getRequestedNetProfitCostsAmount()).isEqualByComparingTo("400.40");
    assertThat(result.getNetCostOfCounselAmount()).isEqualByComparingTo("35.99");
    assertThat(result.getNetTravelCostsAmount()).isEqualByComparingTo("12.10");
    assertThat(result.getNetWaitingCostsAmount()).isEqualByComparingTo("8.80");
    assertThat(result.getDetentionTravelAndWaitingCostsAmount()).isEqualByComparingTo("5.70");
    assertThat(result.getJrFormFillingAmount()).isEqualByComparingTo("18.75");
    assertThat(result.getTravelAndWaitingCostsAmount()).isEqualByComparingTo("20.90");
    assertThat(result.getBoltOnTotalFeeAmount()).isEqualByComparingTo("33.45");
    assertThat(result.getBoltOnAdjournedHearingCount()).isEqualTo(1);
    assertThat(result.getBoltOnAdjournedHearingFee()).isEqualByComparingTo("11.11");
    assertThat(result.getBoltOnCmrhTelephoneCount()).isEqualTo(2);
    assertThat(result.getBoltOnCmrhTelephoneFee()).isEqualByComparingTo("12.12");
    assertThat(result.getBoltOnCmrhOralCount()).isEqualTo(3);
    assertThat(result.getBoltOnCmrhOralFee()).isEqualByComparingTo("13.13");
    assertThat(result.getBoltOnHomeOfficeInterviewCount()).isEqualTo(4);
    assertThat(result.getBoltOnHomeOfficeInterviewFee()).isEqualByComparingTo("14.14");
    assertThat(result.getBoltOnSubstantiveHearingFee()).isEqualByComparingTo("15.15");
    assertThat(result.getVatIndicator()).isTrue();
    assertThat(result.getIsPriceChanged()).isTrue();
  }

  @Test
  @DisplayName("leaves optional FSP fields null when the response omits them")
  void leavesOptionalFieldsNull_whenResponseOmitsThem() {
    Claim claim = claimWithSummaryFee(Instant.now());

    // No optional calculation, metadata or bolt-on values supplied.
    FeeCalculationResponse response =
        new FeeCalculationResponse()
            .feeCode("FEE-123")
            .schemeId("SCHEME-TEST")
            .feeCalculation(new FeeCalculation().totalAmount(650.00));

    CalculatedFeeDetail result =
        factory.prepareCalculatedFeeDetail(
            claim, state(snapshot(BigDecimal.valueOf(100.00))), response, amendment());

    assertThat(result.getEscapeCaseFlag()).isNull();
    assertThat(result.getFeeType()).isNull();
    assertThat(result.getFeeCodeDescription()).isNull();
    assertThat(result.getCategoryOfLaw()).isNull();
    assertThat(result.getNetProfitCostsAmount()).isNull();
    assertThat(result.getVatIndicator()).isNull();
  }

  @Test
  @DisplayName(
      "reuses previous fee metadata when enrichment is absent but the repriced fee code is unchanged")
  void reusesPreviousMetadata_whenCurrentEnrichmentIsAbsentAndFeeCodeUnchanged() {
    Claim claim = claimWithSummaryFee(Instant.now());
    ClaimAmendmentState state =
        state(
            CalculatedFeeDetailSnapshot.builder()
                .feeCode("FEE-123")
                .feeType(FeeCalculationType.FIXED)
                .feeCodeDescription("Existing description")
                .categoryOfLaw("CATEGORY-LEGACY")
                .totalAmount(BigDecimal.valueOf(100.00))
                .build());

    FeeCalculationResponse response =
        new FeeCalculationResponse()
            .feeCode("FEE-123")
            .schemeId("SCHEME-TEST")
            .feeCalculation(new FeeCalculation().totalAmount(650.00));

    CalculatedFeeDetail result =
        factory.prepareCalculatedFeeDetail(claim, state, response, amendment());

    assertThat(result.getFeeType()).isEqualTo(FeeCalculationType.FIXED);
    assertThat(result.getFeeCodeDescription()).isEqualTo("Existing description");
    assertThat(result.getCategoryOfLaw()).isEqualTo("CATEGORY-LEGACY");
  }

  @Test
  @DisplayName("matches the legacy FeeCalculationPatch mapping for every supported field")
  void matchesLegacyFeeCalculationPatchMapping() {
    Claim claim = claimWithSummaryFee(Instant.now());
    ClaimAmendment amendment = amendment();
    ClaimAmendmentState state = state(snapshot(BigDecimal.valueOf(100.00)));
    state.setResolvedClaimDataContext(new ResolvedClaimData("DISB_ONLY", "AREA-B", "CATEGORY-B"));
    state.setFeeSchemeDetailsContext(
        new FeeDetailsResponseV2()
            .feeCodeDescription("Legacy parity description")
            .feeType("DISB_ONLY")
            .categoryOfLawCodes(List.of("CATEGORY-B")));

    FeeCalculationResponse response =
        new FeeCalculationResponse()
            .feeCode("FEE-456")
            .schemeId("SCHEME-02")
            .escapeCaseFlag(false)
            .feeCalculation(
                new FeeCalculation()
                    .totalAmount(510.25)
                    .vatIndicator(false)
                    .vatRateApplied(0.0)
                    .calculatedVatAmount(0.0)
                    .disbursementAmount(210.10)
                    .requestedNetDisbursementAmount(205.05)
                    .disbursementVatAmount(0.0)
                    .hourlyTotalAmount(0.0)
                    .fixedFeeAmount(110.11)
                    .netProfitCostsAmount(190.19)
                    .requestedNetProfitCostsAmount(189.18)
                    .netCostOfCounselAmount(40.40)
                    .netTravelCostsAmount(12.12)
                    .netWaitingCostsAmount(9.09)
                    .detentionTravelAndWaitingCostsAmount(8.08)
                    .jrFormFillingAmount(7.07)
                    .travelAndWaitingCostAmount(6.06)
                    .boltOnFeeDetails(
                        new BoltOnFeeDetails()
                            .boltOnTotalFeeAmount(5.05)
                            .boltOnAdjournedHearingCount(9)
                            .boltOnAdjournedHearingFee(4.04)
                            .boltOnCmrhTelephoneCount(8)
                            .boltOnCmrhTelephoneFee(3.03)
                            .boltOnCmrhOralCount(7)
                            .boltOnCmrhOralFee(2.02)
                            .boltOnHomeOfficeInterviewCount(6)
                            .boltOnHomeOfficeInterviewFee(1.01)
                            .boltOnSubstantiveHearingFee(0.99)));

    CalculatedFeeDetail result =
        factory.prepareCalculatedFeeDetail(claim, state, response, amendment);

    CalculatedFeeDetail expectedLegacyMappedDetail =
        claimMapper.toCalculatedFeeDetail(
            new FeeCalculationPatch()
                .feeCode("FEE-456")
                .feeCodeDescription("Legacy parity description")
                .feeType(FeeCalculationType.DISB_ONLY)
                .categoryOfLaw("CATEGORY-B")
                .totalAmount(new BigDecimal("510.25"))
                .vatIndicator(false)
                .vatRateApplied(new BigDecimal("0.0"))
                .calculatedVatAmount(new BigDecimal("0.0"))
                .disbursementAmount(new BigDecimal("210.10"))
                .requestedNetDisbursementAmount(new BigDecimal("205.05"))
                .disbursementVatAmount(new BigDecimal("0.0"))
                .hourlyTotalAmount(new BigDecimal("0.0"))
                .fixedFeeAmount(new BigDecimal("110.11"))
                .netProfitCostsAmount(new BigDecimal("190.19"))
                .requestedNetProfitCostsAmount(new BigDecimal("189.18"))
                .netCostOfCounselAmount(new BigDecimal("40.40"))
                .netTravelCostsAmount(new BigDecimal("12.12"))
                .netWaitingCostsAmount(new BigDecimal("9.09"))
                .detentionTravelAndWaitingCostsAmount(new BigDecimal("8.08"))
                .jrFormFillingAmount(new BigDecimal("7.07"))
                .travelAndWaitingCostsAmount(new BigDecimal("6.06"))
                .boltOnDetails(
                    new BoltOnPatch()
                        .boltOnTotalFeeAmount(new BigDecimal("5.05"))
                        .boltOnAdjournedHearingCount(9)
                        .boltOnAdjournedHearingFee(new BigDecimal("4.04"))
                        .boltOnCmrhTelephoneCount(8)
                        .boltOnCmrhTelephoneFee(new BigDecimal("3.03"))
                        .boltOnCmrhOralCount(7)
                        .boltOnCmrhOralFee(new BigDecimal("2.02"))
                        .boltOnHomeOfficeInterviewCount(6)
                        .boltOnHomeOfficeInterviewFee(new BigDecimal("1.01"))
                        .boltOnSubstantiveHearingFee(new BigDecimal("0.99"))
                        .escapeCaseFlag(false)
                        .schemeId("SCHEME-02")));

    assertEquivalentMappedFields(result, expectedLegacyMappedDetail);
  }

  // ---------------------------------------------------------------------------
  // price_changed derivation (all four true-branches + the equal false-branch)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("is_price_changed derivation")
  class PriceChanged {

    @Test
    void false_whenTotalsAreNumericallyEqual() {
      Claim claim = claimWithSummaryFee(Instant.now());
      // Same amount, different scale, to prove compareTo (not equals) is used.
      FeeCalculationResponse response = responseWithTotal(250.00);

      CalculatedFeeDetail result =
          factory.prepareCalculatedFeeDetail(
              claim, state(snapshot(new BigDecimal("250.0000"))), response, amendment());

      assertThat(result.getIsPriceChanged()).isFalse();
    }

    @Test
    void true_whenTotalsDiffer() {
      Claim claim = claimWithSummaryFee(Instant.now());
      CalculatedFeeDetail result =
          factory.prepareCalculatedFeeDetail(
              claim,
              state(snapshot(BigDecimal.valueOf(250))),
              responseWithTotal(650.00),
              amendment());

      assertThat(result.getIsPriceChanged()).isTrue();
    }

    @Test
    void true_whenNoPreviousCalculatedFeeSnapshot() {
      Claim claim = claimWithSummaryFee(Instant.now());
      CalculatedFeeDetail result =
          factory.prepareCalculatedFeeDetail(
              claim, state(null), responseWithTotal(650.00), amendment());

      assertThat(result.getIsPriceChanged()).isTrue();
    }

    @Test
    void true_whenPreviousTotalIsNull() {
      Claim claim = claimWithSummaryFee(Instant.now());
      CalculatedFeeDetail result =
          factory.prepareCalculatedFeeDetail(
              claim, state(snapshot(null)), responseWithTotal(650.00), amendment());

      assertThat(result.getIsPriceChanged()).isTrue();
    }

    @Test
    void true_whenResponseTotalIsNull() {
      Claim claim = claimWithSummaryFee(Instant.now());
      // feeCalculation present but totalAmount null -> responseTotal null.
      FeeCalculationResponse response =
          new FeeCalculationResponse().feeCode("FEE-123").feeCalculation(new FeeCalculation());

      CalculatedFeeDetail result =
          factory.prepareCalculatedFeeDetail(
              claim, state(snapshot(BigDecimal.valueOf(250))), response, amendment());

      assertThat(result.getIsPriceChanged()).isTrue();
      assertThat(result.getTotalAmount()).isNull();
    }
  }

  // ---------------------------------------------------------------------------
  // Summary-fee linkage
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("claim_summary_fee linkage")
  class SummaryFeeLink {

    @Test
    void linksTheMostRecentlyCreatedSummaryFee() {
      Instant older = Instant.now().minus(2, ChronoUnit.DAYS);
      Instant newer = Instant.now();
      ClaimSummaryFee oldFee = summaryFee(older);
      ClaimSummaryFee newFee = summaryFee(newer);

      Claim claim = new Claim();
      claim.setId(UUID.randomUUID());
      claim.setClaimSummaryFee(new ArrayList<>(List.of(oldFee, newFee)));

      CalculatedFeeDetail result =
          factory.prepareCalculatedFeeDetail(
              claim,
              state(snapshot(BigDecimal.valueOf(100))),
              responseWithTotal(650.00),
              amendment());

      assertThat(result.getClaimSummaryFee()).isSameAs(newFee);
    }

    @Test
    void throws_whenClaimHasNoSummaryFeeList() {
      Claim claim = new Claim();
      claim.setId(UUID.randomUUID());
      claim.setClaimSummaryFee(null);
      ClaimAmendmentState state = state(snapshot(BigDecimal.valueOf(100)));
      FeeCalculationResponse response = responseWithTotal(650.00);
      ClaimAmendment amendment = amendment();

      assertThatThrownBy(
              () -> factory.prepareCalculatedFeeDetail(claim, state, response, amendment))
          .isInstanceOf(ClaimSummaryFeeNotFoundException.class)
          .hasMessageContaining(claim.getId().toString());
    }

    @Test
    void throws_whenClaimHasEmptySummaryFeeList() {
      Claim claim = new Claim();
      claim.setId(UUID.randomUUID());
      claim.setClaimSummaryFee(new ArrayList<>());
      ClaimAmendmentState state = state(snapshot(BigDecimal.valueOf(100)));
      FeeCalculationResponse response = responseWithTotal(650.00);
      ClaimAmendment amendment = amendment();

      assertThatThrownBy(
              () -> factory.prepareCalculatedFeeDetail(claim, state, response, amendment))
          .isInstanceOf(ClaimSummaryFeeNotFoundException.class);
    }
  }

  // ---------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------

  private static FeeCalculationResponse responseWithTotal(double total) {
    return new FeeCalculationResponse()
        .feeCode("FEE-123")
        .schemeId("SCHEME-TEST")
        .escapeCaseFlag(false)
        .feeCalculation(new FeeCalculation().totalAmount(total));
  }

  private static CalculatedFeeDetailSnapshot snapshot(BigDecimal previousTotal) {
    return CalculatedFeeDetailSnapshot.builder().totalAmount(previousTotal).build();
  }

  private static ClaimAmendmentState state(CalculatedFeeDetailSnapshot previousFee) {
    return ClaimAmendmentState.builder()
        .beforeState(ClaimStateSnapshot.builder().calculatedFeeDetail(previousFee).build())
        .build();
  }

  private static ClaimAmendment amendment() {
    ClaimAmendment amendment = new ClaimAmendment();
    amendment.setId(Uuid7.timeBasedUuid());
    amendment.setCreatedByUserId(AMENDING_USER);
    return amendment;
  }

  private static ClaimSummaryFee summaryFee(Instant createdOn) {
    return ClaimSummaryFee.builder()
        .id(Uuid7.timeBasedUuid())
        .createdByUserId("Test")
        .createdOn(createdOn)
        .build();
  }

  private static Claim claimWithSummaryFee(Instant summaryFeeCreatedOn) {
    Claim claim = new Claim();
    claim.setId(UUID.randomUUID());
    claim.setClaimSummaryFee(new ArrayList<>(List.of(summaryFee(summaryFeeCreatedOn))));
    return claim;
  }

  private static void assertEquivalentMappedFields(
      CalculatedFeeDetail actual, CalculatedFeeDetail expected) {
    assertThat(actual.getFeeCode()).isEqualTo(expected.getFeeCode());
    assertThat(actual.getFeeType()).isEqualTo(expected.getFeeType());
    assertThat(actual.getFeeCodeDescription()).isEqualTo(expected.getFeeCodeDescription());
    assertThat(actual.getCategoryOfLaw()).isEqualTo(expected.getCategoryOfLaw());
    assertThat(actual.getTotalAmount()).isEqualByComparingTo(expected.getTotalAmount());
    assertThat(actual.getVatIndicator()).isEqualTo(expected.getVatIndicator());
    assertThat(actual.getVatRateApplied()).isEqualByComparingTo(expected.getVatRateApplied());
    assertThat(actual.getCalculatedVatAmount())
        .isEqualByComparingTo(expected.getCalculatedVatAmount());
    assertThat(actual.getDisbursementAmount())
        .isEqualByComparingTo(expected.getDisbursementAmount());
    assertThat(actual.getRequestedNetDisbursementAmount())
        .isEqualByComparingTo(expected.getRequestedNetDisbursementAmount());
    assertThat(actual.getDisbursementVatAmount())
        .isEqualByComparingTo(expected.getDisbursementVatAmount());
    assertThat(actual.getHourlyTotalAmount()).isEqualByComparingTo(expected.getHourlyTotalAmount());
    assertThat(actual.getFixedFeeAmount()).isEqualByComparingTo(expected.getFixedFeeAmount());
    assertThat(actual.getNetProfitCostsAmount())
        .isEqualByComparingTo(expected.getNetProfitCostsAmount());
    assertThat(actual.getRequestedNetProfitCostsAmount())
        .isEqualByComparingTo(expected.getRequestedNetProfitCostsAmount());
    assertThat(actual.getNetCostOfCounselAmount())
        .isEqualByComparingTo(expected.getNetCostOfCounselAmount());
    assertThat(actual.getNetTravelCostsAmount())
        .isEqualByComparingTo(expected.getNetTravelCostsAmount());
    assertThat(actual.getNetWaitingCostsAmount())
        .isEqualByComparingTo(expected.getNetWaitingCostsAmount());
    assertThat(actual.getDetentionTravelAndWaitingCostsAmount())
        .isEqualByComparingTo(expected.getDetentionTravelAndWaitingCostsAmount());
    assertThat(actual.getJrFormFillingAmount())
        .isEqualByComparingTo(expected.getJrFormFillingAmount());
    assertThat(actual.getTravelAndWaitingCostsAmount())
        .isEqualByComparingTo(expected.getTravelAndWaitingCostsAmount());
    assertThat(actual.getBoltOnTotalFeeAmount())
        .isEqualByComparingTo(expected.getBoltOnTotalFeeAmount());
    assertThat(actual.getBoltOnAdjournedHearingCount())
        .isEqualTo(expected.getBoltOnAdjournedHearingCount());
    assertThat(actual.getBoltOnAdjournedHearingFee())
        .isEqualByComparingTo(expected.getBoltOnAdjournedHearingFee());
    assertThat(actual.getBoltOnCmrhTelephoneCount())
        .isEqualTo(expected.getBoltOnCmrhTelephoneCount());
    assertThat(actual.getBoltOnCmrhTelephoneFee())
        .isEqualByComparingTo(expected.getBoltOnCmrhTelephoneFee());
    assertThat(actual.getBoltOnCmrhOralCount()).isEqualTo(expected.getBoltOnCmrhOralCount());
    assertThat(actual.getBoltOnCmrhOralFee()).isEqualByComparingTo(expected.getBoltOnCmrhOralFee());
    assertThat(actual.getBoltOnHomeOfficeInterviewCount())
        .isEqualTo(expected.getBoltOnHomeOfficeInterviewCount());
    assertThat(actual.getBoltOnHomeOfficeInterviewFee())
        .isEqualByComparingTo(expected.getBoltOnHomeOfficeInterviewFee());
    assertThat(actual.getBoltOnSubstantiveHearingFee())
        .isEqualByComparingTo(expected.getBoltOnSubstantiveHearingFee());
    assertThat(actual.getEscapeCaseFlag()).isEqualTo(expected.getEscapeCaseFlag());
    assertThat(actual.getSchemeId()).isEqualTo(expected.getSchemeId());
  }
}
