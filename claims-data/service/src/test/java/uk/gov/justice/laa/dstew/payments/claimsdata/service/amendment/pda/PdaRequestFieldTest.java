package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.pda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;

/**
 * Tests for {@link PdaRequestField#impactsPda(String, ClaimStateSnapshot)}.
 *
 * <p>Each amended field is checked against the claim's fully-merged effective state to decide
 * whether changing it could alter the downstream PDA {@code getProviderFirmSchedules} request.
 * Because the effective date is priority-based, a lower-priority field only matters when no
 * higher-priority field is populated.
 */
@DisplayName("PdaRequestField Tests")
class PdaRequestFieldTest {

  private static final LocalDate ANY_DATE = LocalDate.of(2026, Month.JANUARY, 15);

  @Nested
  @DisplayName("claim.feeCode")
  class FeeCode {

    @Test
    @DisplayName("always affects when PROD and concluded date present")
    void prodWithConcluded() {
      ClaimStateSnapshot state =
          ClaimStateSnapshot.builder().feeCode("PROD").caseConcludedDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("claim.feeCode", state)).isTrue();
    }

    @Test
    @DisplayName("always affects even when PROD but no concluded date")
    void prodWithoutConcluded() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().feeCode("PROD").build();
      assertThat(PdaRequestField.impactsPda("claim.feeCode", state)).isTrue();
    }

    @Test
    @DisplayName("always affects when a concluded date is present even if new fee code is not PROD")
    void notProdButConcludedPresent() {
      // feeCode-derived data is compared against the PDA response for validation, so any feeCode
      // change affects the requirement to use the PDA regardless of the other fields.
      ClaimStateSnapshot state =
          ClaimStateSnapshot.builder().feeCode("OTHER").caseConcludedDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("claim.feeCode", state)).isTrue();
    }

    @Test
    @DisplayName("always affects even with no relevant dates populated")
    void alwaysImpacts() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().feeCode("OTHER").build();
      assertThat(PdaRequestField.impactsPda("claim.feeCode", state)).isTrue();
    }
  }

  @Nested
  @DisplayName("claim.caseConcludedDate")
  class CaseConcludedDate {

    @Test
    @DisplayName("affects when PROD")
    void prod() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().feeCode("PROD").build();
      assertThat(PdaRequestField.impactsPda("claim.caseConcludedDate", state)).isTrue();
    }

    @Test
    @DisplayName("does not affect when not PROD")
    void notProd() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().feeCode("OTHER").build();
      assertThat(PdaRequestField.impactsPda("claim.caseConcludedDate", state)).isFalse();
    }
  }

  @Nested
  @DisplayName("claim.caseStartDate")
  class CaseStartDate {

    @Test
    @DisplayName("affects unless PROD+concluded already wins")
    void affectsWhenProdNotConcluding() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().feeCode("OTHER").build();
      assertThat(PdaRequestField.impactsPda("claim.caseStartDate", state)).isTrue();
    }

    @Test
    @DisplayName("does not affect when PROD+concluded wins")
    void noEffectWhenProdConcluded() {
      ClaimStateSnapshot state =
          ClaimStateSnapshot.builder().feeCode("PROD").caseConcludedDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("claim.caseStartDate", state)).isFalse();
    }
  }

  @Nested
  @DisplayName("claim.representationOrderDate")
  class RepresentationOrderDate {

    @Test
    @DisplayName("affects when no higher-priority date present")
    void affectsWhenNothingHigher() {
      assertThat(
              PdaRequestField.impactsPda(
                  "claim.representationOrderDate", ClaimStateSnapshot.builder().build()))
          .isTrue();
    }

    @Test
    @DisplayName("does not affect when caseStartDate present")
    void noEffectWhenCaseStart() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().caseStartDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("claim.representationOrderDate", state)).isFalse();
    }

    @Test
    @DisplayName("does not affect when PROD+concluded wins")
    void noEffectWhenProdConcluded() {
      ClaimStateSnapshot state =
          ClaimStateSnapshot.builder().feeCode("PROD").caseConcludedDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("claim.representationOrderDate", state)).isFalse();
    }
  }

  @Nested
  @DisplayName("claim.uniqueFileNumber")
  class UniqueFileNumber {

    @Test
    @DisplayName("affects when no higher-priority date present")
    void affectsWhenNothingHigher() {
      assertThat(
              PdaRequestField.impactsPda(
                  "claim.uniqueFileNumber", ClaimStateSnapshot.builder().build()))
          .isTrue();
    }

    @Test
    @DisplayName("changing UFN makes no difference when concluded date wins")
    void noEffectWhenProdConcluded() {
      ClaimStateSnapshot state =
          ClaimStateSnapshot.builder().feeCode("PROD").caseConcludedDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("claim.uniqueFileNumber", state)).isFalse();
    }

    @Test
    @DisplayName("does not affect when caseStartDate present")
    void noEffectWhenCaseStart() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().caseStartDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("claim.uniqueFileNumber", state)).isFalse();
    }

    @Test
    @DisplayName("does not affect when representationOrderDate present")
    void noEffectWhenRepOrder() {
      ClaimStateSnapshot state =
          ClaimStateSnapshot.builder().representationOrderDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("claim.uniqueFileNumber", state)).isFalse();
    }
  }

  @Test
  @DisplayName("an unqualified (bare) field name never affects the request")
  void bareFieldNameNeverAffects() {
    // The check speaks the qualified diff vocabulary; a bare leaf name must not match.
    ClaimStateSnapshot state = ClaimStateSnapshot.builder().feeCode("PROD").build();
    assertThat(PdaRequestField.impactsPda("feeCode", state)).isFalse();
  }

  @Test
  @DisplayName("unrelated field never affects the request")
  void unrelatedField() {
    ClaimStateSnapshot state = ClaimStateSnapshot.builder().feeCode("PROD").build();
    assertThat(PdaRequestField.impactsPda("client.clientForename", state)).isFalse();
  }
}
