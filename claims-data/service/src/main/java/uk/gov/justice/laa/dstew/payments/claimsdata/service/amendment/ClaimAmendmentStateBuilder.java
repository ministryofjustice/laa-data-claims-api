package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;

/**
 * Builds the in-memory post-amendment state for a claim by applying a sparse amendment payload onto
 * the current (before) state.
 *
 * <p>Sparse-payload semantics (see {@link ClaimAmendmentPayload}):
 *
 * <ul>
 *   <li><b>omitted</b> field ({@code JsonNullable.undefined()}) &rarr; the stored value is
 *       retained;
 *   <li><b>explicit null</b> ({@code JsonNullable.of(null)}) &rarr; the value is cleared (kept as
 *       {@code null} for later validation);
 *   <li><b>value present</b> &rarr; the submitted value is applied.
 * </ul>
 *
 * <p>UCN ({@code uniqueClientNumber}) and UFN ({@code uniqueFileNumber}) are treated like any other
 * field: they change only when explicitly present in the payload. They are never recomputed from
 * client forename, surname, date of birth or case id - applying each field independently guarantees
 * this.
 *
 * <p>Read-only context (identity, lifecycle status, submission context, category of law, calculated
 * fee detail and latest assessment) is carried over unchanged from the before-state.
 *
 * <p>This component performs no persistence and makes no external calls.
 */
@Component
public class ClaimAmendmentStateBuilder {

