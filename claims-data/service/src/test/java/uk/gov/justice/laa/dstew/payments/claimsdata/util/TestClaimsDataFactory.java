package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;

/**
 * Shared test factory for creating CalculatedFeeDetail fixtures used across unit/integration tests.
 */
public final class TestClaimsDataFactory {

  private TestClaimsDataFactory() {
    // utility
  }

  public static CalculatedFeeDetail getCalculatedFeeDetail(
      Claim claim, ClaimSummaryFee claimSummaryFee, UUID id) {
    return getCalculatedFeeDetail(
        claim,
        claimSummaryFee,
        id,
        Boolean.TRUE,
        Instant.now(),
        "FEE-123",
        BigDecimal.valueOf(100.00));
  }

  public static CalculatedFeeDetail getCalculatedFeeDetail(
      Claim claim,
      ClaimSummaryFee claimSummaryFee,
      UUID id,
      boolean escapeCaseFlag,
      Instant createdOn,
      String feeCode,
      BigDecimal totalAmount) {

    CalculatedFeeDetail.CalculatedFeeDetailBuilder builder = CalculatedFeeDetail.builder();

    builder.id(id == null ? UUID.randomUUID() : id);

    if (claim != null) {
      builder.claim(claim);
    }

    if (claimSummaryFee != null) {
      builder.claimSummaryFee(claimSummaryFee);
    }

    builder
        .feeCode(feeCode)
        .feeCodeDescription("Fee description")
        .feeType(FeeCalculationType.DISB_ONLY)
        .categoryOfLaw("LAW")
        .totalAmount(totalAmount)
        .vatIndicator(Boolean.TRUE)
        .vatRateApplied(new BigDecimal("20.00"))
        .calculatedVatAmount(new BigDecimal("20.00"))
        .disbursementAmount(new BigDecimal("10.00"))
        .requestedNetDisbursementAmount(new BigDecimal("9.00"))
        .disbursementVatAmount(new BigDecimal("1.00"))
        .hourlyTotalAmount(new BigDecimal("50.00"))
        .fixedFeeAmount(new BigDecimal("30.00"))
        .netProfitCostsAmount(new BigDecimal("40.00"))
        .requestedNetProfitCostsAmount(new BigDecimal("35.00"))
        .netCostOfCounselAmount(new BigDecimal("25.00"))
        .netTravelCostsAmount(new BigDecimal("15.00"))
        .netWaitingCostsAmount(new BigDecimal("5.00"))
        .detentionTravelAndWaitingCostsAmount(new BigDecimal("3.00"))
        .jrFormFillingAmount(new BigDecimal("2.00"))
        .travelAndWaitingCostsAmount(new BigDecimal("4.00"))
        .boltOnTotalFeeAmount(new BigDecimal("6.00"))
        .boltOnAdjournedHearingCount(1)
        .boltOnAdjournedHearingFee(new BigDecimal("1.50"))
        .boltOnCmrhTelephoneCount(2)
        .boltOnCmrhTelephoneFee(new BigDecimal("2.50"))
        .boltOnCmrhOralCount(3)
        .boltOnCmrhOralFee(new BigDecimal("3.50"))
        .boltOnHomeOfficeInterviewCount(4)
        .boltOnHomeOfficeInterviewFee(new BigDecimal("4.50"))
        .boltOnSubstantiveHearingFee(new BigDecimal("7.30"))
        .escapeCaseFlag(escapeCaseFlag)
        .schemeId("SCHEME-01")
        .createdByUserId("test-user")
        .createdOn(createdOn == null ? Instant.now() : createdOn);

    return builder.build();
  }
}
