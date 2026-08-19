package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

/**
 * Single source of truth for the stable amendment <em>diff field identifiers</em> - the {@code
 * "section.fieldName"} strings emitted in a {@link DiffEntry} and persisted in the {@code diff}
 * JSONB column.
 *
 * <p>These identifiers are shared across the amendment machinery so the vocabulary stays in
 * lock-step:
 *
 * <ul>
 *   <li>{@code AmendmentChangeDetector} emits them when building the diff;
 *   <li>{@code PdaRequestField} matches them to decide whether a change affects the PDA request;
 *   <li>{@code FeeSchemeRequestField} matches them to decide whether a change affects FSP pricing.
 * </ul>
 *
 * <p>Grouped by the entity/section they belong to. All values are compile-time constants so they
 * may be used as {@code switch} case labels and in enum constant declarations.
 */
public final class AmendmentFieldIdentifiers {

  private AmendmentFieldIdentifiers() {
    // Constants holder; no instances.
  }

  /** Provider-amendable {@code claim.*} field identifiers. */
  public static final class ClaimFields {

    private ClaimFields() {}

    public static final String SCHEDULE_REFERENCE = "claim.scheduleReference";
    public static final String LINE_NUMBER = "claim.lineNumber";
    public static final String CASE_REFERENCE_NUMBER = "claim.caseReferenceNumber";
    public static final String UNIQUE_FILE_NUMBER = "claim.uniqueFileNumber";
    public static final String CASE_START_DATE = "claim.caseStartDate";
    public static final String CASE_CONCLUDED_DATE = "claim.caseConcludedDate";
    public static final String MATTER_TYPE_CODE = "claim.matterTypeCode";
    public static final String CRIME_MATTER_TYPE_CODE = "claim.crimeMatterTypeCode";
    public static final String FEE_SCHEME_CODE = "claim.feeSchemeCode";
    public static final String FEE_CODE = "claim.feeCode";
    public static final String PROCUREMENT_AREA_CODE = "claim.procurementAreaCode";
    public static final String ACCESS_POINT_CODE = "claim.accessPointCode";
    public static final String DELIVERY_LOCATION = "claim.deliveryLocation";
    public static final String REPRESENTATION_ORDER_DATE = "claim.representationOrderDate";
    public static final String SUSPECTS_DEFENDANTS_COUNT = "claim.suspectsDefendantsCount";
    public static final String POLICE_STATION_COURT_ATTENDANCES_COUNT =
        "claim.policeStationCourtAttendancesCount";
    public static final String POLICE_STATION_COURT_PRISON_ID = "claim.policeStationCourtPrisonId";
    public static final String DSCC_NUMBER = "claim.dsccNumber";
    public static final String MAAT_ID = "claim.maatId";
    public static final String PRISON_LAW_PRIOR_APPROVAL_NUMBER =
        "claim.prisonLawPriorApprovalNumber";
    public static final String DUTY_SOLICITOR = "claim.dutySolicitor";
    public static final String YOUTH_COURT = "claim.youthCourt";
    public static final String SCHEME_ID = "claim.schemeId";
    public static final String MEDIATION_SESSIONS_COUNT = "claim.mediationSessionsCount";
    public static final String MEDIATION_TIME_MINUTES = "claim.mediationTimeMinutes";
    public static final String OUTREACH_LOCATION = "claim.outreachLocation";
    public static final String REFERRAL_SOURCE = "claim.referralSource";
  }

  /** Provider-amendable {@code client.*} field identifiers. */
  public static final class ClientFields {

    private ClientFields() {}

    public static final String CLIENT_FORENAME = "client.clientForename";
    public static final String CLIENT_SURNAME = "client.clientSurname";
    public static final String CLIENT_DATE_OF_BIRTH = "client.clientDateOfBirth";
    public static final String UNIQUE_CLIENT_NUMBER = "client.uniqueClientNumber";
    public static final String CLIENT_POSTCODE = "client.clientPostcode";
    public static final String GENDER_CODE = "client.genderCode";
    public static final String ETHNICITY_CODE = "client.ethnicityCode";
    public static final String DISABILITY_CODE = "client.disabilityCode";
    public static final String IS_LEGALLY_AIDED = "client.isLegallyAided";
    public static final String CLIENT_TYPE_CODE = "client.clientTypeCode";
    public static final String HOME_OFFICE_CLIENT_NUMBER = "client.homeOfficeClientNumber";
    public static final String CLA_REFERENCE_NUMBER = "client.claReferenceNumber";
    public static final String CLA_EXEMPTION_CODE = "client.claExemptionCode";
    public static final String CLIENT2_FORENAME = "client.client2Forename";
    public static final String CLIENT2_SURNAME = "client.client2Surname";
    public static final String CLIENT2_DATE_OF_BIRTH = "client.client2DateOfBirth";
    public static final String CLIENT2_UCN = "client.client2Ucn";
    public static final String CLIENT2_POSTCODE = "client.client2Postcode";
    public static final String CLIENT2_GENDER_CODE = "client.client2GenderCode";
    public static final String CLIENT2_ETHNICITY_CODE = "client.client2EthnicityCode";
    public static final String CLIENT2_DISABILITY_CODE = "client.client2DisabilityCode";
    public static final String CLIENT2_IS_LEGALLY_AIDED = "client.client2IsLegallyAided";
  }

