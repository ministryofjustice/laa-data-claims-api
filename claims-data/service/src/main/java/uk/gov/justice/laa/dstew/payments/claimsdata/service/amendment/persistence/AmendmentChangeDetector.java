package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ChangeSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;

/**
 * Derives the provider-requested changed fields of an amendment by comparing the before-state with
 * the post-amendment state field by field, emitting one {@link DiffEntry} per changed field tagged
 * {@link ChangeSource#REQUESTED}.
 *
 * <p>Because the post-amendment state is the sparse payload already applied onto the before-state
 * (omitted fields retained, explicit nulls cleared, values set), comparing the two yields exactly
 * the changed fields: an omitted field is unchanged and absent from the diff; an explicit null over
 * a non-null value is a clear; a value differing from the stored value is a set. No-op
 * resubmissions (a value equal to the stored value) produce no change - the diff carries changed
 * fields only.
 */
@Component
public class AmendmentChangeDetector {

  private record FieldDescriptor(
      String fieldIdentifier, Function<ClaimStateSnapshot, Object> accessor) {}

  private static final List<FieldDescriptor> FIELDS = buildFieldDescriptors();

  /**
   * Detects the provider-requested changed fields for the given amendment.
   *
   * @param state the in-memory amendment state (before/after snapshots)
   * @return the changed fields as {@link ChangeSource#REQUESTED} diff entries; never {@code null},
   *     may be empty
   */
  public List<DiffEntry> detectChanges(ClaimAmendmentState state) {
    ClaimStateSnapshot before = state.getBeforeState();
    ClaimStateSnapshot after = state.getPostAmendmentState();
    if (before == null || after == null) {
      return List.of();
    }

    List<DiffEntry> changes = new ArrayList<>();
    for (FieldDescriptor field : FIELDS) {
      Object beforeValue = field.accessor().apply(before);
      Object afterValue = field.accessor().apply(after);
      if (!Objects.equals(beforeValue, afterValue)) {
        changes.add(
            new DiffEntry(
                field.fieldIdentifier(), ChangeSource.REQUESTED, beforeValue, afterValue));
      }
    }
    return changes;
  }

  private static FieldDescriptor field(
      String identifier, Function<ClaimStateSnapshot, Object> accessor) {
    return new FieldDescriptor(identifier, accessor);
  }

