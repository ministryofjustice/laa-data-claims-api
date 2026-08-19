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
 * law) behave as agreed. The field vocabulary is the amendment diff identifier (e.g. {@code
 * "claim.feeCode"}), aligned with {@code AmendmentChangeDetector}.
 *
 * <p>The area-specific tests iterate over every {@link AreaOfLaw} value and assert the result
 * against the expected set, so a newly added area of law is automatically asserted (and would catch
 * a regression) rather than being silently ignored.
 */
@DisplayName("FeeSchemeRequestField Tests")
class FeeSchemeRequestFieldTest {

  /** Diff identifiers that map to the fee-scheme request for every area of law. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "claim.feeCode",
        "claim.caseStartDate",
        "claim.policeStationCourtPrisonId",
        "claim.schemeId",
        "claim.uniqueFileNumber",
        "claimSummaryFee.netProfitCostsAmount",
        "claimSummaryFee.netCounselCostsAmount",
        "claimSummaryFee.netDisbursementAmount",
        "claimSummaryFee.disbursementsVatAmount",
        "claimSummaryFee.isVatApplicable",
        "claimSummaryFee.detentionTravelWaitingCostsAmount",
        "claim.caseConcludedDate",
        "claim.mediationSessionsCount",
        "claimSummaryFee.jrFormFillingAmount",
        "claimSummaryFee.priorAuthorityReference",
        "claim.representationOrderDate",
        "claimSummaryFee.isLondonRate",
        "claimSummaryFee.adjournedHearingFeeAmount",
        "claimSummaryFee.cmrhOralCount",
        "claimSummaryFee.cmrhTelephoneCount",
        "claimSummaryFee.hoInterview",
        "claimSummaryFee.isSubstantiveHearing"
      })
  @DisplayName("unconditional fields map for every area of law")
  void unconditionalFieldsMapForEveryArea(String diffFieldIdentifier) {
    for (AreaOfLaw areaOfLaw : AreaOfLaw.values()) {
      assertThat(impactsPricing(diffFieldIdentifier, areaOfLaw))
          .as("%s for %s", diffFieldIdentifier, areaOfLaw)
          .isTrue();
    }
  }

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  @DisplayName("travelWaitingCostsAmount maps only for CRIME_LOWER and LEGAL_HELP")
  void travelWaitingCostsMapsForCrimeLowerAndLegalHelpOnly(AreaOfLaw areaOfLaw) {
    Set<AreaOfLaw> expected = EnumSet.of(AreaOfLaw.CRIME_LOWER, AreaOfLaw.LEGAL_HELP);

    assertThat(impactsPricing("claimSummaryFee.travelWaitingCostsAmount", areaOfLaw))
        .as("claimSummaryFee.travelWaitingCostsAmount for %s", areaOfLaw)
        .isEqualTo(expected.contains(areaOfLaw));
  }

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  @DisplayName("netWaitingCostsAmount maps only for CRIME_LOWER")
  void netWaitingCostsMapsForCrimeLowerOnly(AreaOfLaw areaOfLaw) {
    Set<AreaOfLaw> expected = EnumSet.of(AreaOfLaw.CRIME_LOWER);

    assertThat(impactsPricing("claimSummaryFee.netWaitingCostsAmount", areaOfLaw))
        .as("claimSummaryFee.netWaitingCostsAmount for %s", areaOfLaw)
        .isEqualTo(expected.contains(areaOfLaw));
  }

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  @DisplayName("an unknown field never maps")
  void unknownFieldNeverMaps(AreaOfLaw areaOfLaw) {
    assertThat(impactsPricing("claim.notAField", areaOfLaw)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  @DisplayName("a non-amendable source (never emitted as a diff identifier) never maps")
  void nonAmendableSourceNeverMaps(AreaOfLaw areaOfLaw) {
    // The claim id is read-only and never amended, so it has no diff identifier and never maps.
    assertThat(impactsPricing("id", areaOfLaw)).isFalse();
    assertThat(impactsPricing("claim.id", areaOfLaw)).isFalse();
  }

  @Test
  @DisplayName("every registry entry is reachable for each of its declared areas of law")
  void everyRegistryEntryMapsForItsDeclaredAreas() {
    for (FeeSchemeRequestField entry : FeeSchemeRequestField.values()) {
      for (AreaOfLaw areaOfLaw : entry.getAreasOfLaw()) {
        assertThat(impactsPricing(entry.getDiffFieldIdentifier(), areaOfLaw))
            .as("%s (diff '%s') for %s", entry.name(), entry.getDiffFieldIdentifier(), areaOfLaw)
            .isTrue();
      }
    }
  }

  @Test
  @DisplayName("a bare (unqualified) claim field name never maps")
  void bareFieldNamesDoNotMap() {
    // The lookup speaks the qualified diff vocabulary; the leaf name alone must not match.
    Set<String> bareLeafNames =
        Arrays.stream(FeeSchemeRequestField.values())
            .map(FeeSchemeRequestField::getDiffFieldIdentifier)
            .map(identifier -> identifier.substring(identifier.indexOf('.') + 1))
            .collect(Collectors.toSet());

    for (String bareName : bareLeafNames) {
      for (AreaOfLaw areaOfLaw : AreaOfLaw.values()) {
        assertThat(impactsPricing(bareName, areaOfLaw))
            .as("bare name '%s' for %s", bareName, areaOfLaw)
            .isFalse();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", " ", "Claim.feeCode", "CLAIM.FEECODE", "claim.feecode", " claim.feeCode"})
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
        .isThrownBy(() -> impactsPricing("claim.feeCode", null))
        .withMessageContaining("areaOfLaw");
  }
}
