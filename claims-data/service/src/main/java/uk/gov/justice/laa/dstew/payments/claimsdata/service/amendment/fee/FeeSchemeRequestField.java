package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;

/**
 * Central, auditable registry of the fee-scheme-platform (FSP) request fields and the claim field
 * each is mapped from, together with the {@link AreaOfLaw areas of law} for which the mapping
 * applies.
 *
 * <p>This is the single source of truth for "does this claim field feed the FSP fee-scheme
 * request?". It is derived directly from the agreed mapping table (and its nested bolt-on table)
 * and is deliberately exhaustive so the full mapping is reviewable in one place.
 *
 * <p>Each constant records:
 *
 * <ul>
 *   <li>{@link #getRequestField() requestField} - the FSP request field name (target);
 *   <li>{@link #getClaimField() claimField} - the claim field it is mapped from (source, and the
 *       value matched by {@link #impactsPricing(String, AreaOfLaw)});
 *   <li>{@link #getAreasOfLaw() areasOfLaw} - the areas of law for which the mapping applies. Most
 *       fields apply to every area; only the travel/waiting-cost fields are area-specific.
 * </ul>
 *
 * <p>A claim field may appear more than once when the same source feeds different FSP fields under
 * different areas of law (e.g. {@code travelWaitingCostsAmount} &rarr; {@code netTravelCosts} for
 * {@code CRIME_LOWER} and {@code travelAndWaitingCosts} for {@code LEGAL_HELP}).
 */
@Getter
public enum FeeSchemeRequestField {
  // ----- Main mapping table -----
  // Format: requestField, claimField (fully-qualified namespace.field), areasOfLaw
  FEE_CODE("feeCode", "claim.feeCode", allAreas()),
  CLAIM_ID("claimId", "claim.id", allAreas()),
  START_DATE("startDate", "claim.caseStartDate", allAreas()),
  POLICE_STATION_ID("policeStationId", "claim.policeStationCourtPrisonId", allAreas()),
  POLICE_STATION_SCHEME_ID("policeStationSchemeId", "claim.schemeId", allAreas()),
  UNIQUE_FILE_NUMBER("uniqueFileNumber", "claim.uniqueFileNumber", allAreas()),
  NET_PROFIT_COSTS("netProfitCosts", "claimSummaryFee.netProfitCostsAmount", allAreas()),
  NET_COST_OF_COUNSEL("netCostOfCounsel", "claimSummaryFee.netCounselCostsAmount", allAreas()),
  NET_DISBURSEMENT_AMOUNT(
      "netDisbursementAmount", "claimSummaryFee.netDisbursementAmount", allAreas()),
  DISBURSEMENT_VAT_AMOUNT(
      "disbursementVatAmount", "claimSummaryFee.disbursementsVatAmount", allAreas()),
  VAT_INDICATOR("vatIndicator", "claimSummaryFee.isVatApplicable", allAreas()),

  // Conditional (area-of-law specific) - rows 13/14/15.
  NET_TRAVEL_COSTS(
      "netTravelCosts",
      "claimSummaryFee.travelWaitingCostsAmount",
      EnumSet.of(AreaOfLaw.CRIME_LOWER)),
  NET_WAITING_COSTS(
      "netWaitingCosts",
      "claimSummaryFee.netWaitingCostsAmount",
      EnumSet.of(AreaOfLaw.CRIME_LOWER)),
  TRAVEL_AND_WAITING_COSTS(
      "travelAndWaitingCosts",
      "claimSummaryFee.travelWaitingCostsAmount",
      EnumSet.of(AreaOfLaw.LEGAL_HELP)),

  DETENTION_TRAVEL_AND_WAITING_COSTS(
      "detentionTravelAndWaitingCosts",
      "claimSummaryFee.detentionTravelWaitingCostsAmount",
      allAreas()),
  CASE_CONCLUDED_DATE("caseConcludedDate", "claim.caseConcludedDate", allAreas()),
  NUMBER_OF_MEDIATION_SESSIONS(
      "numberOfMediationSessions", "claim.mediationSessionsCount", allAreas()),
  JR_FORM_FILLING("jrFormFilling", "claimSummaryFee.jrFormFillingAmount", allAreas()),
  IMMIGRATION_PRIOR_AUTHORITY_NUMBER(
      "immigrationPriorAuthorityNumber", "claimSummaryFee.priorAuthorityReference", allAreas()),

  REPRESENTATION_ORDER_DATE("representationOrderDate", "claim.representationOrderDate", allAreas()),

  LONDON_RATE("londonRate", "claimSummaryFee.isLondonRate", allAreas()),

  // ----- Bolt-ons (nested boltOns object, row 12) -----
  BOLT_ON_ADJOURNED_HEARING(
      "boltOnAdjournedHearing", "claimSummaryFee.adjournedHearingFeeAmount", allAreas()),
  BOLT_ON_CMRH_ORAL("boltOnCmrhOral", "claimSummaryFee.cmrhOralCount", allAreas()),
  BOLT_ON_CMRH_TELEPHONE("boltOnCmrhTelephone", "claimSummaryFee.cmrhTelephoneCount", allAreas()),
  BOLT_ON_HOME_OFFICE_INTERVIEW(
      "boltOnHomeOfficeInterview", "claimSummaryFee.hoInterview", allAreas()),
  BOLT_ON_SUBSTANTIVE_HEARING(
      "boltOnSubstantiveHearing", "claimSummaryFee.isSubstantiveHearing", allAreas());

  private final String requestField;
  private final String claimField;
  private final Set<AreaOfLaw> areasOfLaw;

  FeeSchemeRequestField(String requestField, String claimField, Set<AreaOfLaw> areasOfLaw) {
    this.requestField = requestField;
    this.claimField = claimField;
    this.areasOfLaw = Set.copyOf(areasOfLaw);
  }

  /**
   * Whether the given claim field maps to the FSP fee-scheme request for the given area of law.
   *
   * @param field the claim field name (the "mapped from claim" value); may be {@code null}
   * @param areaOfLaw the area of law to evaluate the mapping for; must not be {@code null}
   * @return {@code true} if the field maps to the fee-scheme request for that area of law
   * @throws NullPointerException if {@code areaOfLaw} is {@code null}
   */
  public static boolean impactsPricing(String field, AreaOfLaw areaOfLaw) {
    Objects.requireNonNull(areaOfLaw, "areaOfLaw must not be null");
    if (field == null) {
      return false;
    }

    // Exact match against the fully-qualified claimField (namespace.field). This function expects
    // callers to supply a fully-qualified identifier (e.g. "claimSummaryFee.netProfitCostsAmount").
    return Arrays.stream(values())
        .filter(entry -> entry.claimField.equals(field))
        .anyMatch(entry -> entry.areasOfLaw.contains(areaOfLaw));
  }

  private static Set<AreaOfLaw> allAreas() {
    return EnumSet.allOf(AreaOfLaw.class);
  }
}