  private static List<FieldDescriptor> buildFieldDescriptors() {
    List<FieldDescriptor> fields = new ArrayList<>();

    // Claim fields.
    fields.add(field("claim.scheduleReference", ClaimStateSnapshot::getScheduleReference));
    fields.add(field("claim.lineNumber", ClaimStateSnapshot::getLineNumber));
    fields.add(field("claim.caseReferenceNumber", ClaimStateSnapshot::getCaseReferenceNumber));
    fields.add(field("claim.uniqueFileNumber", ClaimStateSnapshot::getUniqueFileNumber));
    fields.add(field("claim.caseStartDate", ClaimStateSnapshot::getCaseStartDate));
    fields.add(field("claim.caseConcludedDate", ClaimStateSnapshot::getCaseConcludedDate));
    fields.add(field("claim.matterTypeCode", ClaimStateSnapshot::getMatterTypeCode));
    fields.add(field("claim.crimeMatterTypeCode", ClaimStateSnapshot::getCrimeMatterTypeCode));
    fields.add(field("claim.feeSchemeCode", ClaimStateSnapshot::getFeeSchemeCode));
    fields.add(field("claim.feeCode", ClaimStateSnapshot::getFeeCode));
    fields.add(field("claim.procurementAreaCode", ClaimStateSnapshot::getProcurementAreaCode));
    fields.add(field("claim.accessPointCode", ClaimStateSnapshot::getAccessPointCode));
    fields.add(field("claim.deliveryLocation", ClaimStateSnapshot::getDeliveryLocation));
    fields.add(
        field("claim.representationOrderDate", ClaimStateSnapshot::getRepresentationOrderDate));
    fields.add(
        field("claim.suspectsDefendantsCount", ClaimStateSnapshot::getSuspectsDefendantsCount));
    fields.add(
        field(
            "claim.policeStationCourtAttendancesCount",
            ClaimStateSnapshot::getPoliceStationCourtAttendancesCount));
    fields.add(
        field(
            "claim.policeStationCourtPrisonId", ClaimStateSnapshot::getPoliceStationCourtPrisonId));
    fields.add(field("claim.dsccNumber", ClaimStateSnapshot::getDsccNumber));
    fields.add(field("claim.maatId", ClaimStateSnapshot::getMaatId));
    fields.add(
        field(
            "claim.prisonLawPriorApprovalNumber",
            ClaimStateSnapshot::getPrisonLawPriorApprovalNumber));
    fields.add(field("claim.dutySolicitor", ClaimStateSnapshot::getDutySolicitor));
    fields.add(field("claim.youthCourt", ClaimStateSnapshot::getYouthCourt));
    fields.add(field("claim.schemeId", ClaimStateSnapshot::getSchemeId));
    fields.add(
        field("claim.mediationSessionsCount", ClaimStateSnapshot::getMediationSessionsCount));
    fields.add(field("claim.mediationTimeMinutes", ClaimStateSnapshot::getMediationTimeMinutes));
    fields.add(field("claim.outreachLocation", ClaimStateSnapshot::getOutreachLocation));
    fields.add(field("claim.referralSource", ClaimStateSnapshot::getReferralSource));

    // Client fields.
    fields.add(field("client.clientForename", ClaimStateSnapshot::getClientForename));
    fields.add(field("client.clientSurname", ClaimStateSnapshot::getClientSurname));
    fields.add(field("client.clientDateOfBirth", ClaimStateSnapshot::getClientDateOfBirth));
    fields.add(field("client.uniqueClientNumber", ClaimStateSnapshot::getUniqueClientNumber));
    fields.add(field("client.clientPostcode", ClaimStateSnapshot::getClientPostcode));
    fields.add(field("client.genderCode", ClaimStateSnapshot::getGenderCode));
    fields.add(field("client.ethnicityCode", ClaimStateSnapshot::getEthnicityCode));
    fields.add(field("client.disabilityCode", ClaimStateSnapshot::getDisabilityCode));
    fields.add(field("client.isLegallyAided", ClaimStateSnapshot::getIsLegallyAided));
    fields.add(field("client.clientTypeCode", ClaimStateSnapshot::getClientTypeCode));
    fields.add(
        field("client.homeOfficeClientNumber", ClaimStateSnapshot::getHomeOfficeClientNumber));
    fields.add(field("client.claReferenceNumber", ClaimStateSnapshot::getClaReferenceNumber));
    fields.add(field("client.claExemptionCode", ClaimStateSnapshot::getClaExemptionCode));
    fields.add(field("client.client2Forename", ClaimStateSnapshot::getClient2Forename));
    fields.add(field("client.client2Surname", ClaimStateSnapshot::getClient2Surname));
    fields.add(field("client.client2DateOfBirth", ClaimStateSnapshot::getClient2DateOfBirth));
    fields.add(field("client.client2Ucn", ClaimStateSnapshot::getClient2Ucn));
    fields.add(field("client.client2Postcode", ClaimStateSnapshot::getClient2Postcode));
    fields.add(field("client.client2GenderCode", ClaimStateSnapshot::getClient2GenderCode));
    fields.add(field("client.client2EthnicityCode", ClaimStateSnapshot::getClient2EthnicityCode));
    fields.add(field("client.client2DisabilityCode", ClaimStateSnapshot::getClient2DisabilityCode));
    fields.add(field("client.client2IsLegallyAided", ClaimStateSnapshot::getClient2IsLegallyAided));

    // Claim-case fields.
    fields.add(field("claimCase.caseId", ClaimStateSnapshot::getCaseId));
    fields.add(field("claimCase.uniqueCaseId", ClaimStateSnapshot::getUniqueCaseId));
    fields.add(field("claimCase.caseStageCode", ClaimStateSnapshot::getCaseStageCode));
    fields.add(field("claimCase.stageReachedCode", ClaimStateSnapshot::getStageReachedCode));
    fields.add(
        field("claimCase.standardFeeCategoryCode", ClaimStateSnapshot::getStandardFeeCategoryCode));
    fields.add(field("claimCase.outcomeCode", ClaimStateSnapshot::getOutcomeCode));
    fields.add(
        field(
            "claimCase.designatedAccreditedRepresentativeCode",
            ClaimStateSnapshot::getDesignatedAccreditedRepresentativeCode));
    fields.add(
        field(
            "claimCase.isPostalApplicationAccepted",
            ClaimStateSnapshot::getIsPostalApplicationAccepted));
    fields.add(
        field(
            "claimCase.isClient2PostalApplicationAccepted",
            ClaimStateSnapshot::getIsClient2PostalApplicationAccepted));
    fields.add(
        field(
            "claimCase.mentalHealthTribunalReference",
            ClaimStateSnapshot::getMentalHealthTribunalReference));
    fields.add(field("claimCase.isNrmAdvice", ClaimStateSnapshot::getIsNrmAdvice));
    fields.add(field("claimCase.followOnWork", ClaimStateSnapshot::getFollowOnWork));
    fields.add(field("claimCase.transferDate", ClaimStateSnapshot::getTransferDate));
    fields.add(
        field(
            "claimCase.exemptionCriteriaSatisfied",
            ClaimStateSnapshot::getExemptionCriteriaSatisfied));
    fields.add(
        field(
            "claimCase.exceptionalCaseFundingReference",
            ClaimStateSnapshot::getExceptionalCaseFundingReference));
    fields.add(field("claimCase.isLegacyCase", ClaimStateSnapshot::getIsLegacyCase));

    // Claim-summary-fee fields.
    fields.add(field("claimSummaryFee.adviceTime", ClaimStateSnapshot::getAdviceTime));
    fields.add(field("claimSummaryFee.travelTime", ClaimStateSnapshot::getTravelTime));
    fields.add(field("claimSummaryFee.waitingTime", ClaimStateSnapshot::getWaitingTime));
    fields.add(
        field("claimSummaryFee.netProfitCostsAmount", ClaimStateSnapshot::getNetProfitCostsAmount));
    fields.add(
        field(
            "claimSummaryFee.netDisbursementAmount", ClaimStateSnapshot::getNetDisbursementAmount));
    fields.add(
        field(
            "claimSummaryFee.netCounselCostsAmount", ClaimStateSnapshot::getNetCounselCostsAmount));
    fields.add(
        field(
            "claimSummaryFee.disbursementsVatAmount",
            ClaimStateSnapshot::getDisbursementsVatAmount));
    fields.add(
        field(
            "claimSummaryFee.travelWaitingCostsAmount",
            ClaimStateSnapshot::getTravelWaitingCostsAmount));
    fields.add(
        field(
            "claimSummaryFee.netWaitingCostsAmount", ClaimStateSnapshot::getNetWaitingCostsAmount));
    fields.add(field("claimSummaryFee.isVatApplicable", ClaimStateSnapshot::getIsVatApplicable));
    fields.add(
        field(
            "claimSummaryFee.isToleranceApplicable", ClaimStateSnapshot::getIsToleranceApplicable));
    fields.add(
        field(
            "claimSummaryFee.priorAuthorityReference",
            ClaimStateSnapshot::getPriorAuthorityReference));
    fields.add(field("claimSummaryFee.isLondonRate", ClaimStateSnapshot::getIsLondonRate));
    fields.add(
        field(
            "claimSummaryFee.adjournedHearingFeeAmount",
            ClaimStateSnapshot::getAdjournedHearingFeeAmount));
    fields.add(
        field(
            "claimSummaryFee.isAdditionalTravelPayment",
            ClaimStateSnapshot::getIsAdditionalTravelPayment));
    fields.add(
        field(
            "claimSummaryFee.costsDamagesRecoveredAmount",
            ClaimStateSnapshot::getCostsDamagesRecoveredAmount));
    fields.add(
        field("claimSummaryFee.meetingsAttendedCode", ClaimStateSnapshot::getMeetingsAttendedCode));
    fields.add(
        field(
            "claimSummaryFee.detentionTravelWaitingCostsAmount",
            ClaimStateSnapshot::getDetentionTravelWaitingCostsAmount));
    fields.add(
        field("claimSummaryFee.jrFormFillingAmount", ClaimStateSnapshot::getJrFormFillingAmount));
    fields.add(field("claimSummaryFee.isEligibleClient", ClaimStateSnapshot::getIsEligibleClient));
    fields.add(
        field("claimSummaryFee.courtLocationCode", ClaimStateSnapshot::getCourtLocationCode));
    fields.add(field("claimSummaryFee.adviceTypeCode", ClaimStateSnapshot::getAdviceTypeCode));
    fields.add(
        field("claimSummaryFee.medicalReportsCount", ClaimStateSnapshot::getMedicalReportsCount));
    fields.add(field("claimSummaryFee.isIrcSurgery", ClaimStateSnapshot::getIsIrcSurgery));
    fields.add(field("claimSummaryFee.surgeryDate", ClaimStateSnapshot::getSurgeryDate));
    fields.add(
        field("claimSummaryFee.surgeryClientsCount", ClaimStateSnapshot::getSurgeryClientsCount));
    fields.add(
        field("claimSummaryFee.surgeryMattersCount", ClaimStateSnapshot::getSurgeryMattersCount));
    fields.add(field("claimSummaryFee.cmrhOralCount", ClaimStateSnapshot::getCmrhOralCount));
    fields.add(
        field("claimSummaryFee.cmrhTelephoneCount", ClaimStateSnapshot::getCmrhTelephoneCount));
    fields.add(
        field("claimSummaryFee.aitHearingCentreCode", ClaimStateSnapshot::getAitHearingCentreCode));
    fields.add(
        field("claimSummaryFee.isSubstantiveHearing", ClaimStateSnapshot::getIsSubstantiveHearing));
    fields.add(field("claimSummaryFee.hoInterview", ClaimStateSnapshot::getHoInterview));
    fields.add(
        field("claimSummaryFee.localAuthorityNumber", ClaimStateSnapshot::getLocalAuthorityNumber));

    return List.copyOf(fields);
  }
}
