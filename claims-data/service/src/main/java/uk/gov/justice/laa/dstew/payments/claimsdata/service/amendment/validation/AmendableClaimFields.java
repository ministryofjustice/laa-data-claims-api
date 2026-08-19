package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.Map;
import java.util.Set;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimCaseFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimSummaryFeeFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClientFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;

/**
 * Central, auditable registry of the claim fields a provider is permitted to amend, organised by
 * {@link AreaOfLaw area of law}.
 *
 * <p>This is the single source of truth for "is a change to this claim field amendable for the
 * claim's area of law?". It mirrors the final signed-off AaBC artefact <em>"Amend MVP - fields for
 * amendment"</em>, which is authored per area of law. Each area's set is a flat, self-contained
 * list of the diff field identifiers amendable for that area, so it can be reviewed line-for-line
 * against the artefact. A field/area-of-law pair not represented here is <b>not</b> amendable.
 *
 * <p>Fields amendable for more than one area appear in each of those areas' sets. The identifier
 * strings live once in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers}, so a
 * shared field is a repeated constant reference, not a duplicated string.
 *
 * <p>Mapping notes for artefact labels whose recognised identifier is not a literal match:
 *
 * <ul>
 *   <li>Crime Lower "Matter Type" maps to {@code claim.crimeMatterTypeCode}.
 *   <li>Legal Help / Mediation "Matter Type 1" and "Matter Type 2" are the first and second
 *       components of the single {@code claim.matterTypeCode} value (stored as {@code MT1:MT2}), so
 *       both are covered by that one identifier.
 *   <li>Crime Lower "Client Initial" maps to {@code client.clientForename} (the crime-lower client
 *       name column).
 *   <li>Mediation "Claim ID" maps to {@code claimCase.caseId} - the provider-facing case
 *       identifier, not the internal {@code claim.id}.
 *   <li>Legal Help "NIAT Disbursement Prior Authority Number" maps to {@code
 *       claimSummaryFee.priorAuthorityReference} only.
 *   <li>Legal Help "Clients resulting in a Legal Help matter opened" maps to {@code
 *       claimSummaryFee.surgeryMattersCount}; "Clients seen at surgery" maps to {@code
 *       claimSummaryFee.surgeryClientsCount}.
 * </ul>
 */
public final class AmendableClaimFields {

  private AmendableClaimFields() {
    // Registry holder; no instances.
  }

  /** Fields amendable for a Crime Lower claim. */
  private static final Set<String> CRIME_LOWER =
      Set.of(
          // claim.*
          ClaimFields.FEE_CODE,
          ClaimFields.CRIME_MATTER_TYPE_CODE,
          ClaimFields.CASE_CONCLUDED_DATE,
          ClaimFields.UNIQUE_FILE_NUMBER,
          ClaimFields.REPRESENTATION_ORDER_DATE,
          ClaimFields.SUSPECTS_DEFENDANTS_COUNT,
          ClaimFields.POLICE_STATION_COURT_ATTENDANCES_COUNT,
          ClaimFields.POLICE_STATION_COURT_PRISON_ID,
          ClaimFields.DSCC_NUMBER,
          ClaimFields.MAAT_ID,
          ClaimFields.PRISON_LAW_PRIOR_APPROVAL_NUMBER,
          ClaimFields.DUTY_SOLICITOR,
          ClaimFields.YOUTH_COURT,
          ClaimFields.SCHEME_ID,
          // client.*
          ClientFields.CLIENT_FORENAME,
          ClientFields.CLIENT_SURNAME,
          ClientFields.GENDER_CODE,
          ClientFields.ETHNICITY_CODE,
          ClientFields.DISABILITY_CODE,
          // claimCase.*
          ClaimCaseFields.OUTCOME_CODE,
          ClaimCaseFields.STAGE_REACHED_CODE,
          ClaimCaseFields.STANDARD_FEE_CATEGORY_CODE,
          // claimSummaryFee.*
          ClaimSummaryFeeFields.NET_DISBURSEMENT_AMOUNT,
          ClaimSummaryFeeFields.IS_VAT_APPLICABLE,
          ClaimSummaryFeeFields.DISBURSEMENTS_VAT_AMOUNT,
          ClaimSummaryFeeFields.NET_PROFIT_COSTS_AMOUNT,
          ClaimSummaryFeeFields.TRAVEL_WAITING_COSTS_AMOUNT,
          ClaimSummaryFeeFields.NET_WAITING_COSTS_AMOUNT);

