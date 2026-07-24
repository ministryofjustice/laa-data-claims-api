package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimCaseFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimSummaryFeeFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClientFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.FeeFields;
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

  /**
   * A lightweight accessor that pairs a stable field identifier with a function that extracts the
   * field's value from a containing object.
   *
   * <p>The identifier is used in emitted {@link DiffEntry} instances and should be the canonical
   * domain name for the field (for example, {@code "claim.feeCode"}). The accessor is invoked
   * against the before/after objects when detecting changes.
   *
   * @param fieldIdentifier stable, machine-readable field identifier used in diffs
   * @param accessor function that returns the field's value from the container object
   */
  private record FieldAccessor<T>(String fieldIdentifier, Function<T, Object> accessor) {}

  /**
   * A group of fields compared between a before and after object of the same type, whose changes
   * are all attributed to the section's {@link ChangeSource}.
   *
   * <p>The {@link #detect(ClaimAmendmentState)} method compares each named field on the resolved
   * before/after objects and emits a {@link DiffEntry} for each value that is considered different
   * (using {@link #valuesEqual(Object, Object)}).
   *
   * @param source section change source (e.g. {@link ChangeSource#REQUESTED} or {@link
   *     ChangeSource#FSP})
   * @param beforeExtractor function that extracts the 'before' object from the amendment state
   * @param afterExtractor function that extracts the 'after' object from the amendment state
   * @param fields the list of fields to compare in this section
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

  /** The ordered sections that are compared when detecting amendment changes. */
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

  /**
   * Convenience factory that builds a {@link FieldAccessor} for the named field.
   *
   * <p>Used when declaring the set of fields inspected by the detector; keeps the field-list
   * declarations compact and readable.
   *
   * @param identifier stable field identifier used in emitted {@link DiffEntry} instances
   * @param accessor function extracting the field value from the section object
   * @param <T> the section object type (e.g. {@link ClaimStateSnapshot} or {@link
   *     CalculatedFeeDetailSnapshot})
   * @return a new {@link FieldAccessor} pairing identifier and accessor
   */
  private static <T> FieldAccessor<T> field(String identifier, Function<T, Object> accessor) {
    return new FieldAccessor<>(identifier, accessor);
  }

  /**
   * Builds the list of claim-state field accessors that are compared in the REQUESTED section.
   *
   * <p>Each entry pairs a stable identifier (the string used in the persisted {@code diff} JSONB)
   * with a method reference that extracts the value from {@link ClaimStateSnapshot}. The list
   * includes claim, client, claim-case and claim-summary-fee scalar fields that are considered
   * provider-amendable.
   *
   * @return an immutable list of {@link FieldAccessor} instances for the claim-state section
   */
  private static List<FieldAccessor<ClaimStateSnapshot>> claimStateFields() {
    List<FieldAccessor<ClaimStateSnapshot>> fields = new ArrayList<>();

    // Claim fields.
    fields.add(field(ClaimFields.SCHEDULE_REFERENCE, ClaimStateSnapshot::getScheduleReference));
    fields.add(field(ClaimFields.LINE_NUMBER, ClaimStateSnapshot::getLineNumber));
    fields.add(
        field(ClaimFields.CASE_REFERENCE_NUMBER, ClaimStateSnapshot::getCaseReferenceNumber));
    fields.add(field(ClaimFields.UNIQUE_FILE_NUMBER, ClaimStateSnapshot::getUniqueFileNumber));
    fields.add(field(ClaimFields.CASE_START_DATE, ClaimStateSnapshot::getCaseStartDate));
    fields.add(field(ClaimFields.CASE_CONCLUDED_DATE, ClaimStateSnapshot::getCaseConcludedDate));
    fields.add(field(ClaimFields.MATTER_TYPE_CODE, ClaimStateSnapshot::getMatterTypeCode));
    fields.add(
        field(ClaimFields.CRIME_MATTER_TYPE_CODE, ClaimStateSnapshot::getCrimeMatterTypeCode));
    fields.add(field(ClaimFields.FEE_SCHEME_CODE, ClaimStateSnapshot::getFeeSchemeCode));
    fields.add(field(ClaimFields.FEE_CODE, ClaimStateSnapshot::getFeeCode));
    fields.add(
        field(ClaimFields.PROCUREMENT_AREA_CODE, ClaimStateSnapshot::getProcurementAreaCode));
    fields.add(field(ClaimFields.ACCESS_POINT_CODE, ClaimStateSnapshot::getAccessPointCode));
    fields.add(field(ClaimFields.DELIVERY_LOCATION, ClaimStateSnapshot::getDeliveryLocation));
    fields.add(
        field(
            ClaimFields.REPRESENTATION_ORDER_DATE, ClaimStateSnapshot::getRepresentationOrderDate));
    fields.add(
        field(
            ClaimFields.SUSPECTS_DEFENDANTS_COUNT, ClaimStateSnapshot::getSuspectsDefendantsCount));
    fields.add(
        field(
            ClaimFields.POLICE_STATION_COURT_ATTENDANCES_COUNT,
            ClaimStateSnapshot::getPoliceStationCourtAttendancesCount));
    fields.add(
        field(
            ClaimFields.POLICE_STATION_COURT_PRISON_ID,
            ClaimStateSnapshot::getPoliceStationCourtPrisonId));
    fields.add(field(ClaimFields.DSCC_NUMBER, ClaimStateSnapshot::getDsccNumber));
    fields.add(field(ClaimFields.MAAT_ID, ClaimStateSnapshot::getMaatId));
    fields.add(
        field(
            ClaimFields.PRISON_LAW_PRIOR_APPROVAL_NUMBER,
            ClaimStateSnapshot::getPrisonLawPriorApprovalNumber));
    fields.add(field(ClaimFields.DUTY_SOLICITOR, ClaimStateSnapshot::getDutySolicitor));
    fields.add(field(ClaimFields.YOUTH_COURT, ClaimStateSnapshot::getYouthCourt));
    fields.add(field(ClaimFields.SCHEME_ID, ClaimStateSnapshot::getSchemeId));
    fields.add(
        field(ClaimFields.MEDIATION_SESSIONS_COUNT, ClaimStateSnapshot::getMediationSessionsCount));
    fields.add(
        field(ClaimFields.MEDIATION_TIME_MINUTES, ClaimStateSnapshot::getMediationTimeMinutes));
    fields.add(field(ClaimFields.OUTREACH_LOCATION, ClaimStateSnapshot::getOutreachLocation));
    fields.add(field(ClaimFields.REFERRAL_SOURCE, ClaimStateSnapshot::getReferralSource));

    // Client fields.
    fields.add(field(ClientFields.CLIENT_FORENAME, ClaimStateSnapshot::getClientForename));
    fields.add(field(ClientFields.CLIENT_SURNAME, ClaimStateSnapshot::getClientSurname));
    fields.add(field(ClientFields.CLIENT_DATE_OF_BIRTH, ClaimStateSnapshot::getClientDateOfBirth));
    fields.add(field(ClientFields.UNIQUE_CLIENT_NUMBER, ClaimStateSnapshot::getUniqueClientNumber));
    fields.add(field(ClientFields.CLIENT_POSTCODE, ClaimStateSnapshot::getClientPostcode));
    fields.add(field(ClientFields.GENDER_CODE, ClaimStateSnapshot::getGenderCode));
    fields.add(field(ClientFields.ETHNICITY_CODE, ClaimStateSnapshot::getEthnicityCode));
    fields.add(field(ClientFields.DISABILITY_CODE, ClaimStateSnapshot::getDisabilityCode));
    fields.add(field(ClientFields.IS_LEGALLY_AIDED, ClaimStateSnapshot::getIsLegallyAided));
    fields.add(field(ClientFields.CLIENT_TYPE_CODE, ClaimStateSnapshot::getClientTypeCode));
    fields.add(
        field(
            ClientFields.HOME_OFFICE_CLIENT_NUMBER, ClaimStateSnapshot::getHomeOfficeClientNumber));
    fields.add(field(ClientFields.CLA_REFERENCE_NUMBER, ClaimStateSnapshot::getClaReferenceNumber));
    fields.add(field(ClientFields.CLA_EXEMPTION_CODE, ClaimStateSnapshot::getClaExemptionCode));
    fields.add(field(ClientFields.CLIENT2_FORENAME, ClaimStateSnapshot::getClient2Forename));
    fields.add(field(ClientFields.CLIENT2_SURNAME, ClaimStateSnapshot::getClient2Surname));
    fields.add(
        field(ClientFields.CLIENT2_DATE_OF_BIRTH, ClaimStateSnapshot::getClient2DateOfBirth));
    fields.add(field(ClientFields.CLIENT2_UCN, ClaimStateSnapshot::getClient2Ucn));
    fields.add(field(ClientFields.CLIENT2_POSTCODE, ClaimStateSnapshot::getClient2Postcode));
    fields.add(field(ClientFields.CLIENT2_GENDER_CODE, ClaimStateSnapshot::getClient2GenderCode));
    fields.add(
        field(ClientFields.CLIENT2_ETHNICITY_CODE, ClaimStateSnapshot::getClient2EthnicityCode));
    fields.add(
        field(ClientFields.CLIENT2_DISABILITY_CODE, ClaimStateSnapshot::getClient2DisabilityCode));
    fields.add(
        field(ClientFields.CLIENT2_IS_LEGALLY_AIDED, ClaimStateSnapshot::getClient2IsLegallyAided));

    // Claim-case fields.
    fields.add(field(ClaimCaseFields.CASE_ID, ClaimStateSnapshot::getCaseId));
    fields.add(field(ClaimCaseFields.UNIQUE_CASE_ID, ClaimStateSnapshot::getUniqueCaseId));
    fields.add(field(ClaimCaseFields.CASE_STAGE_CODE, ClaimStateSnapshot::getCaseStageCode));
    fields.add(field(ClaimCaseFields.STAGE_REACHED_CODE, ClaimStateSnapshot::getStageReachedCode));
    fields.add(
        field(
            ClaimCaseFields.STANDARD_FEE_CATEGORY_CODE,
            ClaimStateSnapshot::getStandardFeeCategoryCode));
    fields.add(field(ClaimCaseFields.OUTCOME_CODE, ClaimStateSnapshot::getOutcomeCode));
    fields.add(
        field(
            ClaimCaseFields.DESIGNATED_ACCREDITED_REPRESENTATIVE_CODE,
            ClaimStateSnapshot::getDesignatedAccreditedRepresentativeCode));
    fields.add(
        field(
            ClaimCaseFields.IS_POSTAL_APPLICATION_ACCEPTED,
            ClaimStateSnapshot::getIsPostalApplicationAccepted));
    fields.add(
        field(
            ClaimCaseFields.IS_CLIENT2_POSTAL_APPLICATION_ACCEPTED,
            ClaimStateSnapshot::getIsClient2PostalApplicationAccepted));
    fields.add(
        field(
            ClaimCaseFields.MENTAL_HEALTH_TRIBUNAL_REFERENCE,
            ClaimStateSnapshot::getMentalHealthTribunalReference));
    fields.add(field(ClaimCaseFields.IS_NRM_ADVICE, ClaimStateSnapshot::getIsNrmAdvice));
    fields.add(field(ClaimCaseFields.FOLLOW_ON_WORK, ClaimStateSnapshot::getFollowOnWork));
    fields.add(field(ClaimCaseFields.TRANSFER_DATE, ClaimStateSnapshot::getTransferDate));
    fields.add(
        field(
            ClaimCaseFields.EXEMPTION_CRITERIA_SATISFIED,
            ClaimStateSnapshot::getExemptionCriteriaSatisfied));
    fields.add(
        field(
            ClaimCaseFields.EXCEPTIONAL_CASE_FUNDING_REFERENCE,
            ClaimStateSnapshot::getExceptionalCaseFundingReference));
    fields.add(field(ClaimCaseFields.IS_LEGACY_CASE, ClaimStateSnapshot::getIsLegacyCase));

    // Claim-summary-fee fields.
    fields.add(field(ClaimSummaryFeeFields.ADVICE_TIME, ClaimStateSnapshot::getAdviceTime));
    fields.add(field(ClaimSummaryFeeFields.TRAVEL_TIME, ClaimStateSnapshot::getTravelTime));
    fields.add(field(ClaimSummaryFeeFields.WAITING_TIME, ClaimStateSnapshot::getWaitingTime));
    fields.add(
        field(
            ClaimSummaryFeeFields.NET_PROFIT_COSTS_AMOUNT,
            ClaimStateSnapshot::getNetProfitCostsAmount));
    fields.add(
        field(
            ClaimSummaryFeeFields.NET_DISBURSEMENT_AMOUNT,
            ClaimStateSnapshot::getNetDisbursementAmount));
    fields.add(
        field(
            ClaimSummaryFeeFields.NET_COUNSEL_COSTS_AMOUNT,
            ClaimStateSnapshot::getNetCounselCostsAmount));
    fields.add(
        field(
            ClaimSummaryFeeFields.DISBURSEMENTS_VAT_AMOUNT,
            ClaimStateSnapshot::getDisbursementsVatAmount));
    fields.add(
        field(
            ClaimSummaryFeeFields.TRAVEL_WAITING_COSTS_AMOUNT,
            ClaimStateSnapshot::getTravelWaitingCostsAmount));
    fields.add(
        field(
            ClaimSummaryFeeFields.NET_WAITING_COSTS_AMOUNT,
            ClaimStateSnapshot::getNetWaitingCostsAmount));
    fields.add(
        field(ClaimSummaryFeeFields.IS_VAT_APPLICABLE, ClaimStateSnapshot::getIsVatApplicable));
    fields.add(
        field(
            ClaimSummaryFeeFields.IS_TOLERANCE_APPLICABLE,
            ClaimStateSnapshot::getIsToleranceApplicable));
    fields.add(
        field(
            ClaimSummaryFeeFields.PRIOR_AUTHORITY_REFERENCE,
            ClaimStateSnapshot::getPriorAuthorityReference));
    fields.add(field(ClaimSummaryFeeFields.IS_LONDON_RATE, ClaimStateSnapshot::getIsLondonRate));
    fields.add(
        field(
            ClaimSummaryFeeFields.ADJOURNED_HEARING_FEE_AMOUNT,
            ClaimStateSnapshot::getAdjournedHearingFeeAmount));
    fields.add(
        field(
            ClaimSummaryFeeFields.IS_ADDITIONAL_TRAVEL_PAYMENT,
            ClaimStateSnapshot::getIsAdditionalTravelPayment));
    fields.add(
        field(
            ClaimSummaryFeeFields.COSTS_DAMAGES_RECOVERED_AMOUNT,
            ClaimStateSnapshot::getCostsDamagesRecoveredAmount));
    fields.add(
        field(
            ClaimSummaryFeeFields.MEETINGS_ATTENDED_CODE,
            ClaimStateSnapshot::getMeetingsAttendedCode));
    fields.add(
        field(
            ClaimSummaryFeeFields.DETENTION_TRAVEL_WAITING_COSTS_AMOUNT,
            ClaimStateSnapshot::getDetentionTravelWaitingCostsAmount));
    fields.add(
        field(
            ClaimSummaryFeeFields.JR_FORM_FILLING_AMOUNT,
            ClaimStateSnapshot::getJrFormFillingAmount));
    fields.add(
        field(ClaimSummaryFeeFields.IS_ELIGIBLE_CLIENT, ClaimStateSnapshot::getIsEligibleClient));
    fields.add(
        field(ClaimSummaryFeeFields.COURT_LOCATION_CODE, ClaimStateSnapshot::getCourtLocationCode));
    fields.add(
        field(ClaimSummaryFeeFields.ADVICE_TYPE_CODE, ClaimStateSnapshot::getAdviceTypeCode));
    fields.add(
        field(
            ClaimSummaryFeeFields.MEDICAL_REPORTS_COUNT,
            ClaimStateSnapshot::getMedicalReportsCount));
    fields.add(field(ClaimSummaryFeeFields.IS_IRC_SURGERY, ClaimStateSnapshot::getIsIrcSurgery));
    fields.add(field(ClaimSummaryFeeFields.SURGERY_DATE, ClaimStateSnapshot::getSurgeryDate));
    fields.add(
        field(
            ClaimSummaryFeeFields.SURGERY_CLIENTS_COUNT,
            ClaimStateSnapshot::getSurgeryClientsCount));
    fields.add(
        field(
            ClaimSummaryFeeFields.SURGERY_MATTERS_COUNT,
            ClaimStateSnapshot::getSurgeryMattersCount));
    fields.add(field(ClaimSummaryFeeFields.CMRH_ORAL_COUNT, ClaimStateSnapshot::getCmrhOralCount));
    fields.add(
        field(
            ClaimSummaryFeeFields.CMRH_TELEPHONE_COUNT, ClaimStateSnapshot::getCmrhTelephoneCount));
    fields.add(
        field(
            ClaimSummaryFeeFields.AIT_HEARING_CENTRE_CODE,
            ClaimStateSnapshot::getAitHearingCentreCode));
    fields.add(
        field(
            ClaimSummaryFeeFields.IS_SUBSTANTIVE_HEARING,
            ClaimStateSnapshot::getIsSubstantiveHearing));
    fields.add(field(ClaimSummaryFeeFields.HO_INTERVIEW, ClaimStateSnapshot::getHoInterview));
    fields.add(
        field(
            ClaimSummaryFeeFields.LOCAL_AUTHORITY_NUMBER,
            ClaimStateSnapshot::getLocalAuthorityNumber));

    return List.copyOf(fields);
  }

  /**
   * Builds the list of calculated-fee field accessors that are compared in the FSP section.
   *
   * <p>Fields here are sourced from the fee snapshot returned by the Fee Scheme Platform; changes
   * to these values are attributed to {@link ChangeSource#FSP}.
   *
   * @return an immutable list of {@link FieldAccessor} instances for the FSP fee section
   */
  private static List<FieldAccessor<CalculatedFeeDetailSnapshot>> feeFields() {
    List<FieldAccessor<CalculatedFeeDetailSnapshot>> fields = new ArrayList<>();

    fields.add(field(FeeFields.FEE_CODE, CalculatedFeeDetailSnapshot::getFeeCode));
    fields.add(field(FeeFields.FEE_TYPE, CalculatedFeeDetailSnapshot::getFeeType));
    fields.add(
        field(FeeFields.FEE_CODE_DESCRIPTION, CalculatedFeeDetailSnapshot::getFeeCodeDescription));
    fields.add(field(FeeFields.CATEGORY_OF_LAW, CalculatedFeeDetailSnapshot::getCategoryOfLaw));
    fields.add(field(FeeFields.TOTAL_AMOUNT, CalculatedFeeDetailSnapshot::getTotalAmount));
    fields.add(field(FeeFields.VAT_INDICATOR, CalculatedFeeDetailSnapshot::getVatIndicator));
    fields.add(field(FeeFields.VAT_RATE_APPLIED, CalculatedFeeDetailSnapshot::getVatRateApplied));
    fields.add(
        field(
            FeeFields.CALCULATED_VAT_AMOUNT, CalculatedFeeDetailSnapshot::getCalculatedVatAmount));
    fields.add(
        field(FeeFields.DISBURSEMENT_AMOUNT, CalculatedFeeDetailSnapshot::getDisbursementAmount));
    fields.add(
        field(
            FeeFields.REQUESTED_NET_DISBURSEMENT_AMOUNT,
            CalculatedFeeDetailSnapshot::getRequestedNetDisbursementAmount));
    fields.add(
        field(
            FeeFields.DISBURSEMENT_VAT_AMOUNT,
            CalculatedFeeDetailSnapshot::getDisbursementVatAmount));
    fields.add(
        field(FeeFields.HOURLY_TOTAL_AMOUNT, CalculatedFeeDetailSnapshot::getHourlyTotalAmount));
    fields.add(field(FeeFields.FIXED_FEE_AMOUNT, CalculatedFeeDetailSnapshot::getFixedFeeAmount));
    fields.add(
        field(
            FeeFields.NET_PROFIT_COSTS_AMOUNT,
            CalculatedFeeDetailSnapshot::getNetProfitCostsAmount));
    fields.add(
        field(
            FeeFields.REQUESTED_NET_PROFIT_COSTS_AMOUNT,
            CalculatedFeeDetailSnapshot::getRequestedNetProfitCostsAmount));
    fields.add(
        field(
            FeeFields.NET_COST_OF_COUNSEL_AMOUNT,
            CalculatedFeeDetailSnapshot::getNetCostOfCounselAmount));
    fields.add(
        field(
            FeeFields.NET_TRAVEL_COSTS_AMOUNT,
            CalculatedFeeDetailSnapshot::getNetTravelCostsAmount));
    fields.add(
        field(
            FeeFields.NET_WAITING_COSTS_AMOUNT,
            CalculatedFeeDetailSnapshot::getNetWaitingCostsAmount));
    fields.add(
        field(
            FeeFields.DETENTION_TRAVEL_AND_WAITING_COSTS_AMOUNT,
            CalculatedFeeDetailSnapshot::getDetentionTravelAndWaitingCostsAmount));
    fields.add(
        field(
            FeeFields.JR_FORM_FILLING_AMOUNT, CalculatedFeeDetailSnapshot::getJrFormFillingAmount));
    fields.add(
        field(
            FeeFields.TRAVEL_AND_WAITING_COSTS_AMOUNT,
            CalculatedFeeDetailSnapshot::getTravelAndWaitingCostsAmount));
    fields.add(
        field(
            FeeFields.BOLT_ON_TOTAL_FEE_AMOUNT,
            CalculatedFeeDetailSnapshot::getBoltOnTotalFeeAmount));
    fields.add(
        field(
            FeeFields.BOLT_ON_ADJOURNED_HEARING_COUNT,
            CalculatedFeeDetailSnapshot::getBoltOnAdjournedHearingCount));
    fields.add(
        field(
            FeeFields.BOLT_ON_ADJOURNED_HEARING_FEE,
            CalculatedFeeDetailSnapshot::getBoltOnAdjournedHearingFee));
    fields.add(
        field(
            FeeFields.BOLT_ON_CMRH_TELEPHONE_COUNT,
            CalculatedFeeDetailSnapshot::getBoltOnCmrhTelephoneCount));
    fields.add(
        field(
            FeeFields.BOLT_ON_CMRH_TELEPHONE_FEE,
            CalculatedFeeDetailSnapshot::getBoltOnCmrhTelephoneFee));
    fields.add(
        field(
            FeeFields.BOLT_ON_CMRH_ORAL_COUNT,
            CalculatedFeeDetailSnapshot::getBoltOnCmrhOralCount));
    fields.add(
        field(FeeFields.BOLT_ON_CMRH_ORAL_FEE, CalculatedFeeDetailSnapshot::getBoltOnCmrhOralFee));
    fields.add(
        field(
            FeeFields.BOLT_ON_HOME_OFFICE_INTERVIEW_COUNT,
            CalculatedFeeDetailSnapshot::getBoltOnHomeOfficeInterviewCount));
    fields.add(
        field(
            FeeFields.BOLT_ON_HOME_OFFICE_INTERVIEW_FEE,
            CalculatedFeeDetailSnapshot::getBoltOnHomeOfficeInterviewFee));
    fields.add(
        field(
            FeeFields.BOLT_ON_SUBSTANTIVE_HEARING_FEE,
            CalculatedFeeDetailSnapshot::getBoltOnSubstantiveHearingFee));
    fields.add(field(FeeFields.ESCAPE_CASE_FLAG, CalculatedFeeDetailSnapshot::getEscapeCaseFlag));
    fields.add(field(FeeFields.SCHEME_ID, CalculatedFeeDetailSnapshot::getSchemeId));

    return List.copyOf(fields);
  }
}
