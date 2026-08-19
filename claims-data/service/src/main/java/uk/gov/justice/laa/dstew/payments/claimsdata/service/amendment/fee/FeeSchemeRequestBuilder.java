package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import static uk.gov.justice.laa.dstew.payments.claimsdata.util.DoubleUtils.toDouble;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.fee.scheme.model.BoltOnType;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationRequest;

/**
 * Component responsible for building a pristine {@link FeeCalculationRequest} payload to send to
 * the Fee Scheme Platform for repricing.
 *
 * <p>It maps values directly from the fully resolved post-amendment state snapshot.
 */
@Component
public class FeeSchemeRequestBuilder {

  /**
   * Builds the calculation request from the current post-amendment state context.
   *
   * @param state the in-memory aggregate describing the amendment in progress
   * @return the fully populated request contract matching the FSP OpenAPI specifications
   */
  public FeeCalculationRequest buildRequest(ClaimAmendmentState state) {
    ClaimStateSnapshot post = state.getPostAmendmentState();

    // 1. Build the nested BoltOn block
    BoltOnType boltOns =
        new BoltOnType()
            .boltOnAdjournedHearing(post.getAdjournedHearingFeeAmount())
            .boltOnCmrhOral(post.getCmrhOralCount())
            .boltOnCmrhTelephone(post.getCmrhTelephoneCount())
            .boltOnHomeOfficeInterview(post.getHoInterview())
            .boltOnSubstantiveHearing(post.getIsSubstantiveHearing());

    // 2. Build the root calculation request mapped to the external platform parameters
    return new FeeCalculationRequest(post.getFeeCode())
        .claimId(post.getClaimId() != null ? post.getClaimId().toString() : null)
        .startDate(post.getCaseStartDate())
        .caseConcludedDate(post.getCaseConcludedDate())
        .uniqueFileNumber(post.getUniqueFileNumber())
        .policeStationId(post.getPoliceStationCourtPrisonId())
        .policeStationSchemeId(post.getSchemeId())

        // Map financial amounts (BigDecimal -> Double)
        .netProfitCosts(toDouble(post.getNetProfitCostsAmount()))
        .netCostOfCounsel(toDouble(post.getNetCounselCostsAmount()))
        .netDisbursementAmount(toDouble(post.getNetDisbursementAmount()))
        .disbursementVatAmount(toDouble(post.getDisbursementsVatAmount()))
        .jrFormFilling(toDouble(post.getJrFormFillingAmount()))
        .travelAndWaitingCosts(toDouble(post.getTravelWaitingCostsAmount()))
        .detentionTravelAndWaitingCosts(toDouble(post.getDetentionTravelWaitingCostsAmount()))

        // Map time allocations (Integer -> Double)
        .netTravelCosts(toDouble(post.getTravelTime()))
        .netWaitingCosts(toDouble(post.getWaitingTime()))

        // Map remaining identifiers and flags
        .vatIndicator(post.getIsVatApplicable())
        .londonRate(post.getIsLondonRate())
        .numberOfMediationSessions(post.getMediationSessionsCount())
        .representationOrderDate(post.getRepresentationOrderDate())
        .immigrationPriorAuthorityNumber(post.getPriorAuthorityReference())
        .boltOns(boltOns);
  }
}