  /** Fields amendable for a Mediation claim. */
  private static final Set<String> MEDIATION =
      Set.of(
          // claim.*
          ClaimFields.FEE_CODE,
          ClaimFields.MATTER_TYPE_CODE,
          ClaimFields.CASE_CONCLUDED_DATE,
          ClaimFields.CASE_REFERENCE_NUMBER,
          ClaimFields.CASE_START_DATE,
          ClaimFields.SCHEDULE_REFERENCE,
          ClaimFields.MEDIATION_SESSIONS_COUNT,
          ClaimFields.MEDIATION_TIME_MINUTES,
          ClaimFields.OUTREACH_LOCATION,
          ClaimFields.REFERRAL_SOURCE,
          // client.*
          ClientFields.CLIENT_FORENAME,
          ClientFields.CLIENT_SURNAME,
          ClientFields.GENDER_CODE,
          ClientFields.ETHNICITY_CODE,
          ClientFields.DISABILITY_CODE,
          ClientFields.CLIENT_DATE_OF_BIRTH,
          ClientFields.UNIQUE_CLIENT_NUMBER,
          ClientFields.CLIENT_POSTCODE,
          ClientFields.IS_LEGALLY_AIDED,
          ClientFields.CLIENT2_FORENAME,
          ClientFields.CLIENT2_SURNAME,
          ClientFields.CLIENT2_DATE_OF_BIRTH,
          ClientFields.CLIENT2_UCN,
          ClientFields.CLIENT2_POSTCODE,
          ClientFields.CLIENT2_GENDER_CODE,
          ClientFields.CLIENT2_ETHNICITY_CODE,
          ClientFields.CLIENT2_DISABILITY_CODE,
          ClientFields.CLIENT2_IS_LEGALLY_AIDED,
          // claimCase.*
          ClaimCaseFields.OUTCOME_CODE,
          ClaimCaseFields.CASE_ID,
          ClaimCaseFields.UNIQUE_CASE_ID,
          ClaimCaseFields.IS_POSTAL_APPLICATION_ACCEPTED,
          ClaimCaseFields.IS_CLIENT2_POSTAL_APPLICATION_ACCEPTED,
          // claimSummaryFee.*
          ClaimSummaryFeeFields.NET_DISBURSEMENT_AMOUNT,
          ClaimSummaryFeeFields.IS_VAT_APPLICABLE,
          ClaimSummaryFeeFields.DISBURSEMENTS_VAT_AMOUNT);

