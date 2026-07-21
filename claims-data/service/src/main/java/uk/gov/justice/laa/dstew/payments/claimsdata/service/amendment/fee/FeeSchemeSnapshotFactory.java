package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.fee.scheme.model.BoltOnFeeDetails;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculation;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

/**
 * Factory component responsible for converting a downstream Fee Scheme Platform (FSP) runtime
 * pricing calculation response into an immutable, in-memory snapshot.
 *
 * <p>This translation runs during Phase 2 (Validation) of the claim amendment flow, completely
 * outside an active database transaction. The resulting immutable snapshot is attached to the
 * parent {@link uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState}
 * object to represent the "after" picture of the fee calculations. This enables downstream history
 * diff generation and validation checks before records hit physical database persistence.
 *
 * <p>The factory structurally unwraps the deeply nested object hierarchy produced by the FSP
 * OpenAPI specification rules (such as sub-calculation sheets and nested bolt-ons) and transforms
 * raw primitive {@link Double} data fields back into domain-compliant {@link java.math.BigDecimal}
 * types.
 *
 * @see uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot
 * @see uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState
 */
@Component
public class FeeSchemeSnapshotFactory {

  /**
   * Transforms a nullable {@link FeeCalculationResponse} payload into an immutable {@link
   * CalculatedFeeDetailSnapshot}.
   *
   * <p>This extraction applies explicit null-safety blocks to every intermediate nested model layer
   * (unwrapping {@code FeeCalculation} and {@code BoltOnFeeDetails}) so that missing structural
   * segments safely fall back to unpopulated properties rather than throwing a {@code
   * NullPointerException}.
   *
   * @param response the raw, auto-generated OpenAPI contract response model received from the Fee
   *     Scheme Platform service endpoint; may be {@code null}
   * @return a completely unrolled, read-only {@link CalculatedFeeDetailSnapshot} carrying the
   *     unwrapped monetary metrics and calculation context, or {@code null} if the incoming
   *     response was null
   */
  public CalculatedFeeDetailSnapshot toSnapshot(FeeCalculationResponse response) {
    if (response == null) {
      return null;
    }

    // Safely parse out nested sub-schemas from the contract
    FeeCalculation calc =
        response.getFeeCalculation() != null ? response.getFeeCalculation() : new FeeCalculation();
    BoltOnFeeDetails boltOns =
        calc.getBoltOnFeeDetails() != null ? calc.getBoltOnFeeDetails() : new BoltOnFeeDetails();

    return CalculatedFeeDetailSnapshot.builder()
        .feeCode(response.getFeeCode())
        .schemeId(response.getSchemeId())
        .escapeCaseFlag(response.getEscapeCaseFlag())
        // Unwrap root values from inner feeCalculation object
        .totalAmount(toBigDecimal(calc.getTotalAmount()))
        .vatIndicator(calc.getVatIndicator())
        .vatRateApplied(toBigDecimal(calc.getVatRateApplied()))
        .calculatedVatAmount(toBigDecimal(calc.getCalculatedVatAmount()))
        .disbursementAmount(toBigDecimal(calc.getDisbursementAmount()))
        .requestedNetDisbursementAmount(toBigDecimal(calc.getRequestedNetDisbursementAmount()))
        .disbursementVatAmount(toBigDecimal(calc.getDisbursementVatAmount()))
        .hourlyTotalAmount(toBigDecimal(calc.getHourlyTotalAmount()))
        .fixedFeeAmount(toBigDecimal(calc.getFixedFeeAmount()))
        .netProfitCostsAmount(toBigDecimal(calc.getNetProfitCostsAmount()))
        .requestedNetProfitCostsAmount(toBigDecimal(calc.getRequestedNetProfitCostsAmount()))
        .netCostOfCounselAmount(toBigDecimal(calc.getNetCostOfCounselAmount()))
        .netTravelCostsAmount(toBigDecimal(calc.getNetTravelCostsAmount()))
        .netWaitingCostsAmount(toBigDecimal(calc.getNetWaitingCostsAmount()))
        .detentionTravelAndWaitingCostsAmount(
            toBigDecimal(calc.getDetentionTravelAndWaitingCostsAmount()))
        .jrFormFillingAmount(toBigDecimal(calc.getJrFormFillingAmount()))
        .travelAndWaitingCostsAmount(toBigDecimal(calc.getTravelAndWaitingCostAmount()))
        // Unwrap details from the deeply nested boltOnFeeDetails structure
        .boltOnTotalFeeAmount(toBigDecimal(boltOns.getBoltOnTotalFeeAmount()))
        .boltOnAdjournedHearingCount(boltOns.getBoltOnAdjournedHearingCount())
        .boltOnAdjournedHearingFee(toBigDecimal(boltOns.getBoltOnAdjournedHearingFee()))
        .boltOnCmrhTelephoneCount(boltOns.getBoltOnCmrhTelephoneCount())
        .boltOnCmrhTelephoneFee(toBigDecimal(boltOns.getBoltOnCmrhTelephoneFee()))
        .boltOnCmrhOralCount(boltOns.getBoltOnCmrhOralCount())
        .boltOnCmrhOralFee(toBigDecimal(boltOns.getBoltOnCmrhOralFee()))
        .boltOnHomeOfficeInterviewCount(boltOns.getBoltOnHomeOfficeInterviewCount())
        .boltOnHomeOfficeInterviewFee(toBigDecimal(boltOns.getBoltOnHomeOfficeInterviewFee()))
        .boltOnSubstantiveHearingFee(toBigDecimal(boltOns.getBoltOnSubstantiveHearingFee()))
        .build();
  }

  private BigDecimal toBigDecimal(Double val) {
    return val == null ? null : BigDecimal.valueOf(val);
  }
}