  /**
   * Applies the sparse payload onto the before-state and returns the proposed post-amendment state.
   *
   * @param beforeState the current stored claim state
   * @param payload the sparse amendment payload as submitted
   * @return the proposed post-amendment state
   */
  ClaimStateSnapshot buildPostAmendmentState(
      ClaimStateSnapshot beforeState, ClaimAmendmentPayload payload) {

    return beforeState.toBuilder()
        // claim fields
        .scheduleReference(
            resolve(payload.getScheduleReference(), beforeState.getScheduleReference()))
        .lineNumber(resolve(payload.getLineNumber(), beforeState.getLineNumber()))
        .caseReferenceNumber(
            resolve(payload.getCaseReferenceNumber(), beforeState.getCaseReferenceNumber()))
        .uniqueFileNumber(resolve(payload.getUniqueFileNumber(), beforeState.getUniqueFileNumber()))
        .caseStartDate(resolve(payload.getCaseStartDate(), beforeState.getCaseStartDate()))
        .caseConcludedDate(
            resolve(payload.getCaseConcludedDate(), beforeState.getCaseConcludedDate()))
        .matterTypeCode(resolve(payload.getMatterTypeCode(), beforeState.getMatterTypeCode()))
        .crimeMatterTypeCode(
            resolve(payload.getCrimeMatterTypeCode(), beforeState.getCrimeMatterTypeCode()))
        .feeSchemeCode(resolve(payload.getFeeSchemeCode(), beforeState.getFeeSchemeCode()))
        .feeCode(resolve(payload.getFeeCode(), beforeState.getFeeCode()))
        .procurementAreaCode(
            resolve(payload.getProcurementAreaCode(), beforeState.getProcurementAreaCode()))
        .accessPointCode(resolve(payload.getAccessPointCode(), beforeState.getAccessPointCode()))
        .deliveryLocation(resolve(payload.getDeliveryLocation(), beforeState.getDeliveryLocation()))
        .representationOrderDate(
            resolve(payload.getRepresentationOrderDate(), beforeState.getRepresentationOrderDate()))
        .suspectsDefendantsCount(
            resolve(payload.getSuspectsDefendantsCount(), beforeState.getSuspectsDefendantsCount()))
        .policeStationCourtAttendancesCount(
            resolve(
                payload.getPoliceStationCourtAttendancesCount(),
                beforeState.getPoliceStationCourtAttendancesCount()))
        .policeStationCourtPrisonId(
            resolve(
                payload.getPoliceStationCourtPrisonId(),
                beforeState.getPoliceStationCourtPrisonId()))
        .dsccNumber(resolve(payload.getDsccNumber(), beforeState.getDsccNumber()))
        .maatId(resolve(payload.getMaatId(), beforeState.getMaatId()))
        .prisonLawPriorApprovalNumber(
            resolve(
                payload.getPrisonLawPriorApprovalNumber(),
                beforeState.getPrisonLawPriorApprovalNumber()))
        .dutySolicitor(resolve(payload.getIsDutySolicitor(), beforeState.getDutySolicitor()))
        .youthCourt(resolve(payload.getIsYouthCourt(), beforeState.getYouthCourt()))
        .schemeId(resolve(payload.getSchemeId(), beforeState.getSchemeId()))
        .mediationSessionsCount(
            resolve(payload.getMediationSessionsCount(), beforeState.getMediationSessionsCount()))
        .mediationTimeMinutes(
            resolve(payload.getMediationTimeMinutes(), beforeState.getMediationTimeMinutes()))
        .outreachLocation(resolve(payload.getOutreachLocation(), beforeState.getOutreachLocation()))
        .referralSource(resolve(payload.getReferralSource(), beforeState.getReferralSource()))
        // client fields
        .clientForename(resolve(payload.getClientForename(), beforeState.getClientForename()))
        .clientSurname(resolve(payload.getClientSurname(), beforeState.getClientSurname()))
        .clientDateOfBirth(
            resolve(payload.getClientDateOfBirth(), beforeState.getClientDateOfBirth()))
        .uniqueClientNumber(
            resolve(payload.getUniqueClientNumber(), beforeState.getUniqueClientNumber()))
        .clientPostcode(resolve(payload.getClientPostcode(), beforeState.getClientPostcode()))
        .genderCode(resolve(payload.getGenderCode(), beforeState.getGenderCode()))
        .ethnicityCode(resolve(payload.getEthnicityCode(), beforeState.getEthnicityCode()))
        .disabilityCode(resolve(payload.getDisabilityCode(), beforeState.getDisabilityCode()))
        .isLegallyAided(resolve(payload.getIsLegallyAided(), beforeState.getIsLegallyAided()))
        .clientTypeCode(resolve(payload.getClientTypeCode(), beforeState.getClientTypeCode()))
        .homeOfficeClientNumber(
            resolve(payload.getHomeOfficeClientNumber(), beforeState.getHomeOfficeClientNumber()))
        .claReferenceNumber(
            resolve(payload.getClaReferenceNumber(), beforeState.getClaReferenceNumber()))
        .claExemptionCode(resolve(payload.getClaExemptionCode(), beforeState.getClaExemptionCode()))
        .client2Forename(resolve(payload.getClient2Forename(), beforeState.getClient2Forename()))
        .client2Surname(resolve(payload.getClient2Surname(), beforeState.getClient2Surname()))
        .client2DateOfBirth(
            resolve(payload.getClient2DateOfBirth(), beforeState.getClient2DateOfBirth()))
        .client2Ucn(resolve(payload.getClient2Ucn(), beforeState.getClient2Ucn()))
        .client2Postcode(resolve(payload.getClient2Postcode(), beforeState.getClient2Postcode()))
        .client2GenderCode(
            resolve(payload.getClient2GenderCode(), beforeState.getClient2GenderCode()))
        .client2EthnicityCode(
            resolve(payload.getClient2EthnicityCode(), beforeState.getClient2EthnicityCode()))
        .client2DisabilityCode(
            resolve(payload.getClient2DisabilityCode(), beforeState.getClient2DisabilityCode()))
        .client2IsLegallyAided(
            resolve(payload.getClient2IsLegallyAided(), beforeState.getClient2IsLegallyAided()))
        // claim-case fields
        .caseId(resolve(payload.getCaseId(), beforeState.getCaseId()))
        .uniqueCaseId(resolve(payload.getUniqueCaseId(), beforeState.getUniqueCaseId()))
        .caseStageCode(resolve(payload.getCaseStageCode(), beforeState.getCaseStageCode()))
        .stageReachedCode(resolve(payload.getStageReachedCode(), beforeState.getStageReachedCode()))
        .standardFeeCategoryCode(
            resolve(payload.getStandardFeeCategoryCode(), beforeState.getStandardFeeCategoryCode()))
        .outcomeCode(resolve(payload.getOutcomeCode(), beforeState.getOutcomeCode()))
        .designatedAccreditedRepresentativeCode(
            resolve(
                payload.getDesignatedAccreditedRepresentativeCode(),
                beforeState.getDesignatedAccreditedRepresentativeCode()))
        .isPostalApplicationAccepted(
            resolve(
                payload.getIsPostalApplicationAccepted(),
                beforeState.getIsPostalApplicationAccepted()))
        .isClient2PostalApplicationAccepted(
            resolve(
                payload.getIsClient2PostalApplicationAccepted(),
                beforeState.getIsClient2PostalApplicationAccepted()))
        .mentalHealthTribunalReference(
            resolve(
                payload.getMentalHealthTribunalReference(),
                beforeState.getMentalHealthTribunalReference()))
        .isNrmAdvice(resolve(payload.getIsNrmAdvice(), beforeState.getIsNrmAdvice()))
        .followOnWork(resolve(payload.getFollowOnWork(), beforeState.getFollowOnWork()))
        .transferDate(resolve(payload.getTransferDate(), beforeState.getTransferDate()))
        .exemptionCriteriaSatisfied(
            resolve(
                payload.getExemptionCriteriaSatisfied(),
                beforeState.getExemptionCriteriaSatisfied()))
        .exceptionalCaseFundingReference(
            resolve(
                payload.getExceptionalCaseFundingReference(),
                beforeState.getExceptionalCaseFundingReference()))
        .isLegacyCase(resolve(payload.getIsLegacyCase(), beforeState.getIsLegacyCase()))
        // claim-summary-fee fields
        .adviceTime(resolve(payload.getAdviceTime(), beforeState.getAdviceTime()))
        .travelTime(resolve(payload.getTravelTime(), beforeState.getTravelTime()))
        .waitingTime(resolve(payload.getWaitingTime(), beforeState.getWaitingTime()))
        .netProfitCostsAmount(
            resolve(payload.getNetProfitCostsAmount(), beforeState.getNetProfitCostsAmount()))
        .netDisbursementAmount(
            resolve(payload.getNetDisbursementAmount(), beforeState.getNetDisbursementAmount()))
        .netCounselCostsAmount(
            resolve(payload.getNetCounselCostsAmount(), beforeState.getNetCounselCostsAmount()))
        .disbursementsVatAmount(
            resolve(payload.getDisbursementsVatAmount(), beforeState.getDisbursementsVatAmount()))
        .travelWaitingCostsAmount(
            resolve(
                payload.getTravelWaitingCostsAmount(), beforeState.getTravelWaitingCostsAmount()))
        .netWaitingCostsAmount(
            resolve(payload.getNetWaitingCostsAmount(), beforeState.getNetWaitingCostsAmount()))
        .isVatApplicable(resolve(payload.getIsVatApplicable(), beforeState.getIsVatApplicable()))
        .isToleranceApplicable(
            resolve(payload.getIsToleranceApplicable(), beforeState.getIsToleranceApplicable()))
        .priorAuthorityReference(
            resolve(payload.getPriorAuthorityReference(), beforeState.getPriorAuthorityReference()))
        .isLondonRate(resolve(payload.getIsLondonRate(), beforeState.getIsLondonRate()))
        .adjournedHearingFeeAmount(
            resolve(
                payload.getAdjournedHearingFeeAmount(), beforeState.getAdjournedHearingFeeAmount()))
        .isAdditionalTravelPayment(
            resolve(
                payload.getIsAdditionalTravelPayment(), beforeState.getIsAdditionalTravelPayment()))
        .costsDamagesRecoveredAmount(
            resolve(
                payload.getCostsDamagesRecoveredAmount(),
                beforeState.getCostsDamagesRecoveredAmount()))
        .meetingsAttendedCode(
            resolve(payload.getMeetingsAttendedCode(), beforeState.getMeetingsAttendedCode()))
        .detentionTravelWaitingCostsAmount(
            resolve(
                payload.getDetentionTravelWaitingCostsAmount(),
                beforeState.getDetentionTravelWaitingCostsAmount()))
        .jrFormFillingAmount(
            resolve(payload.getJrFormFillingAmount(), beforeState.getJrFormFillingAmount()))
        .isEligibleClient(resolve(payload.getIsEligibleClient(), beforeState.getIsEligibleClient()))
        .courtLocationCode(
            resolve(payload.getCourtLocationCode(), beforeState.getCourtLocationCode()))
        .adviceTypeCode(resolve(payload.getAdviceTypeCode(), beforeState.getAdviceTypeCode()))
        .medicalReportsCount(
            resolve(payload.getMedicalReportsCount(), beforeState.getMedicalReportsCount()))
        .isIrcSurgery(resolve(payload.getIsIrcSurgery(), beforeState.getIsIrcSurgery()))
        .surgeryDate(resolve(payload.getSurgeryDate(), beforeState.getSurgeryDate()))
        .surgeryClientsCount(
            resolve(payload.getSurgeryClientsCount(), beforeState.getSurgeryClientsCount()))
        .surgeryMattersCount(
            resolve(payload.getSurgeryMattersCount(), beforeState.getSurgeryMattersCount()))
        .cmrhOralCount(resolve(payload.getCmrhOralCount(), beforeState.getCmrhOralCount()))
        .cmrhTelephoneCount(
            resolve(payload.getCmrhTelephoneCount(), beforeState.getCmrhTelephoneCount()))
        .aitHearingCentreCode(
            resolve(payload.getAitHearingCentreCode(), beforeState.getAitHearingCentreCode()))
        .isSubstantiveHearing(
            resolve(payload.getIsSubstantiveHearing(), beforeState.getIsSubstantiveHearing()))
        .hoInterview(resolve(payload.getHoInterview(), beforeState.getHoInterview()))
        .localAuthorityNumber(
            resolve(payload.getLocalAuthorityNumber(), beforeState.getLocalAuthorityNumber()))
        .build();
  }

  /**
   * Builds the full amendment aggregate bundling the before-state, the request payload and the
   * computed post-amendment state.
   *
   * @param beforeState the current stored claim state
   * @param payload the sparse amendment payload as submitted
   * @return the amendment state aggregate
   */
  public ClaimAmendmentState buildAmendmentState(
      ClaimStateSnapshot beforeState, ClaimAmendmentPayload payload) {

    return ClaimAmendmentState.builder()
        .beforeState(beforeState)
        .requestPayload(payload)
        .postAmendmentState(buildPostAmendmentState(beforeState, payload))
        .build();
  }

  /**
   * Resolves a single field: returns the submitted value when present (including an explicit null),
   * otherwise retains the current value.
   *
   * @param submitted the submitted tri-state value
   * @param current the current stored value
   * @param <T> the field type
   * @return the resolved value for the post-amendment state
   */
  private static <T> T resolve(JsonNullable<T> submitted, T current) {
    return submitted.isPresent() ? submitted.get() : current;
  }
}
