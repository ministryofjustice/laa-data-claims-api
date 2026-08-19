package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;

/**
 * Immutable read-only snapshot of the latest calculated fee detail (as produced by the Fee Scheme
 * Platform). Used by downstream validation to compare a fresh FSP response against the previously
 * calculated state. Not provider-amendable.
 */
@Value
@Builder
public class CalculatedFeeDetailSnapshot {

  String feeCode;
  FeeCalculationType feeType;
  String feeCodeDescription;
  String categoryOfLaw;
  BigDecimal totalAmount;
  Boolean vatIndicator;
  BigDecimal vatRateApplied;
  BigDecimal calculatedVatAmount;
  BigDecimal disbursementAmount;
  BigDecimal requestedNetDisbursementAmount;
  BigDecimal disbursementVatAmount;
  BigDecimal hourlyTotalAmount;
  BigDecimal fixedFeeAmount;
  BigDecimal netProfitCostsAmount;
  BigDecimal requestedNetProfitCostsAmount;
  BigDecimal netCostOfCounselAmount;
  BigDecimal netTravelCostsAmount;
  BigDecimal netWaitingCostsAmount;
  BigDecimal detentionTravelAndWaitingCostsAmount;
  BigDecimal jrFormFillingAmount;
  BigDecimal travelAndWaitingCostsAmount;
  BigDecimal boltOnTotalFeeAmount;
  Integer boltOnAdjournedHearingCount;
  BigDecimal boltOnAdjournedHearingFee;
  Integer boltOnCmrhTelephoneCount;
  BigDecimal boltOnCmrhTelephoneFee;
  Integer boltOnCmrhOralCount;
  BigDecimal boltOnCmrhOralFee;
  Integer boltOnHomeOfficeInterviewCount;
  BigDecimal boltOnHomeOfficeInterviewFee;
  BigDecimal boltOnSubstantiveHearingFee;
  Boolean escapeCaseFlag;
  String schemeId;
}
