package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculation;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

/**
 * Unit coverage for {@link FeeSchemeHandoffFactory} - the single component that maps a successful
 * FSP repricing response into a storable {@link CalculatedFeeDetail} and establishes the {@code
 * calculated_fee_detail -> claim_amendment} tracking link (DSTEW-1762 / 1595-F).
 */
@DisplayName("FeeSchemeHandoffFactory (DSTEW-1762 / 1595-F) unit tests")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class FeeSchemeHandoffFactoryTest {

  private static final String AMENDING_USER = "user-1762";

  private final FeeSchemeHandoffFactory factory = new FeeSchemeHandoffFactory();

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
  @DisplayName("maps every FSP field, inherits the amending user and links the amendment")
  void mapsAllFields_andEstablishesAmendmentLink() {
    Claim claim = claimWithSummaryFee(Instant.now());
    ClaimAmendment amendment = amendment();

    FeeCalculationResponse response =
        new FeeCalculationResponse()
            .feeCode("FEE-123")
            .schemeId("SCHEME-TEST")
            .escapeCaseFlag(true)
            .feeCalculation(
                new FeeCalculation()
                    .totalAmount(650.00)
                    .netProfitCostsAmount(450.00)
                    .vatIndicator(true));

    CalculatedFeeDetail result =
        factory.prepareCalculatedFeeDetail(
            claim, state(snapshot(BigDecimal.valueOf(100.00))), response, amendment);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isNotNull();
    assertThat(result.getClaim()).isSameAs(claim);
    // The tracking link that the history-endpoint FSP flags depend on.
    assertThat(result.getClaimAmendment()).isSameAs(amendment);
    assertThat(result.getCreatedByUserId()).isEqualTo(AMENDING_USER);
    assertThat(result.getCreatedOn()).isNotNull();

    assertThat(result.getFeeCode()).isEqualTo("FEE-123");
    assertThat(result.getSchemeId()).isEqualTo("SCHEME-TEST");
    assertThat(result.getEscapeCaseFlag()).isTrue();
    assertThat(result.getTotalAmount()).isEqualByComparingTo("650.00");
    assertThat(result.getNetProfitCostsAmount()).isEqualByComparingTo("450.00");
    assertThat(result.getVatIndicator()).isTrue();
    assertThat(result.getIsPriceChanged()).isTrue();
  }

  @Test
  @DisplayName("leaves optional FSP fields null when the response omits them")
  void leavesOptionalFieldsNull_whenResponseOmitsThem() {
    Claim claim = claimWithSummaryFee(Instant.now());

    // No escapeCaseFlag / netProfitCosts / vatIndicator supplied.
    FeeCalculationResponse response =
        new FeeCalculationResponse()
            .feeCode("FEE-123")
            .schemeId("SCHEME-TEST")
            .feeCalculation(new FeeCalculation().totalAmount(650.00));

    CalculatedFeeDetail result =
        factory.prepareCalculatedFeeDetail(
            claim, state(snapshot(BigDecimal.valueOf(100.00))), response, amendment());

    assertThat(result.getEscapeCaseFlag()).isNull();
    assertThat(result.getNetProfitCostsAmount()).isNull();
    assertThat(result.getVatIndicator()).isNull();
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
              claim,
              state((CalculatedFeeDetailSnapshot) null),
              responseWithTotal(650.00),
              amendment());

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
}
