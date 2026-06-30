package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.state;

import java.util.function.Consumer;
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

    // The builder starts as a copy of the before-state, so each helper only needs to overwrite the
    // fields the payload actually carries; omitted fields keep their before-state value.
    ClaimStateSnapshot.ClaimStateSnapshotBuilder builder = beforeState.toBuilder();
    applyClaimFields(builder, payload);
    applyClientFields(builder, payload);
    applyClaimCaseFields(builder, payload);
    applyClaimSummaryFeeFields(builder, payload);
    return builder.build();
  }

  /** Applies the provider-amendable claim fields onto the post-amendment builder. */
  private static void applyClaimFields(
      ClaimStateSnapshot.ClaimStateSnapshotBuilder builder, ClaimAmendmentPayload payload) {

    applyIfPresent(payload.getScheduleReference(), builder::scheduleReference);
    applyIfPresent(payload.getLineNumber(), builder::lineNumber);
    applyIfPresent(payload.getCaseReferenceNumber(), builder::caseReferenceNumber);
    applyIfPresent(payload.getUniqueFileNumber(), builder::uniqueFileNumber);
    applyIfPresent(payload.getCaseStartDate(), builder::caseStartDate);
    applyIfPresent(payload.getCaseConcludedDate(), builder::caseConcludedDate);
    applyIfPresent(payload.getMatterTypeCode(), builder::matterTypeCode);
    applyIfPresent(payload.getCrimeMatterTypeCode(), builder::crimeMatterTypeCode);
    applyIfPresent(payload.getFeeSchemeCode(), builder::feeSchemeCode);
    applyIfPresent(payload.getFeeCode(), builder::feeCode);
    applyIfPresent(payload.getProcurementAreaCode(), builder::procurementAreaCode);
    applyIfPresent(payload.getAccessPointCode(), builder::accessPointCode);
    applyIfPresent(payload.getDeliveryLocation(), builder::deliveryLocation);
    applyIfPresent(payload.getRepresentationOrderDate(), builder::representationOrderDate);
    applyIfPresent(payload.getSuspectsDefendantsCount(), builder::suspectsDefendantsCount);
    applyIfPresent(
        payload.getPoliceStationCourtAttendancesCount(),
        builder::policeStationCourtAttendancesCount);
    applyIfPresent(payload.getPoliceStationCourtPrisonId(), builder::policeStationCourtPrisonId);
    applyIfPresent(payload.getDsccNumber(), builder::dsccNumber);
    applyIfPresent(payload.getMaatId(), builder::maatId);
    applyIfPresent(
        payload.getPrisonLawPriorApprovalNumber(), builder::prisonLawPriorApprovalNumber);
    applyIfPresent(payload.getIsDutySolicitor(), builder::dutySolicitor);
    applyIfPresent(payload.getIsYouthCourt(), builder::youthCourt);
    applyIfPresent(payload.getSchemeId(), builder::schemeId);
    applyIfPresent(payload.getMediationSessionsCount(), builder::mediationSessionsCount);
    applyIfPresent(payload.getMediationTimeMinutes(), builder::mediationTimeMinutes);
    applyIfPresent(payload.getOutreachLocation(), builder::outreachLocation);
    applyIfPresent(payload.getReferralSource(), builder::referralSource);
  }

  /** Applies the provider-amendable client fields onto the post-amendment builder. */
  private static void applyClientFields(
      ClaimStateSnapshot.ClaimStateSnapshotBuilder builder, ClaimAmendmentPayload payload) {

    applyIfPresent(payload.getClientForename(), builder::clientForename);
    applyIfPresent(payload.getClientSurname(), builder::clientSurname);
    applyIfPresent(payload.getClientDateOfBirth(), builder::clientDateOfBirth);
    applyIfPresent(payload.getUniqueClientNumber(), builder::uniqueClientNumber);
    applyIfPresent(payload.getClientPostcode(), builder::clientPostcode);
    applyIfPresent(payload.getGenderCode(), builder::genderCode);
    applyIfPresent(payload.getEthnicityCode(), builder::ethnicityCode);
    applyIfPresent(payload.getDisabilityCode(), builder::disabilityCode);
    applyIfPresent(payload.getIsLegallyAided(), builder::isLegallyAided);
    applyIfPresent(payload.getClientTypeCode(), builder::clientTypeCode);
    applyIfPresent(payload.getHomeOfficeClientNumber(), builder::homeOfficeClientNumber);
    applyIfPresent(payload.getClaReferenceNumber(), builder::claReferenceNumber);
    applyIfPresent(payload.getClaExemptionCode(), builder::claExemptionCode);
    applyIfPresent(payload.getClient2Forename(), builder::client2Forename);
    applyIfPresent(payload.getClient2Surname(), builder::client2Surname);
    applyIfPresent(payload.getClient2DateOfBirth(), builder::client2DateOfBirth);
    applyIfPresent(payload.getClient2Ucn(), builder::client2Ucn);
    applyIfPresent(payload.getClient2Postcode(), builder::client2Postcode);
    applyIfPresent(payload.getClient2GenderCode(), builder::client2GenderCode);
    applyIfPresent(payload.getClient2EthnicityCode(), builder::client2EthnicityCode);
    applyIfPresent(payload.getClient2DisabilityCode(), builder::client2DisabilityCode);
    applyIfPresent(payload.getClient2IsLegallyAided(), builder::client2IsLegallyAided);
  }

  /** Applies the provider-amendable claim-case fields onto the post-amendment builder. */
  private static void applyClaimCaseFields(
      ClaimStateSnapshot.ClaimStateSnapshotBuilder builder, ClaimAmendmentPayload payload) {

    applyIfPresent(payload.getCaseId(), builder::caseId);
    applyIfPresent(payload.getUniqueCaseId(), builder::uniqueCaseId);
    applyIfPresent(payload.getCaseStageCode(), builder::caseStageCode);
    applyIfPresent(payload.getStageReachedCode(), builder::stageReachedCode);
    applyIfPresent(payload.getStandardFeeCategoryCode(), builder::standardFeeCategoryCode);
    applyIfPresent(payload.getOutcomeCode(), builder::outcomeCode);
    applyIfPresent(
        payload.getDesignatedAccreditedRepresentativeCode(),
        builder::designatedAccreditedRepresentativeCode);
    applyIfPresent(payload.getIsPostalApplicationAccepted(), builder::isPostalApplicationAccepted);
    applyIfPresent(
        payload.getIsClient2PostalApplicationAccepted(),
        builder::isClient2PostalApplicationAccepted);
    applyIfPresent(
        payload.getMentalHealthTribunalReference(), builder::mentalHealthTribunalReference);
    applyIfPresent(payload.getIsNrmAdvice(), builder::isNrmAdvice);
    applyIfPresent(payload.getFollowOnWork(), builder::followOnWork);
    applyIfPresent(payload.getTransferDate(), builder::transferDate);
    applyIfPresent(payload.getExemptionCriteriaSatisfied(), builder::exemptionCriteriaSatisfied);
    applyIfPresent(
        payload.getExceptionalCaseFundingReference(), builder::exceptionalCaseFundingReference);
    applyIfPresent(payload.getIsLegacyCase(), builder::isLegacyCase);
  }

  /** Applies the provider-amendable claim-summary-fee fields onto the post-amendment builder. */
  private static void applyClaimSummaryFeeFields(
      ClaimStateSnapshot.ClaimStateSnapshotBuilder builder, ClaimAmendmentPayload payload) {

    applyIfPresent(payload.getAdviceTime(), builder::adviceTime);
    applyIfPresent(payload.getTravelTime(), builder::travelTime);
    applyIfPresent(payload.getWaitingTime(), builder::waitingTime);
    applyIfPresent(payload.getNetProfitCostsAmount(), builder::netProfitCostsAmount);
    applyIfPresent(payload.getNetDisbursementAmount(), builder::netDisbursementAmount);
    applyIfPresent(payload.getNetCounselCostsAmount(), builder::netCounselCostsAmount);
    applyIfPresent(payload.getDisbursementsVatAmount(), builder::disbursementsVatAmount);
    applyIfPresent(payload.getTravelWaitingCostsAmount(), builder::travelWaitingCostsAmount);
    applyIfPresent(payload.getNetWaitingCostsAmount(), builder::netWaitingCostsAmount);
    applyIfPresent(payload.getIsVatApplicable(), builder::isVatApplicable);
    applyIfPresent(payload.getIsToleranceApplicable(), builder::isToleranceApplicable);
    applyIfPresent(payload.getPriorAuthorityReference(), builder::priorAuthorityReference);
    applyIfPresent(payload.getIsLondonRate(), builder::isLondonRate);
    applyIfPresent(payload.getAdjournedHearingFeeAmount(), builder::adjournedHearingFeeAmount);
    applyIfPresent(payload.getIsAdditionalTravelPayment(), builder::isAdditionalTravelPayment);
    applyIfPresent(payload.getCostsDamagesRecoveredAmount(), builder::costsDamagesRecoveredAmount);
    applyIfPresent(payload.getMeetingsAttendedCode(), builder::meetingsAttendedCode);
    applyIfPresent(
        payload.getDetentionTravelWaitingCostsAmount(), builder::detentionTravelWaitingCostsAmount);
    applyIfPresent(payload.getJrFormFillingAmount(), builder::jrFormFillingAmount);
    applyIfPresent(payload.getIsEligibleClient(), builder::isEligibleClient);
    applyIfPresent(payload.getCourtLocationCode(), builder::courtLocationCode);
    applyIfPresent(payload.getAdviceTypeCode(), builder::adviceTypeCode);
    applyIfPresent(payload.getMedicalReportsCount(), builder::medicalReportsCount);
    applyIfPresent(payload.getIsIrcSurgery(), builder::isIrcSurgery);
    applyIfPresent(payload.getSurgeryDate(), builder::surgeryDate);
    applyIfPresent(payload.getSurgeryClientsCount(), builder::surgeryClientsCount);
    applyIfPresent(payload.getSurgeryMattersCount(), builder::surgeryMattersCount);
    applyIfPresent(payload.getCmrhOralCount(), builder::cmrhOralCount);
    applyIfPresent(payload.getCmrhTelephoneCount(), builder::cmrhTelephoneCount);
    applyIfPresent(payload.getAitHearingCentreCode(), builder::aitHearingCentreCode);
    applyIfPresent(payload.getIsSubstantiveHearing(), builder::isSubstantiveHearing);
    applyIfPresent(payload.getHoInterview(), builder::hoInterview);
    applyIfPresent(payload.getLocalAuthorityNumber(), builder::localAuthorityNumber);
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
   * Applies a single submitted field onto the builder, but only when the payload carries it.
   *
   * <p>The builder is pre-seeded with the before-state, so an omitted field ({@code
   * JsonNullable.undefined()}) is left untouched and retains its stored value. A present value -
   * including an explicit {@code null} - overwrites it (an explicit null clears the field for later
   * validation).
   *
   * <p>A {@code null} wrapper (only reachable if a payload is built programmatically with a field
   * explicitly set to {@code null}, bypassing the {@code JsonNullable.undefined()} defaults) is
   * treated the same as if that field were omitted: the before-state value is retained.
   *
   * @param submitted the submitted tri-state value
   * @param setter the builder setter for the corresponding field
   * @param <T> the field type
   */
  private static <T> void applyIfPresent(JsonNullable<T> submitted, Consumer<T> setter) {
    if (submitted != null && submitted.isPresent()) {
      setter.accept(submitted.get());
    }
  }
}
