package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BoltOnPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculation;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

/**
 * Prepares the physical database entity row data for successful FSP repricing to be handed off to
 * the atomic commit write transaction (1595-F).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeeSchemeHandoffFactory {

  private final ClaimMapper claimMapper;
  private final FeeCalculationMetadataResolver feeCalculationMetadataResolver;

  /**
   * Translates a successful OpenAPI platform response into a storable CalculatedFeeDetail entity.
   */
  public CalculatedFeeDetail prepareCalculatedFeeDetail(
      Claim claim,
      ClaimAmendmentState state,
      FeeCalculationResponse feeCalculationResponse,
      ClaimAmendment claimAmendment) {

    if (feeCalculationResponse == null || feeCalculationResponse.getFeeCalculation() == null) {
      return null;
    }

    FeeCalculation calc = feeCalculationResponse.getFeeCalculation();
    CalculatedFeeDetailSnapshot previousFeeState = state.getBeforeState().getCalculatedFeeDetail();

    // 1595-F: Check if the overall amount shifted numerically (Double vs BigDecimal)
    BigDecimal responseTotal =
        calc.getTotalAmount() != null ? BigDecimal.valueOf(calc.getTotalAmount()) : null;

    boolean priceChanged =
        previousFeeState == null
            || previousFeeState.getTotalAmount() == null
            || responseTotal == null
            || previousFeeState.getTotalAmount().compareTo(responseTotal) != 0;
    // Reuse the legacy claim-mapper shape so amendment repricing persists the same
    // CalculatedFeeDetail
    // fields as the standard claim journey.
    CalculatedFeeDetail newFeeDetail =
        claimMapper.toCalculatedFeeDetail(toFeeCalculationPatch(state, feeCalculationResponse));
    newFeeDetail.setId(Uuid7.timeBasedUuid());
    newFeeDetail.setClaim(claim);
    newFeeDetail.setClaimAmendment(claimAmendment); // 1595-F: Establish tracking link
    newFeeDetail.setIsPriceChanged(priceChanged);
    newFeeDetail.setTotalAmount(responseTotal);
    newFeeDetail.setCreatedOn(Instant.now());

    // --- ADDED: Map required audit & relational fields ---
    // Inherit the user ID from the amendment request
    newFeeDetail.setCreatedByUserId(claimAmendment.getCreatedByUserId());

    // Link to the active ClaimSummaryFee (required by CalculatedFeeDetail.claimSummaryFee)
    List<ClaimSummaryFee> claimSummaryFees = claim.getClaimSummaryFee();
    ClaimSummaryFee latestSummaryFee =
        claimSummaryFees == null
            ? null
            : claimSummaryFees.stream()
                .max(Comparator.comparing(ClaimSummaryFee::getCreatedOn))
                .orElse(null);

    if (latestSummaryFee == null) {
      final String errorMessage =
          String.format(
              "Cannot persist CalculatedFeeDetail: No summary fee for claim %s", claim.getId());
      log.error(errorMessage);
      throw new ClaimSummaryFeeNotFoundException(String.format(errorMessage));
    }
    newFeeDetail.setClaimSummaryFee(latestSummaryFee);
    return newFeeDetail;
  }

  private FeeCalculationPatch toFeeCalculationPatch(
      ClaimAmendmentState state, FeeCalculationResponse feeCalculationResponse) {
    FeeCalculation calc = feeCalculationResponse.getFeeCalculation();
    BoltOnPatch boltOnPatch = toBoltOnPatch(feeCalculationResponse, calc);

    FeeCalculationPatch patch =
        new FeeCalculationPatch()
            .feeCode(feeCalculationResponse.getFeeCode())
            .feeType(
                feeCalculationMetadataResolver.resolveFeeType(
                    state, feeCalculationResponse.getFeeCode()))
            .feeCodeDescription(
                feeCalculationMetadataResolver.resolveFeeCodeDescription(
                    state, feeCalculationResponse.getFeeCode()))
            .categoryOfLaw(
                feeCalculationMetadataResolver.resolveCategoryOfLaw(
                    state, feeCalculationResponse.getFeeCode()))
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
            .travelAndWaitingCostsAmount(toBigDecimal(calc.getTravelAndWaitingCostAmount()));

    if (boltOnPatch != null) {
      patch.setBoltOnDetails(boltOnPatch);
    }

    return patch;
  }

  private BoltOnPatch toBoltOnPatch(
      FeeCalculationResponse feeCalculationResponse, FeeCalculation calc) {
    BoltOnPatch boltOnPatch = new BoltOnPatch();
    boolean hasBoltOnFields = false;

    if (calc.getBoltOnFeeDetails() != null) {
      boltOnPatch
          .boltOnTotalFeeAmount(toBigDecimal(calc.getBoltOnFeeDetails().getBoltOnTotalFeeAmount()))
          .boltOnAdjournedHearingCount(calc.getBoltOnFeeDetails().getBoltOnAdjournedHearingCount())
          .boltOnAdjournedHearingFee(
              toBigDecimal(calc.getBoltOnFeeDetails().getBoltOnAdjournedHearingFee()))
          .boltOnCmrhTelephoneCount(calc.getBoltOnFeeDetails().getBoltOnCmrhTelephoneCount())
          .boltOnCmrhTelephoneFee(
              toBigDecimal(calc.getBoltOnFeeDetails().getBoltOnCmrhTelephoneFee()))
          .boltOnCmrhOralCount(calc.getBoltOnFeeDetails().getBoltOnCmrhOralCount())
          .boltOnCmrhOralFee(toBigDecimal(calc.getBoltOnFeeDetails().getBoltOnCmrhOralFee()))
          .boltOnHomeOfficeInterviewCount(
              calc.getBoltOnFeeDetails().getBoltOnHomeOfficeInterviewCount())
          .boltOnHomeOfficeInterviewFee(
              toBigDecimal(calc.getBoltOnFeeDetails().getBoltOnHomeOfficeInterviewFee()))
          .boltOnSubstantiveHearingFee(
              toBigDecimal(calc.getBoltOnFeeDetails().getBoltOnSubstantiveHearingFee()));
      hasBoltOnFields = true;
    }

    if (feeCalculationResponse.getEscapeCaseFlag() != null) {
      boltOnPatch.escapeCaseFlag(feeCalculationResponse.getEscapeCaseFlag());
      hasBoltOnFields = true;
    }

    if (feeCalculationResponse.getSchemeId() != null) {
      boltOnPatch.schemeId(feeCalculationResponse.getSchemeId());
      hasBoltOnFields = true;
    }

    return hasBoltOnFields ? boltOnPatch : null;
  }

  private BigDecimal toBigDecimal(Double value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }
}
