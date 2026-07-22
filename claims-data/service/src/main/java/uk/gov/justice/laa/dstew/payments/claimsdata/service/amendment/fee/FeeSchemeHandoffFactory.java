package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
    // Build the database entity row structure
    CalculatedFeeDetail newFeeDetail = new CalculatedFeeDetail();
    newFeeDetail.setId(Uuid7.timeBasedUuid());
    newFeeDetail.setClaim(claim);
    newFeeDetail.setClaimAmendment(claimAmendment); // 1595-F: Establish tracking link
    newFeeDetail.setIsPriceChanged(priceChanged);
    newFeeDetail.setTotalAmount(responseTotal);
    newFeeDetail.setCreatedOn(OffsetDateTime.now());

    // --- ADDED: Map missing required FSP fields ---
    newFeeDetail.setFeeCode(feeCalculationResponse.getFeeCode());
    newFeeDetail.setSchemeId(feeCalculationResponse.getSchemeId());

    if (feeCalculationResponse.getEscapeCaseFlag() != null) {
      newFeeDetail.setEscapeCaseFlag(feeCalculationResponse.getEscapeCaseFlag());
    }

    if (calc.getNetProfitCostsAmount() != null) {
      newFeeDetail.setNetProfitCostsAmount(BigDecimal.valueOf(calc.getNetProfitCostsAmount()));
    }

    if (calc.getVatIndicator() != null) {
      newFeeDetail.setVatIndicator(calc.getVatIndicator());
    }

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
              "Cannot persist CalculatedFeeDetail: claim has no ClaimSummaryFee- ID: %s",
              claim.getId().toString());
      log.error(errorMessage);
      throw new IllegalStateException(errorMessage);
    }
    newFeeDetail.setClaimSummaryFee(latestSummaryFee);
    return newFeeDetail;
  }
}
