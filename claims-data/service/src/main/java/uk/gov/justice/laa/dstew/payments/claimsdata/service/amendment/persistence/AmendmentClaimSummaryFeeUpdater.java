package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;

/**
 * Applies the amended provider-entered {@code claim_summary_fee}-table values from the
 * post-amendment state onto the managed {@link ClaimSummaryFee} entity.
 *
 * <p>Every amendable claim-summary-fee column is copied from the post-amendment snapshot, which
 * already encodes the sparse-payload semantics (omitted fields retain their stored value; an
 * explicit {@code null} clears the field). Identity, audit and calculated-fee columns are left
 * untouched, and this component never issues its own save.
 */
@Component
public class AmendmentClaimSummaryFeeUpdater {

  /**
   * Copies the amendable {@code claim_summary_fee}-table fields from the post-amendment state onto
   * the managed claim-summary-fee entity.
   *
   * @param summaryFee the managed claim-summary-fee entity to mutate (not saved here)
   * @param postAmendmentState the proposed post-amendment values
   */
  public void applyAmendedFields(
      ClaimSummaryFee summaryFee, ClaimStateSnapshot postAmendmentState) {
    summaryFee.setAdviceTime(postAmendmentState.getAdviceTime());
    summaryFee.setTravelTime(postAmendmentState.getTravelTime());
    summaryFee.setWaitingTime(postAmendmentState.getWaitingTime());
    summaryFee.setNetProfitCostsAmount(postAmendmentState.getNetProfitCostsAmount());
    summaryFee.setNetDisbursementAmount(postAmendmentState.getNetDisbursementAmount());
    summaryFee.setNetCounselCostsAmount(postAmendmentState.getNetCounselCostsAmount());
    summaryFee.setDisbursementsVatAmount(postAmendmentState.getDisbursementsVatAmount());
    summaryFee.setTravelWaitingCostsAmount(postAmendmentState.getTravelWaitingCostsAmount());
    summaryFee.setNetWaitingCostsAmount(postAmendmentState.getNetWaitingCostsAmount());
    summaryFee.setIsVatApplicable(postAmendmentState.getIsVatApplicable());
    summaryFee.setIsToleranceApplicable(postAmendmentState.getIsToleranceApplicable());
    summaryFee.setPriorAuthorityReference(postAmendmentState.getPriorAuthorityReference());
    summaryFee.setIsLondonRate(postAmendmentState.getIsLondonRate());
    summaryFee.setAdjournedHearingFeeAmount(postAmendmentState.getAdjournedHearingFeeAmount());
    summaryFee.setIsAdditionalTravelPayment(postAmendmentState.getIsAdditionalTravelPayment());
    summaryFee.setCostsDamagesRecoveredAmount(postAmendmentState.getCostsDamagesRecoveredAmount());
    summaryFee.setMeetingsAttendedCode(postAmendmentState.getMeetingsAttendedCode());
    summaryFee.setDetentionTravelWaitingCostsAmount(
        postAmendmentState.getDetentionTravelWaitingCostsAmount());
    summaryFee.setJrFormFillingAmount(postAmendmentState.getJrFormFillingAmount());
    summaryFee.setIsEligibleClient(postAmendmentState.getIsEligibleClient());
    summaryFee.setCourtLocationCode(postAmendmentState.getCourtLocationCode());
    summaryFee.setAdviceTypeCode(postAmendmentState.getAdviceTypeCode());
    summaryFee.setMedicalReportsCount(postAmendmentState.getMedicalReportsCount());
    summaryFee.setIsIrcSurgery(postAmendmentState.getIsIrcSurgery());
    summaryFee.setSurgeryDate(postAmendmentState.getSurgeryDate());
    summaryFee.setSurgeryClientsCount(postAmendmentState.getSurgeryClientsCount());
    summaryFee.setSurgeryMattersCount(postAmendmentState.getSurgeryMattersCount());
    summaryFee.setCmrhOralCount(postAmendmentState.getCmrhOralCount());
    summaryFee.setCmrhTelephoneCount(postAmendmentState.getCmrhTelephoneCount());
    summaryFee.setAitHearingCentreCode(postAmendmentState.getAitHearingCentreCode());
    summaryFee.setIsSubstantiveHearing(postAmendmentState.getIsSubstantiveHearing());
    summaryFee.setHoInterview(postAmendmentState.getHoInterview());
    summaryFee.setLocalAuthorityNumber(postAmendmentState.getLocalAuthorityNumber());
  }
}
