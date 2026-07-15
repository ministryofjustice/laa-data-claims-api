package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import java.math.BigDecimal;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.fee.scheme.model.BoltOnType;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationRequest;

/**
 * Component responsible for building a pristine {@link FeeCalculationRequest} payload to send to
 * the Fee Scheme Platform for repricing.
 *
 * <p>It applies sparse merge semantics: picking values from the provider's amendment request
 * payload if present, and falling back to the current stored "before" state snapshot if omitted.
 */
@Component
public class FeeSchemeRequestBuilder {

  /**
   * Builds the sparse-merged calculation request from the current amendment state context.
   *
   * @param state the in-memory aggregate describing the amendment in progress
   * @return the fully populated request contract matching the FSP OpenAPI specifications
   */
  public FeeCalculationRequest buildRequest(ClaimAmendmentState state) {
    ClaimAmendmentPayload patch = state.getRequestPayload();
    ClaimStateSnapshot before = state.getBeforeState();

    // 1. Build the nested BoltOn block using sparse overrides
    BoltOnType boltOns =
        new BoltOnType()
            .boltOnAdjournedHearing(
                resolve(
                    patch.getAdjournedHearingFeeAmount(), before.getAdjournedHearingFeeAmount()))
            .boltOnCmrhOral(resolve(patch.getCmrhOralCount(), before.getCmrhOralCount()))
            .boltOnCmrhTelephone(
                resolve(patch.getCmrhTelephoneCount(), before.getCmrhTelephoneCount()))
            .boltOnHomeOfficeInterview(resolve(patch.getHoInterview(), before.getHoInterview()))
            .boltOnSubstantiveHearing(
                resolve(patch.getIsSubstantiveHearing(), before.getIsSubstantiveHearing()));

    // 2. Build the root calculation request mapped to the external platform parameters
    return new FeeCalculationRequest(resolve(patch.getFeeCode(), before.getFeeCode()))
        .claimId(before.getClaimId() != null ? before.getClaimId().toString() : null)
        .startDate(resolve(patch.getCaseStartDate(), before.getCaseStartDate()))
        .caseConcludedDate(resolve(patch.getCaseConcludedDate(), before.getCaseConcludedDate()))
        .uniqueFileNumber(resolve(patch.getUniqueFileNumber(), before.getUniqueFileNumber()))
        .policeStationId(
            resolve(patch.getPoliceStationCourtPrisonId(), before.getPoliceStationCourtPrisonId()))
        .policeStationSchemeId(resolve(patch.getSchemeId(), before.getSchemeId()))

        // Map financial amounts (BigDecimal -> Double)
        .netProfitCosts(
            toDouble(resolve(patch.getNetProfitCostsAmount(), before.getNetProfitCostsAmount())))
        .netCostOfCounsel(
            toDouble(resolve(patch.getNetCounselCostsAmount(), before.getNetCounselCostsAmount())))
        .netDisbursementAmount(
            toDouble(resolve(patch.getNetDisbursementAmount(), before.getNetDisbursementAmount())))
        .disbursementVatAmount(
            toDouble(
                resolve(patch.getDisbursementsVatAmount(), before.getDisbursementsVatAmount())))
        .jrFormFilling(
            toDouble(resolve(patch.getJrFormFillingAmount(), before.getJrFormFillingAmount())))
        .travelAndWaitingCosts(
            toDouble(
                resolve(patch.getTravelWaitingCostsAmount(), before.getTravelWaitingCostsAmount())))
        .detentionTravelAndWaitingCosts(
            toDouble(
                resolve(
                    patch.getDetentionTravelWaitingCostsAmount(),
                    before.getDetentionTravelWaitingCostsAmount())))

        // Map time allocations (Integer -> Double)
        .netTravelCosts(toDouble(resolve(patch.getTravelTime(), before.getTravelTime())))
        .netWaitingCosts(toDouble(resolve(patch.getWaitingTime(), before.getWaitingTime())))

        // Map remaining identifiers and flags
        .vatIndicator(resolve(patch.getIsVatApplicable(), before.getIsVatApplicable()))
        .londonRate(resolve(patch.getIsLondonRate(), before.getIsLondonRate()))
        .numberOfMediationSessions(
            resolve(patch.getMediationSessionsCount(), before.getMediationSessionsCount()))
        .representationOrderDate(
            resolve(patch.getRepresentationOrderDate(), before.getRepresentationOrderDate()))
        .immigrationPriorAuthorityNumber(
            resolve(patch.getPriorAuthorityReference(), before.getPriorAuthorityReference()))
        .boltOns(boltOns);
  }

  /**
   * Resolves the value to use by checking if the sparse amendment field is defined. Preserves
   * explicit null resets requested by the provider.
   */
  private <T> T resolve(JsonNullable<T> patchValue, T fallbackValue) {
    return (patchValue != null && patchValue.isPresent()) ? patchValue.get() : fallbackValue;
  }

  private Double toDouble(BigDecimal decimal) {
    return decimal == null ? null : decimal.doubleValue();
  }

  private Double toDouble(Integer integer) {
    return integer == null ? null : integer.doubleValue();
  }
}
