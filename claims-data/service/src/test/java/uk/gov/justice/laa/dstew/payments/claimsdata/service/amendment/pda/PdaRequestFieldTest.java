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
  @DisplayName("officeAccountNumber")
  class OfficeAccountNumber {

    @Test
    @DisplayName("always affects the request regardless of dates")
    void alwaysAffects() {
      assertThat(
              PdaRequestField.impactsPda(
                  "officeAccountNumber", ClaimStateSnapshot.builder().build()))
          .isTrue();
    }
  }

  @Nested
  @DisplayName("feeCode")
  class FeeCode {

    @Test
    @DisplayName("always affects when PROD and concluded date present")
    void prodWithConcluded() {
      ClaimStateSnapshot state =
          ClaimStateSnapshot.builder().feeCode("PROD").caseConcludedDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("feeCode", state)).isTrue();
    }

    @Test
    @DisplayName("always affects even when PROD but no concluded date")
    void prodWithoutConcluded() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().feeCode("PROD").build();
      assertThat(PdaRequestField.impactsPda("feeCode", state)).isTrue();
    }

    @Test
    @DisplayName("always affects when a concluded date is present even if new fee code is not PROD")
    void notProdButConcludedPresent() {
      // feeCode-derived data is compared against the PDA response for validation, so any feeCode
      // change affects the requirement to use the PDA regardless of the other fields.
      ClaimStateSnapshot state =
          ClaimStateSnapshot.builder().feeCode("OTHER").caseConcludedDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("feeCode", state)).isTrue();
    }

    @Test
    @DisplayName("always affects even with no relevant dates populated")
    void alwaysImpacts() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().feeCode("OTHER").build();
      assertThat(PdaRequestField.impactsPda("feeCode", state)).isTrue();
    }
  }

  @Nested
  @DisplayName("caseConcludedDate")
  class CaseConcludedDate {

    @Test
    @DisplayName("affects when PROD")
    void prod() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().feeCode("PROD").build();
      assertThat(PdaRequestField.impactsPda("caseConcludedDate", state)).isTrue();
    }

    @Test
    @DisplayName("does not affect when not PROD")
    void notProd() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().feeCode("OTHER").build();
      assertThat(PdaRequestField.impactsPda("caseConcludedDate", state)).isFalse();
    }
  }

  @Nested
  @DisplayName("caseStartDate")
  class CaseStartDate {

    @Test
    @DisplayName("affects unless PROD+concluded already wins")
    void affectsWhenProdNotConcluding() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().feeCode("OTHER").build();
      assertThat(PdaRequestField.impactsPda("caseStartDate", state)).isTrue();
    }

    @Test
    @DisplayName("does not affect when PROD+concluded wins")
    void noEffectWhenProdConcluded() {
      ClaimStateSnapshot state =
          ClaimStateSnapshot.builder().feeCode("PROD").caseConcludedDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("caseStartDate", state)).isFalse();
    }
  }

  @Nested
  @DisplayName("representationOrderDate")
  class RepresentationOrderDate {

    @Test
    @DisplayName("affects when no higher-priority date present")
    void affectsWhenNothingHigher() {
      assertThat(
              PdaRequestField.impactsPda(
                  "representationOrderDate", ClaimStateSnapshot.builder().build()))
          .isTrue();
    }

    @Test
    @DisplayName("does not affect when caseStartDate present")
    void noEffectWhenCaseStart() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().caseStartDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("representationOrderDate", state)).isFalse();
    }

    @Test
    @DisplayName("does not affect when PROD+concluded wins")
    void noEffectWhenProdConcluded() {
      ClaimStateSnapshot state =
          ClaimStateSnapshot.builder().feeCode("PROD").caseConcludedDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("representationOrderDate", state)).isFalse();
    }
  }

  @Nested
  @DisplayName("uniqueFileNumber")
  class UniqueFileNumber {

    @Test
    @DisplayName("affects when no higher-priority date present")
    void affectsWhenNothingHigher() {
      assertThat(
              PdaRequestField.impactsPda("uniqueFileNumber", ClaimStateSnapshot.builder().build()))
          .isTrue();
    }

    @Test
    @DisplayName("changing UFN makes no difference when concluded date wins")
    void noEffectWhenProdConcluded() {
      ClaimStateSnapshot state =
          ClaimStateSnapshot.builder().feeCode("PROD").caseConcludedDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("uniqueFileNumber", state)).isFalse();
    }

    @Test
    @DisplayName("does not affect when caseStartDate present")
    void noEffectWhenCaseStart() {
      ClaimStateSnapshot state = ClaimStateSnapshot.builder().caseStartDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("uniqueFileNumber", state)).isFalse();
    }

    @Test
    @DisplayName("does not affect when representationOrderDate present")
    void noEffectWhenRepOrder() {
      ClaimStateSnapshot state =
          ClaimStateSnapshot.builder().representationOrderDate(ANY_DATE).build();
      assertThat(PdaRequestField.impactsPda("uniqueFileNumber", state)).isFalse();
    }
  }

  @Test
  @DisplayName("unrelated field never affects the request")
  void unrelatedField() {
    ClaimStateSnapshot state = ClaimStateSnapshot.builder().feeCode("PROD").build();
    assertThat(PdaRequestField.impactsPda("clientForename", state)).isFalse();
  }
}
