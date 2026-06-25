package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeSchemeRequestField.mapsToFeeSchemeRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;

/**
 * Tests for {@link FeeSchemeRequestField}.
 *
 * <p>Pins the full mapping table: every unconditional claim field maps for all areas of law, the
 * travel/waiting fields are area-specific, {@code representationOrderDate} is intentionally
 * unmapped, and the edge cases (unknown/null field, null area of law) behave as agreed.
 */
@DisplayName("FeeSchemeRequestField Tests")
class FeeSchemeRequestFieldTest {

  /** Claim fields that map to the fee-scheme request for every area of law. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "feeCode",
        "id",
        "caseStartDate",
        "policeStationCourtPrisonId",
        "schemeId",
        "uniqueFileNumber",
        "netProfitCostsAmount",
        "netCounselCostsAmount",
        "netDisbursementAmount",
        "disbursementsVatAmount",
        "isVatApplicable",
        "detentionTravelWaitingCostsAmount",
        "caseConcludedDate",
        "mediationSessionsCount",
        "jrFormFillingAmount",
        "priorAuthorityReference",
        "isLondonRate",
        "adjournedHearingFeeAmount",
        "cmrhOralCount",
        "cmrhTelephoneCount",
        "hoInterview",
        "isSubstantiveHearing"
      })
  @DisplayName("unconditional fields map for every area of law")
  void unconditionalFieldsMapForEveryArea(String claimField) {
    for (AreaOfLaw areaOfLaw : AreaOfLaw.values()) {
      assertThat(mapsToFeeSchemeRequest(claimField, areaOfLaw))
          .as("%s for %s", claimField, areaOfLaw)
          .isTrue();
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = AreaOfLaw.class,
      names = {"CRIME_LOWER", "LEGAL_HELP"})
  @DisplayName("travelWaitingCostsAmount maps for CRIME_LOWER and LEGAL_HELP")
  void travelWaitingCostsMapsForCrimeLowerAndLegalHelp(AreaOfLaw areaOfLaw) {
    assertThat(mapsToFeeSchemeRequest("travelWaitingCostsAmount", areaOfLaw)).isTrue();
  }

  @Test
  @DisplayName("travelWaitingCostsAmount does not map for MEDIATION")
  void travelWaitingCostsDoesNotMapForMediation() {
    assertThat(mapsToFeeSchemeRequest("travelWaitingCostsAmount", AreaOfLaw.MEDIATION)).isFalse();
  }

  @Test
  @DisplayName("netWaitingCostsAmount maps for CRIME_LOWER only")
  void netWaitingCostsMapsForCrimeLowerOnly() {
    assertThat(mapsToFeeSchemeRequest("netWaitingCostsAmount", AreaOfLaw.CRIME_LOWER)).isTrue();
    assertThat(mapsToFeeSchemeRequest("netWaitingCostsAmount", AreaOfLaw.LEGAL_HELP)).isFalse();
    assertThat(mapsToFeeSchemeRequest("netWaitingCostsAmount", AreaOfLaw.MEDIATION)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  @DisplayName("representationOrderDate is intentionally unmapped (row 21 MISSING)")
  void representationOrderDateNeverMaps(AreaOfLaw areaOfLaw) {
    assertThat(mapsToFeeSchemeRequest("representationOrderDate", areaOfLaw)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  @DisplayName("an unknown field never maps")
  void unknownFieldNeverMaps(AreaOfLaw areaOfLaw) {
    assertThat(mapsToFeeSchemeRequest("notAField", areaOfLaw)).isFalse();
  }

  @Test
  @DisplayName("a null field returns false")
  void nullFieldReturnsFalse() {
    assertThat(mapsToFeeSchemeRequest(null, AreaOfLaw.CRIME_LOWER)).isFalse();
  }

  @Test
  @DisplayName("a null area of law throws")
  void nullAreaOfLawThrows() {
    assertThatNullPointerException()
        .isThrownBy(() -> mapsToFeeSchemeRequest("feeCode", null))
        .withMessageContaining("areaOfLaw");
  }
}
