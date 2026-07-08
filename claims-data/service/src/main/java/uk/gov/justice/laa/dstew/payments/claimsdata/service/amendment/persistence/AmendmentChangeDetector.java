package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ChangeSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;

/**
 * Derives the changed fields of an amendment by comparing before and after values, section by
 * section, emitting one {@link DiffEntry} per changed field tagged with that section's {@link
 * ChangeSource}.
 *
 * <p>Two sections are compared:
 *
 * <ul>
 *   <li>the provider-requested claim state - the before-state against the post-amendment state,
 *       tagged {@link ChangeSource#REQUESTED} (DSTEW-1766). Because the post-amendment state is the
 *       sparse payload already applied onto the before-state (omitted fields retained, explicit
 *       nulls cleared, values set), comparing the two yields exactly the changed fields: an omitted
 *       field is unchanged and absent from the diff; an explicit null over a non-null value is a
 *       clear; a value differing from the stored value is a set; a no-op resubmission produces no
 *       change.
 *   <li>the Fee Scheme Platform calculated fee - the before-fee against the after-fee, tagged
 *       {@link ChangeSource#FSP} (DSTEW-1762). The FSP handoff supplies both fee snapshots; if
 *       either side is {@code null} the section yields no changes.
 * </ul>
 */
@Component
public class AmendmentChangeDetector {

  /** A single comparable field within a {@link DiffSection}. */
  private record FieldAccessor<T>(String fieldIdentifier, Function<T, Object> accessor) {}

  /**
   * A group of fields compared between a before and after object of the same type, whose changes
   * are all attributed to the section's {@link ChangeSource}.
   */
  private record DiffSection<T>(
      ChangeSource source,
      Function<ClaimAmendmentState, T> beforeExtractor,
      Function<ClaimAmendmentState, T> afterExtractor,
      List<FieldAccessor<T>> fields) {

    List<DiffEntry> detect(ClaimAmendmentState state) {
      T before = beforeExtractor.apply(state);
      T after = afterExtractor.apply(state);
      if (before == null || after == null) {
        return List.of();
      }
      List<DiffEntry> changes = new ArrayList<>();
      for (FieldAccessor<T> field : fields) {
        Object beforeValue = field.accessor().apply(before);
        Object afterValue = field.accessor().apply(after);
        if (!valuesEqual(beforeValue, afterValue)) {
          changes.add(new DiffEntry(field.fieldIdentifier(), source, beforeValue, afterValue));
        }
      }
      return changes;
    }
  }

  /**
   * Value equality for change detection. {@link BigDecimal} values are compared with {@link
   * BigDecimal#compareTo} so that numerically equal amounts differing only in scale (e.g. {@code
   * 1.0} versus {@code 1.00}) are treated as unchanged - avoiding spurious diff entries for money
   * fields whose scale can differ between the stored value and an external (FSP) response. All
   * other types fall back to {@link Objects#equals}.
   *
   * @param before the before value (may be {@code null})
   * @param after the after value (may be {@code null})
   * @return {@code true} if the two values are considered equal for diffing
   */
  private static boolean valuesEqual(Object before, Object after) {
    if (before instanceof BigDecimal beforeAmount && after instanceof BigDecimal afterAmount) {
      return beforeAmount.compareTo(afterAmount) == 0;
    }
    return Objects.equals(before, after);
  }

  /** Provider-requested claim-state changes: before-state versus post-amendment state. */
  private static final DiffSection<ClaimStateSnapshot> REQUESTED_CLAIM_STATE_SECTION =
      new DiffSection<>(
          ChangeSource.REQUESTED,
          ClaimAmendmentState::getBeforeState,
          ClaimAmendmentState::getPostAmendmentState,
          claimStateFields());

  /** FSP calculated-fee consequences: before-fee versus after-fee. */
  private static final DiffSection<CalculatedFeeDetailSnapshot> FSP_FEE_SECTION =
      new DiffSection<>(
          ChangeSource.FSP,
          ClaimAmendmentState::getBeforeFee,
          ClaimAmendmentState::getAfterFee,
          feeFields());

  private static final List<DiffSection<?>> SECTIONS =
      List.of(REQUESTED_CLAIM_STATE_SECTION, FSP_FEE_SECTION);

  /**
   * Detects the changed fields for the given amendment across all sections.
   *
   * @param state the in-memory amendment state (before/after snapshots and fee snapshots)
   * @return the changed fields as {@link DiffEntry} entries tagged by section {@link ChangeSource};
   *     never {@code null}, may be empty
   */
  public List<DiffEntry> detectChanges(ClaimAmendmentState state) {
    List<DiffEntry> changes = new ArrayList<>();
    for (DiffSection<?> section : SECTIONS) {
      changes.addAll(section.detect(state));
    }
    return changes;
  }

  private static <T> FieldAccessor<T> field(String identifier, Function<T, Object> accessor) {
    return new FieldAccessor<>(identifier, accessor);
  }

