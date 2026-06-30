package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeSchemeRequestField.impactsPricing;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
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
 * travel/waiting fields are area-specific, and the edge cases (unknown/null field, null area of
 * law) behave as agreed.
 *
 * <p>The area-specific tests iterate over every {@link AreaOfLaw} value and assert the result
 * against the expected set, so a newly added area of law is automatically asserted (and would catch
 * a regression) rather than being silently ignored.
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
        "representationOrderDate",
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
      assertThat(impactsPricing(claimField, areaOfLaw))
          .as("%s for %s", claimField, areaOfLaw)
          .isTrue();
    }
  }

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  @DisplayName("travelWaitingCostsAmount maps only for CRIME_LOWER and LEGAL_HELP")
  void travelWaitingCostsMapsForCrimeLowerAndLegalHelpOnly(AreaOfLaw areaOfLaw) {
    Set<AreaOfLaw> expected = EnumSet.of(AreaOfLaw.CRIME_LOWER, AreaOfLaw.LEGAL_HELP);

    assertThat(impactsPricing("travelWaitingCostsAmount", areaOfLaw))
        .as("travelWaitingCostsAmount for %s", areaOfLaw)
        .isEqualTo(expected.contains(areaOfLaw));
  }

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  @DisplayName("netWaitingCostsAmount maps only for CRIME_LOWER")
  void netWaitingCostsMapsForCrimeLowerOnly(AreaOfLaw areaOfLaw) {
    Set<AreaOfLaw> expected = EnumSet.of(AreaOfLaw.CRIME_LOWER);

    assertThat(impactsPricing("netWaitingCostsAmount", areaOfLaw))
        .as("netWaitingCostsAmount for %s", areaOfLaw)
        .isEqualTo(expected.contains(areaOfLaw));
  }

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  @DisplayName("an unknown field never maps")
  void unknownFieldNeverMaps(AreaOfLaw areaOfLaw) {
    assertThat(impactsPricing("notAField", areaOfLaw)).isFalse();
  }

  @Test
  @DisplayName("every registry entry is reachable for each of its declared areas of law")
  void everyRegistryEntryMapsForItsDeclaredAreas() {
    for (FeeSchemeRequestField entry : FeeSchemeRequestField.values()) {
      for (AreaOfLaw areaOfLaw : entry.getAreasOfLaw()) {
        assertThat(impactsPricing(entry.getClaimField(), areaOfLaw))
            .as("%s (claim field '%s') for %s", entry.name(), entry.getClaimField(), areaOfLaw)
            .isTrue();
      }
    }
  }

  @Test
  @DisplayName("a request field name that is not also a claim field never maps")
  void requestOnlyFieldNamesDoNotMap() {
    Set<String> claimFields =
        Arrays.stream(FeeSchemeRequestField.values())
            .map(FeeSchemeRequestField::getClaimField)
            .collect(Collectors.toSet());

    for (FeeSchemeRequestField entry : FeeSchemeRequestField.values()) {
      String requestField = entry.getRequestField();
      // Skip request names that happen to equal a claim field (e.g. feeCode) - they map by design.
      if (claimFields.contains(requestField)) {
        continue;
      }
      for (AreaOfLaw areaOfLaw : AreaOfLaw.values()) {
        assertThat(impactsPricing(requestField, areaOfLaw))
            .as("request-only field '%s' (%s) for %s", requestField, entry.name(), areaOfLaw)
            .isFalse();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "FeeCode", "FEECODE", "feecode", " feeCode"})
  @DisplayName("blank or wrong-case field names never map (lookup is exact and case-sensitive)")
  void blankOrWrongCaseFieldNamesDoNotMap(String field) {
    for (AreaOfLaw areaOfLaw : AreaOfLaw.values()) {
      assertThat(impactsPricing(field, areaOfLaw)).as("'%s' for %s", field, areaOfLaw).isFalse();
    }
  }

  @Test
  @DisplayName("a null field returns false")
  void nullFieldReturnsFalse() {
    assertThat(impactsPricing(null, AreaOfLaw.CRIME_LOWER)).isFalse();
  }

  @Test
  @DisplayName("a null area of law throws")
  void nullAreaOfLawThrows() {
    assertThatNullPointerException()
        .isThrownBy(() -> impactsPricing("feeCode", null))
        .withMessageContaining("areaOfLaw");
  }
}
