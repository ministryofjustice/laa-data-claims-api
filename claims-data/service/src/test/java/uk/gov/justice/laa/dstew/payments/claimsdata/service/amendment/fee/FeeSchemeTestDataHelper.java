package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import java.math.BigDecimal;
import java.time.LocalDate;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.fee.scheme.model.BoltOnFeeDetails;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculation;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

public class FeeSchemeTestDataHelper {

  public static ClaimStateSnapshot.ClaimStateSnapshotBuilder createBaseBeforeStateBuilder() {
    return ClaimStateSnapshot.builder()
        .feeCode("FEE001")
        .caseStartDate(LocalDate.of(2026, 1, 1))
        .netProfitCostsAmount(BigDecimal.valueOf(150.00))
        .netDisbursementAmount(BigDecimal.valueOf(50.00))
        .travelTime(120)
        .waitingTime(60)
        .calculatedFeeDetail(
            CalculatedFeeDetailSnapshot.builder().totalAmount(BigDecimal.valueOf(250.00)).build());
  }

  public static FeeCalculationResponse createMockResponse(
      Double total, Double profitCosts, Double boltOnTotal) {
    BoltOnFeeDetails boltOns =
        new BoltOnFeeDetails()
            .boltOnTotalFeeAmount(boltOnTotal)
            .boltOnAdjournedHearingCount(1)
            .boltOnAdjournedHearingFee(boltOnTotal);

    FeeCalculation calc =
        new FeeCalculation()
            .totalAmount(total)
            .netProfitCostsAmount(profitCosts)
            .boltOnFeeDetails(boltOns)
            .vatIndicator(true)
            .vatRateApplied(20.0);

    return new FeeCalculationResponse()
        .feeCode("FEE001")
        .schemeId("SCHEME-A")
        .escapeCaseFlag(false)
        .feeCalculation(calc);
  }
}
