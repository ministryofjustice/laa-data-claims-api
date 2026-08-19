package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimSummaryFeeFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClientFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;

/**
 * Tests for {@link AmendableClaimFields}, the per-area amendable-field whitelist derived from the
 * signed-off "Amend MVP - fields for amendment" artefact.
 */
@DisplayName("AmendableClaimFields Tests")
class AmendableClaimFieldsTest {

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  @DisplayName("every field listed for an area is amendable for exactly that area's set")
  void listedFieldsResolveConsistentlyWithTheAreaSet(AreaOfLaw area) {
    for (String field : AmendableClaimFields.amendableFieldsFor(area)) {
      assertThat(AmendableClaimFields.isAmendable(field, area))
          .as("field %s for area %s", field, area)
          .isTrue();
      for (AreaOfLaw otherArea : AreaOfLaw.values()) {
        boolean expected = AmendableClaimFields.amendableFieldsFor(otherArea).contains(field);
        assertThat(AmendableClaimFields.isAmendable(field, otherArea))
            .as("field %s for area %s", field, otherArea)
            .isEqualTo(expected);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("expectedAmendability")
  @DisplayName("representative artefact rows resolve to the expected amendability")
  void representativeRowsResolveAsExpected(
      String fieldIdentifier, AreaOfLaw area, boolean amendable) {
    assertThat(AmendableClaimFields.isAmendable(fieldIdentifier, area))
        .as("field %s for area %s", fieldIdentifier, area)
        .isEqualTo(amendable);
  }

  private static Stream<Arguments> expectedAmendability() {
    return Stream.of(
        // Fee Code is amendable for every area of law.
        Arguments.of(ClaimFields.FEE_CODE, AreaOfLaw.CRIME_LOWER, true),
        Arguments.of(ClaimFields.FEE_CODE, AreaOfLaw.MEDIATION, true),
        Arguments.of(ClaimFields.FEE_CODE, AreaOfLaw.LEGAL_HELP, true),
        // Scheme ID is Crime Lower only.
        Arguments.of(ClaimFields.SCHEME_ID, AreaOfLaw.CRIME_LOWER, true),
        Arguments.of(ClaimFields.SCHEME_ID, AreaOfLaw.MEDIATION, false),
        Arguments.of(ClaimFields.SCHEME_ID, AreaOfLaw.LEGAL_HELP, false),
        // London/Non-London rate is Legal Help only.
        Arguments.of(ClaimSummaryFeeFields.IS_LONDON_RATE, AreaOfLaw.LEGAL_HELP, true),
        Arguments.of(ClaimSummaryFeeFields.IS_LONDON_RATE, AreaOfLaw.CRIME_LOWER, false),
        Arguments.of(ClaimSummaryFeeFields.IS_LONDON_RATE, AreaOfLaw.MEDIATION, false),
        // Number of Mediation Sessions is Mediation only.
        Arguments.of(ClaimFields.MEDIATION_SESSIONS_COUNT, AreaOfLaw.MEDIATION, true),
        Arguments.of(ClaimFields.MEDIATION_SESSIONS_COUNT, AreaOfLaw.CRIME_LOWER, false),
        Arguments.of(ClaimFields.MEDIATION_SESSIONS_COUNT, AreaOfLaw.LEGAL_HELP, false),
        // Client surname is amendable for every area of law.
        Arguments.of(ClientFields.CLIENT_SURNAME, AreaOfLaw.CRIME_LOWER, true),
        Arguments.of(ClientFields.CLIENT_SURNAME, AreaOfLaw.MEDIATION, true),
        Arguments.of(ClientFields.CLIENT_SURNAME, AreaOfLaw.LEGAL_HELP, true),
        // Client 2 fields are Mediation only.
        Arguments.of(ClientFields.CLIENT2_SURNAME, AreaOfLaw.MEDIATION, true),
        Arguments.of(ClientFields.CLIENT2_SURNAME, AreaOfLaw.LEGAL_HELP, false),
        // Net profit costs are amendable for Crime Lower and Legal Help, not Mediation.
        Arguments.of(ClaimSummaryFeeFields.NET_PROFIT_COSTS_AMOUNT, AreaOfLaw.CRIME_LOWER, true),
        Arguments.of(ClaimSummaryFeeFields.NET_PROFIT_COSTS_AMOUNT, AreaOfLaw.LEGAL_HELP, true),
        Arguments.of(ClaimSummaryFeeFields.NET_PROFIT_COSTS_AMOUNT, AreaOfLaw.MEDIATION, false),
        // Crime "Matter Type" maps to crimeMatterTypeCode, amendable for Crime Lower only.
        Arguments.of(ClaimFields.CRIME_MATTER_TYPE_CODE, AreaOfLaw.CRIME_LOWER, true),
        Arguments.of(ClaimFields.CRIME_MATTER_TYPE_CODE, AreaOfLaw.MEDIATION, false),
        Arguments.of(ClaimFields.CRIME_MATTER_TYPE_CODE, AreaOfLaw.LEGAL_HELP, false),
        // Legal Help / Mediation "Matter Type 1"+"Matter Type 2" are components of matterTypeCode.
        Arguments.of(ClaimFields.MATTER_TYPE_CODE, AreaOfLaw.MEDIATION, true),
        Arguments.of(ClaimFields.MATTER_TYPE_CODE, AreaOfLaw.LEGAL_HELP, true),
        Arguments.of(ClaimFields.MATTER_TYPE_CODE, AreaOfLaw.CRIME_LOWER, false));
  }

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  @DisplayName("a null field identifier is never amendable")
  void nullFieldNeverAmendable(AreaOfLaw area) {
    assertThat(AmendableClaimFields.isAmendable(null, area)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("everyAmendableField")
  @DisplayName("a null area of law is never amendable, even for a registered field")
  void nullAreaNeverAmendable(String field) {
    assertThat(AmendableClaimFields.isAmendable(field, null)).isFalse();
  }

  private static Stream<String> everyAmendableField() {
    return Stream.of(AreaOfLaw.values())
        .flatMap(area -> AmendableClaimFields.amendableFieldsFor(area).stream())
        .distinct();
  }

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  @DisplayName("a field absent from the registry is never amendable")
  void unregisteredFieldNeverAmendable(AreaOfLaw area) {
    // line_number is tracked by the change detector but is not a provider-amendable field.
    assertThat(AmendableClaimFields.isAmendable(ClaimFields.LINE_NUMBER, area)).isFalse();
  }
}