  /** Provider-amendable {@code claimCase.*} field identifiers. */
  public static final class ClaimCaseFields {

    private ClaimCaseFields() {}

    public static final String CASE_ID = "claimCase.caseId";
    public static final String UNIQUE_CASE_ID = "claimCase.uniqueCaseId";
    public static final String CASE_STAGE_CODE = "claimCase.caseStageCode";
    public static final String STAGE_REACHED_CODE = "claimCase.stageReachedCode";
    public static final String STANDARD_FEE_CATEGORY_CODE = "claimCase.standardFeeCategoryCode";
    public static final String OUTCOME_CODE = "claimCase.outcomeCode";
    public static final String DESIGNATED_ACCREDITED_REPRESENTATIVE_CODE =
        "claimCase.designatedAccreditedRepresentativeCode";
    public static final String IS_POSTAL_APPLICATION_ACCEPTED =
        "claimCase.isPostalApplicationAccepted";
    public static final String IS_CLIENT2_POSTAL_APPLICATION_ACCEPTED =
        "claimCase.isClient2PostalApplicationAccepted";
    public static final String MENTAL_HEALTH_TRIBUNAL_REFERENCE =
        "claimCase.mentalHealthTribunalReference";
    public static final String IS_NRM_ADVICE = "claimCase.isNrmAdvice";
    public static final String FOLLOW_ON_WORK = "claimCase.followOnWork";
    public static final String TRANSFER_DATE = "claimCase.transferDate";
    public static final String EXEMPTION_CRITERIA_SATISFIED =
        "claimCase.exemptionCriteriaSatisfied";
    public static final String EXCEPTIONAL_CASE_FUNDING_REFERENCE =
        "claimCase.exceptionalCaseFundingReference";
    public static final String IS_LEGACY_CASE = "claimCase.isLegacyCase";
  }

  /** Provider-amendable {@code claimSummaryFee.*} field identifiers. */
  public static final class ClaimSummaryFeeFields {

    private ClaimSummaryFeeFields() {}

    public static final String ADVICE_TIME = "claimSummaryFee.adviceTime";
    public static final String TRAVEL_TIME = "claimSummaryFee.travelTime";
    public static final String WAITING_TIME = "claimSummaryFee.waitingTime";
    public static final String NET_PROFIT_COSTS_AMOUNT = "claimSummaryFee.netProfitCostsAmount";
    public static final String NET_DISBURSEMENT_AMOUNT = "claimSummaryFee.netDisbursementAmount";
    public static final String NET_COUNSEL_COSTS_AMOUNT = "claimSummaryFee.netCounselCostsAmount";
    public static final String DISBURSEMENTS_VAT_AMOUNT = "claimSummaryFee.disbursementsVatAmount";
    public static final String TRAVEL_WAITING_COSTS_AMOUNT =
        "claimSummaryFee.travelWaitingCostsAmount";
    public static final String NET_WAITING_COSTS_AMOUNT = "claimSummaryFee.netWaitingCostsAmount";
    public static final String IS_VAT_APPLICABLE = "claimSummaryFee.isVatApplicable";
    public static final String IS_TOLERANCE_APPLICABLE = "claimSummaryFee.isToleranceApplicable";
    public static final String PRIOR_AUTHORITY_REFERENCE =
        "claimSummaryFee.priorAuthorityReference";
    public static final String IS_LONDON_RATE = "claimSummaryFee.isLondonRate";
    public static final String ADJOURNED_HEARING_FEE_AMOUNT =
        "claimSummaryFee.adjournedHearingFeeAmount";
    public static final String IS_ADDITIONAL_TRAVEL_PAYMENT =
        "claimSummaryFee.isAdditionalTravelPayment";
    public static final String COSTS_DAMAGES_RECOVERED_AMOUNT =
        "claimSummaryFee.costsDamagesRecoveredAmount";
    public static final String MEETINGS_ATTENDED_CODE = "claimSummaryFee.meetingsAttendedCode";
    public static final String DETENTION_TRAVEL_WAITING_COSTS_AMOUNT =
        "claimSummaryFee.detentionTravelWaitingCostsAmount";
    public static final String JR_FORM_FILLING_AMOUNT = "claimSummaryFee.jrFormFillingAmount";
    public static final String IS_ELIGIBLE_CLIENT = "claimSummaryFee.isEligibleClient";
    public static final String COURT_LOCATION_CODE = "claimSummaryFee.courtLocationCode";
    public static final String ADVICE_TYPE_CODE = "claimSummaryFee.adviceTypeCode";
    public static final String MEDICAL_REPORTS_COUNT = "claimSummaryFee.medicalReportsCount";
    public static final String IS_IRC_SURGERY = "claimSummaryFee.isIrcSurgery";
    public static final String SURGERY_DATE = "claimSummaryFee.surgeryDate";
    public static final String SURGERY_CLIENTS_COUNT = "claimSummaryFee.surgeryClientsCount";
    public static final String SURGERY_MATTERS_COUNT = "claimSummaryFee.surgeryMattersCount";
    public static final String CMRH_ORAL_COUNT = "claimSummaryFee.cmrhOralCount";
    public static final String CMRH_TELEPHONE_COUNT = "claimSummaryFee.cmrhTelephoneCount";
    public static final String AIT_HEARING_CENTRE_CODE = "claimSummaryFee.aitHearingCentreCode";
    public static final String IS_SUBSTANTIVE_HEARING = "claimSummaryFee.isSubstantiveHearing";
    public static final String HO_INTERVIEW = "claimSummaryFee.hoInterview";
    public static final String LOCAL_AUTHORITY_NUMBER = "claimSummaryFee.localAuthorityNumber";
  }