  private static List<FieldAccessor<ClaimStateSnapshot>> claimStateFields() {
    List<FieldAccessor<ClaimStateSnapshot>> fields = new ArrayList<>();

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

  private static List<FieldAccessor<CalculatedFeeDetailSnapshot>> feeFields() {
    List<FieldAccessor<CalculatedFeeDetailSnapshot>> fields = new ArrayList<>();

    fields.add(field("fee.feeCode", CalculatedFeeDetailSnapshot::getFeeCode));
    fields.add(field("fee.feeType", CalculatedFeeDetailSnapshot::getFeeType));
    fields.add(field("fee.feeCodeDescription", CalculatedFeeDetailSnapshot::getFeeCodeDescription));
    fields.add(field("fee.categoryOfLaw", CalculatedFeeDetailSnapshot::getCategoryOfLaw));
    fields.add(field("fee.totalAmount", CalculatedFeeDetailSnapshot::getTotalAmount));
    fields.add(field("fee.vatIndicator", CalculatedFeeDetailSnapshot::getVatIndicator));
    fields.add(field("fee.vatRateApplied", CalculatedFeeDetailSnapshot::getVatRateApplied));
    fields.add(
        field("fee.calculatedVatAmount", CalculatedFeeDetailSnapshot::getCalculatedVatAmount));
    fields.add(field("fee.disbursementAmount", CalculatedFeeDetailSnapshot::getDisbursementAmount));
    fields.add(
        field(
            "fee.requestedNetDisbursementAmount",
            CalculatedFeeDetailSnapshot::getRequestedNetDisbursementAmount));
    fields.add(
        field("fee.disbursementVatAmount", CalculatedFeeDetailSnapshot::getDisbursementVatAmount));
    fields.add(field("fee.hourlyTotalAmount", CalculatedFeeDetailSnapshot::getHourlyTotalAmount));
    fields.add(field("fee.fixedFeeAmount", CalculatedFeeDetailSnapshot::getFixedFeeAmount));
    fields.add(
        field("fee.netProfitCostsAmount", CalculatedFeeDetailSnapshot::getNetProfitCostsAmount));
    fields.add(
        field(
            "fee.requestedNetProfitCostsAmount",
            CalculatedFeeDetailSnapshot::getRequestedNetProfitCostsAmount));
    fields.add(
        field(
            "fee.netCostOfCounselAmount", CalculatedFeeDetailSnapshot::getNetCostOfCounselAmount));
    fields.add(
        field("fee.netTravelCostsAmount", CalculatedFeeDetailSnapshot::getNetTravelCostsAmount));
    fields.add(
        field("fee.netWaitingCostsAmount", CalculatedFeeDetailSnapshot::getNetWaitingCostsAmount));
    fields.add(
        field(
            "fee.detentionTravelAndWaitingCostsAmount",
            CalculatedFeeDetailSnapshot::getDetentionTravelAndWaitingCostsAmount));
    fields.add(
        field("fee.jrFormFillingAmount", CalculatedFeeDetailSnapshot::getJrFormFillingAmount));
    fields.add(
        field(
            "fee.travelAndWaitingCostsAmount",
            CalculatedFeeDetailSnapshot::getTravelAndWaitingCostsAmount));
    fields.add(
        field("fee.boltOnTotalFeeAmount", CalculatedFeeDetailSnapshot::getBoltOnTotalFeeAmount));
    fields.add(
        field(
            "fee.boltOnAdjournedHearingCount",
            CalculatedFeeDetailSnapshot::getBoltOnAdjournedHearingCount));
    fields.add(
        field(
            "fee.boltOnAdjournedHearingFee",
            CalculatedFeeDetailSnapshot::getBoltOnAdjournedHearingFee));
    fields.add(
        field(
            "fee.boltOnCmrhTelephoneCount",
            CalculatedFeeDetailSnapshot::getBoltOnCmrhTelephoneCount));
    fields.add(
        field(
            "fee.boltOnCmrhTelephoneFee", CalculatedFeeDetailSnapshot::getBoltOnCmrhTelephoneFee));
    fields.add(
        field("fee.boltOnCmrhOralCount", CalculatedFeeDetailSnapshot::getBoltOnCmrhOralCount));
    fields.add(field("fee.boltOnCmrhOralFee", CalculatedFeeDetailSnapshot::getBoltOnCmrhOralFee));
    fields.add(
        field(
            "fee.boltOnHomeOfficeInterviewCount",
            CalculatedFeeDetailSnapshot::getBoltOnHomeOfficeInterviewCount));
    fields.add(
        field(
            "fee.boltOnHomeOfficeInterviewFee",
            CalculatedFeeDetailSnapshot::getBoltOnHomeOfficeInterviewFee));
    fields.add(
        field(
            "fee.boltOnSubstantiveHearingFee",
            CalculatedFeeDetailSnapshot::getBoltOnSubstantiveHearingFee));
    fields.add(field("fee.escapeCaseFlag", CalculatedFeeDetailSnapshot::getEscapeCaseFlag));
    fields.add(field("fee.schemeId", CalculatedFeeDetailSnapshot::getSchemeId));

    return List.copyOf(fields);
  }
}
