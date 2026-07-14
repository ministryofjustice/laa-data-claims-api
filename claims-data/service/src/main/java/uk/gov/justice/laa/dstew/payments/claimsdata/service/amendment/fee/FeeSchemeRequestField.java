package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimSummaryFeeFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;

/**
 * Central, auditable registry of the claim fields whose amendment could change the fee-scheme
 * platform (FSP) fee-scheme request, together with the {@link AreaOfLaw areas of law} for which the
 * mapping applies.
 *
 * <p>This is the single source of truth for "does a change to this claim field feed the FSP
 * fee-scheme request?". It is derived directly from the agreed mapping table (and its nested
 * bolt-on table) and is deliberately exhaustive so the full mapping is reviewable in one place.
 *
 * <p>Each constant records:
 *
 * <ul>
 *   <li>{@link #getDiffFieldIdentifier() diffFieldIdentifier} - the stable amendment diff
 *       identifier for the claim field (e.g. {@code "claim.feeCode"}), aligned with {@link
 *       uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentChangeDetector}
 *       and the value matched by {@link #impactsPricing(String, AreaOfLaw)};
 *   <li>{@link #getAreasOfLaw() areasOfLaw} - the areas of law for which the mapping applies. Most
 *       fields apply to every area; only the travel/waiting-cost fields are area-specific.
 * </ul>
 *
 * <p>A claim field may appear more than once when it feeds the request under different areas of law
 * with different scoping (e.g. {@code claimSummaryFee.travelWaitingCostsAmount} applies for both
 * {@code CRIME_LOWER} and {@code LEGAL_HELP}).
 */
@Getter
public enum FeeSchemeRequestField {
  // ----- Main mapping table -----
  FEE_CODE(ClaimFields.FEE_CODE, allAreas()),
  START_DATE(ClaimFields.CASE_START_DATE, allAreas()),
  POLICE_STATION_ID(ClaimFields.POLICE_STATION_COURT_PRISON_ID, allAreas()),
  POLICE_STATION_SCHEME_ID(ClaimFields.SCHEME_ID, allAreas()),
  UNIQUE_FILE_NUMBER(ClaimFields.UNIQUE_FILE_NUMBER, allAreas()),
  NET_PROFIT_COSTS(ClaimSummaryFeeFields.NET_PROFIT_COSTS_AMOUNT, allAreas()),
  NET_COST_OF_COUNSEL(ClaimSummaryFeeFields.NET_COUNSEL_COSTS_AMOUNT, allAreas()),
  NET_DISBURSEMENT_AMOUNT(ClaimSummaryFeeFields.NET_DISBURSEMENT_AMOUNT, allAreas()),
  DISBURSEMENT_VAT_AMOUNT(ClaimSummaryFeeFields.DISBURSEMENTS_VAT_AMOUNT, allAreas()),
  VAT_INDICATOR(ClaimSummaryFeeFields.IS_VAT_APPLICABLE, allAreas()),

  // Conditional (area-of-law specific) - rows 13/14/15.
  NET_TRAVEL_COSTS(
      ClaimSummaryFeeFields.TRAVEL_WAITING_COSTS_AMOUNT, EnumSet.of(AreaOfLaw.CRIME_LOWER)),
  NET_WAITING_COSTS(
      ClaimSummaryFeeFields.NET_WAITING_COSTS_AMOUNT, EnumSet.of(AreaOfLaw.CRIME_LOWER)),
  TRAVEL_AND_WAITING_COSTS(
      ClaimSummaryFeeFields.TRAVEL_WAITING_COSTS_AMOUNT, EnumSet.of(AreaOfLaw.LEGAL_HELP)),

  DETENTION_TRAVEL_AND_WAITING_COSTS(
      ClaimSummaryFeeFields.DETENTION_TRAVEL_WAITING_COSTS_AMOUNT, allAreas()),
  CASE_CONCLUDED_DATE(ClaimFields.CASE_CONCLUDED_DATE, allAreas()),
  NUMBER_OF_MEDIATION_SESSIONS(ClaimFields.MEDIATION_SESSIONS_COUNT, allAreas()),
  JR_FORM_FILLING(ClaimSummaryFeeFields.JR_FORM_FILLING_AMOUNT, allAreas()),
  IMMIGRATION_PRIOR_AUTHORITY_NUMBER(ClaimSummaryFeeFields.PRIOR_AUTHORITY_REFERENCE, allAreas()),

  REPRESENTATION_ORDER_DATE(ClaimFields.REPRESENTATION_ORDER_DATE, allAreas()),

  LONDON_RATE(ClaimSummaryFeeFields.IS_LONDON_RATE, allAreas()),

  // ----- Bolt-ons (nested boltOns object, row 12) -----
  BOLT_ON_ADJOURNED_HEARING(ClaimSummaryFeeFields.ADJOURNED_HEARING_FEE_AMOUNT, allAreas()),
  BOLT_ON_CMRH_ORAL(ClaimSummaryFeeFields.CMRH_ORAL_COUNT, allAreas()),
  BOLT_ON_CMRH_TELEPHONE(ClaimSummaryFeeFields.CMRH_TELEPHONE_COUNT, allAreas()),
  BOLT_ON_HOME_OFFICE_INTERVIEW(ClaimSummaryFeeFields.HO_INTERVIEW, allAreas()),
  BOLT_ON_SUBSTANTIVE_HEARING(ClaimSummaryFeeFields.IS_SUBSTANTIVE_HEARING, allAreas());

  private final String diffFieldIdentifier;
  private final Set<AreaOfLaw> areasOfLaw;

  FeeSchemeRequestField(String diffFieldIdentifier, Set<AreaOfLaw> areasOfLaw) {
    this.diffFieldIdentifier = diffFieldIdentifier;
    this.areasOfLaw = Set.copyOf(areasOfLaw);
  }

  /**
   * Whether the given amendment diff field identifier maps to the FSP fee-scheme request for the
   * given area of law.
   *
   * <p>The {@code field} is a stable diff identifier as emitted by the {@code
   * AmendmentChangeDetector} (e.g. {@code "claim.feeCode"}), matching {@link
   * #getDiffFieldIdentifier()}.
   *
   * @param field the amendment diff field identifier; may be {@code null}
   * @param areaOfLaw the area of law to evaluate the mapping for; must not be {@code null}
   * @return {@code true} if the field maps to the fee-scheme request for that area of law
   * @throws NullPointerException if {@code areaOfLaw} is {@code null}
   */
  public static boolean impactsPricing(String field, AreaOfLaw areaOfLaw) {
    Objects.requireNonNull(areaOfLaw, "areaOfLaw must not be null");
    if (field == null) {
      return false;
    }
    return Arrays.stream(values())
        .filter(entry -> field.equals(entry.diffFieldIdentifier))
        .anyMatch(entry -> entry.areasOfLaw.contains(areaOfLaw));
  }

  private static Set<AreaOfLaw> allAreas() {
    return EnumSet.allOf(AreaOfLaw.class);
  }
}