  /** FSP-sourced {@code fee.*} calculated-fee field identifiers. */
  public static final class FeeFields {

    private FeeFields() {}

    public static final String FEE_CODE = "fee.feeCode";
    public static final String FEE_TYPE = "fee.feeType";
    public static final String FEE_CODE_DESCRIPTION = "fee.feeCodeDescription";
    public static final String CATEGORY_OF_LAW = "fee.categoryOfLaw";
    public static final String TOTAL_AMOUNT = "fee.totalAmount";
    public static final String VAT_INDICATOR = "fee.vatIndicator";
    public static final String VAT_RATE_APPLIED = "fee.vatRateApplied";
    public static final String CALCULATED_VAT_AMOUNT = "fee.calculatedVatAmount";
    public static final String DISBURSEMENT_AMOUNT = "fee.disbursementAmount";
    public static final String REQUESTED_NET_DISBURSEMENT_AMOUNT =
        "fee.requestedNetDisbursementAmount";
    public static final String DISBURSEMENT_VAT_AMOUNT = "fee.disbursementVatAmount";
    public static final String HOURLY_TOTAL_AMOUNT = "fee.hourlyTotalAmount";
    public static final String FIXED_FEE_AMOUNT = "fee.fixedFeeAmount";
    public static final String NET_PROFIT_COSTS_AMOUNT = "fee.netProfitCostsAmount";
    public static final String REQUESTED_NET_PROFIT_COSTS_AMOUNT =
        "fee.requestedNetProfitCostsAmount";
    public static final String NET_COST_OF_COUNSEL_AMOUNT = "fee.netCostOfCounselAmount";
    public static final String NET_TRAVEL_COSTS_AMOUNT = "fee.netTravelCostsAmount";
    public static final String NET_WAITING_COSTS_AMOUNT = "fee.netWaitingCostsAmount";
    public static final String DETENTION_TRAVEL_AND_WAITING_COSTS_AMOUNT =
        "fee.detentionTravelAndWaitingCostsAmount";
    public static final String JR_FORM_FILLING_AMOUNT = "fee.jrFormFillingAmount";
    public static final String TRAVEL_AND_WAITING_COSTS_AMOUNT = "fee.travelAndWaitingCostsAmount";
    public static final String BOLT_ON_TOTAL_FEE_AMOUNT = "fee.boltOnTotalFeeAmount";
    public static final String BOLT_ON_ADJOURNED_HEARING_COUNT = "fee.boltOnAdjournedHearingCount";
    public static final String BOLT_ON_ADJOURNED_HEARING_FEE = "fee.boltOnAdjournedHearingFee";
    public static final String BOLT_ON_CMRH_TELEPHONE_COUNT = "fee.boltOnCmrhTelephoneCount";
    public static final String BOLT_ON_CMRH_TELEPHONE_FEE = "fee.boltOnCmrhTelephoneFee";
    public static final String BOLT_ON_CMRH_ORAL_COUNT = "fee.boltOnCmrhOralCount";
    public static final String BOLT_ON_CMRH_ORAL_FEE = "fee.boltOnCmrhOralFee";
    public static final String BOLT_ON_HOME_OFFICE_INTERVIEW_COUNT =
        "fee.boltOnHomeOfficeInterviewCount";
    public static final String BOLT_ON_HOME_OFFICE_INTERVIEW_FEE =
        "fee.boltOnHomeOfficeInterviewFee";
    public static final String BOLT_ON_SUBSTANTIVE_HEARING_FEE = "fee.boltOnSubstantiveHearingFee";
    public static final String ESCAPE_CASE_FLAG = "fee.escapeCaseFlag";
    public static final String SCHEME_ID = "fee.schemeId";
  }
}
