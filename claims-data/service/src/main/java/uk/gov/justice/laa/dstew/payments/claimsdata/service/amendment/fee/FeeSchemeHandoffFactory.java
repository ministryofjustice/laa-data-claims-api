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
import uk.gov.justice.laa.fee.scheme.model.FeeCalculation;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

/**
 * Prepares the physical database entity row data for successful FSP repricing to be handed off to
 * the atomic commit write transaction (1595-F).
 *
 * <p>All field-level projection from the FSP response to the entity happens in {@link
 * ClaimMapper#toCalculatedFeeDetail(FeeCalculationResponse, ResolvedFeeMetadata)}; this class only
 * orchestrates resolver + mapper + the relational / audit fields that MapStruct cannot infer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeeSchemeHandoffFactory {

  private final ClaimMapper claimMapper;
  private final FeeCalculationMetadataResolver feeCalculationMetadataResolver;

  /** Translates a successful FSP platform response into a storable {@link CalculatedFeeDetail}. */
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

    // Delegate the field-by-field projection to MapStruct. The resolver supplies the three
    // metadata fields (feeType / feeCodeDescription / categoryOfLaw) that are not on the FSP
    // response but are required for parity with the legacy claim pricing flow.
    ResolvedFeeMetadata metadata =
        feeCalculationMetadataResolver.resolve(state, feeCalculationResponse.getFeeCode());
    CalculatedFeeDetail newFeeDetail =
        claimMapper.toCalculatedFeeDetail(feeCalculationResponse, metadata);

    // Relational + audit fields MapStruct cannot infer from the FSP response.
    newFeeDetail.setClaim(claim);
    newFeeDetail.setClaimAmendment(claimAmendment); // 1595-F: Establish tracking link
    newFeeDetail.setIsPriceChanged(priceChanged);
    newFeeDetail.setCreatedOn(Instant.now());
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
      throw new ClaimSummaryFeeNotFoundException(errorMessage);
    }
    newFeeDetail.setClaimSummaryFee(latestSummaryFee);
    return newFeeDetail;
  }
}