  /** Fields amendable for a Legal Help claim. */
  private static final Set<String> LEGAL_HELP =
      Set.of(
          // claim.*
          ClaimFields.FEE_CODE,
          ClaimFields.MATTER_TYPE_CODE,
          ClaimFields.CASE_CONCLUDED_DATE,
          ClaimFields.UNIQUE_FILE_NUMBER,
          ClaimFields.CASE_REFERENCE_NUMBER,
          ClaimFields.CASE_START_DATE,
          ClaimFields.SCHEDULE_REFERENCE,
          ClaimFields.ACCESS_POINT_CODE,
          ClaimFields.DELIVERY_LOCATION,
          ClaimFields.PROCUREMENT_AREA_CODE,
          // client.*
          ClientFields.CLIENT_FORENAME,
          ClientFields.CLIENT_SURNAME,
          ClientFields.GENDER_CODE,
          ClientFields.ETHNICITY_CODE,
          ClientFields.DISABILITY_CODE,
          ClientFields.CLIENT_DATE_OF_BIRTH,
          ClientFields.UNIQUE_CLIENT_NUMBER,
          ClientFields.CLIENT_POSTCODE,
          ClientFields.CLIENT_TYPE_CODE,
          ClientFields.HOME_OFFICE_CLIENT_NUMBER,
          ClientFields.CLA_REFERENCE_NUMBER,
          ClientFields.CLA_EXEMPTION_CODE,
          // claimCase.*
          ClaimCaseFields.OUTCOME_CODE,
          ClaimCaseFields.STAGE_REACHED_CODE,
          ClaimCaseFields.CASE_ID,
          ClaimCaseFields.UNIQUE_CASE_ID,
          ClaimCaseFields.IS_POSTAL_APPLICATION_ACCEPTED,
          ClaimCaseFields.CASE_STAGE_CODE,
          ClaimCaseFields.DESIGNATED_ACCREDITED_REPRESENTATIVE_CODE,
          ClaimCaseFields.EXCEPTIONAL_CASE_FUNDING_REFERENCE,
          ClaimCaseFields.EXEMPTION_CRITERIA_SATISFIED,
          ClaimCaseFields.FOLLOW_ON_WORK,
          ClaimCaseFields.IS_LEGACY_CASE,
          ClaimCaseFields.MENTAL_HEALTH_TRIBUNAL_REFERENCE,
          ClaimCaseFields.IS_NRM_ADVICE,
          ClaimCaseFields.TRANSFER_DATE,
          // claimSummaryFee.*
          ClaimSummaryFeeFields.NET_DISBURSEMENT_AMOUNT,
          ClaimSummaryFeeFields.IS_VAT_APPLICABLE,
          ClaimSummaryFeeFields.DISBURSEMENTS_VAT_AMOUNT,
          ClaimSummaryFeeFields.NET_PROFIT_COSTS_AMOUNT,
          ClaimSummaryFeeFields.TRAVEL_WAITING_COSTS_AMOUNT,
          ClaimSummaryFeeFields.NET_COUNSEL_COSTS_AMOUNT,
          ClaimSummaryFeeFields.IS_ADDITIONAL_TRAVEL_PAYMENT,
          ClaimSummaryFeeFields.ADJOURNED_HEARING_FEE_AMOUNT,
          ClaimSummaryFeeFields.ADVICE_TIME,
          ClaimSummaryFeeFields.TRAVEL_TIME,
          ClaimSummaryFeeFields.WAITING_TIME,
          ClaimSummaryFeeFields.AIT_HEARING_CENTRE_CODE,
          ClaimSummaryFeeFields.CMRH_ORAL_COUNT,
          ClaimSummaryFeeFields.CMRH_TELEPHONE_COUNT,
          ClaimSummaryFeeFields.COURT_LOCATION_CODE,
          ClaimSummaryFeeFields.DETENTION_TRAVEL_WAITING_COSTS_AMOUNT,
          ClaimSummaryFeeFields.IS_ELIGIBLE_CLIENT,
          ClaimSummaryFeeFields.HO_INTERVIEW,
          ClaimSummaryFeeFields.IS_IRC_SURGERY,
          ClaimSummaryFeeFields.JR_FORM_FILLING_AMOUNT,
          ClaimSummaryFeeFields.LOCAL_AUTHORITY_NUMBER,
          ClaimSummaryFeeFields.IS_LONDON_RATE,
          ClaimSummaryFeeFields.MEDICAL_REPORTS_COUNT,
          ClaimSummaryFeeFields.MEETINGS_ATTENDED_CODE,
          ClaimSummaryFeeFields.PRIOR_AUTHORITY_REFERENCE,
          ClaimSummaryFeeFields.SURGERY_CLIENTS_COUNT,
          ClaimSummaryFeeFields.SURGERY_MATTERS_COUNT,
          ClaimSummaryFeeFields.IS_SUBSTANTIVE_HEARING,
          ClaimSummaryFeeFields.SURGERY_DATE,
          ClaimSummaryFeeFields.IS_TOLERANCE_APPLICABLE,
          ClaimSummaryFeeFields.ADVICE_TYPE_CODE,
          ClaimSummaryFeeFields.COSTS_DAMAGES_RECOVERED_AMOUNT);

  private static final Map<AreaOfLaw, Set<String>> AMENDABLE_BY_AREA =
      Map.of(
          AreaOfLaw.CRIME_LOWER, CRIME_LOWER,
          AreaOfLaw.MEDIATION, MEDIATION,
          AreaOfLaw.LEGAL_HELP, LEGAL_HELP);

  /**
   * Whether the given amendment diff field identifier is amendable for the given area of law.
   *
   * <p>The {@code field} is a stable diff identifier as emitted by the {@code
   * AmendmentChangeDetector} (e.g. {@code "claim.feeCode"}).
   *
   * <p>A {@code null} field, a {@code null} area of law (unknown/absent area), or a field not
   * amendable for that area are all treated as not amendable.
   *
   * @param field the amendment diff field identifier; may be {@code null}
   * @param areaOfLaw the claim's area of law; may be {@code null}
   * @return {@code true} if the field is amendable for that area of law
   */
  public static boolean isAmendable(String field, AreaOfLaw areaOfLaw) {
    if (field == null || areaOfLaw == null) {
      return false;
    }
    return AMENDABLE_BY_AREA.getOrDefault(areaOfLaw, Set.of()).contains(field);
  }

  /**
   * The set of field identifiers amendable for the given area of law. Package-private introspection
   * for same-package tests only; not part of the public API. Returns an empty set for a {@code
   * null} or unmapped area.
   *
   * @param areaOfLaw the area of law
   * @return the amendable field identifiers for that area (never {@code null})
   */
  static Set<String> amendableFieldsFor(AreaOfLaw areaOfLaw) {
    return AMENDABLE_BY_AREA.getOrDefault(areaOfLaw, Set.of());
  }
}
