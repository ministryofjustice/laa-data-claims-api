package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculation;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

/**
 * Prepares the physical database entity row data for successful FSP repricing to be handed off to
 * the atomic commit write transaction (1595-F).
 */
@Component
@RequiredArgsConstructor
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
        calc.getTotalAmount() != null ? BigDecimal.valueOf(calc.getTotalAmount()) : BigDecimal.ZERO;
    boolean priceChanged =
        previousFeeState == null || previousFeeState.getTotalAmount().compareTo(responseTotal) != 0;

    // Build the database entity row structure
    CalculatedFeeDetail newFeeDetail = new CalculatedFeeDetail();
    newFeeDetail.setClaim(claim);
    newFeeDetail.setClaimAmendment(claimAmendment); // 1595-F: Establish tracking link
    newFeeDetail.setIsPriceChanged(priceChanged);
    newFeeDetail.setTotalAmount(responseTotal);
    newFeeDetail.setCreatedOn(OffsetDateTime.now());

    // You can map additional breakdown fields here (e.g. fixedFeeAmount, hourlyTotalAmount)
    // from 'calc' to 'newFeeDetail' as required by your entity schema.

    return newFeeDetail;
  }
}
