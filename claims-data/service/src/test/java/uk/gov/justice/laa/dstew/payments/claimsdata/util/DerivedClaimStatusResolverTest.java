package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.DerivedClaimStatus;

@DisplayName("DerivedClaimStatusResolver")
class DerivedClaimStatusResolverTest {

  @DisplayName("resolves the full derivation truth table")
  @ParameterizedTest(name = "[{index}] status={0}, hasAssessment={1}, isAmended={2} -> {3}")
  @CsvSource({
    // VOID always -> VOIDED
    "VOID,             false, false, VOIDED",
    "VOID,             false, true,  VOIDED",
    "VOID,             true,  false, VOIDED",
    "VOID,             true,  true,  VOIDED",
    // INVALID always -> INVALID
    "INVALID,          false, false, INVALID",
    "INVALID,          false, true,  INVALID",
    "INVALID,          true,  false, INVALID",
    "INVALID,          true,  true,  INVALID",
    // READY_TO_PROCESS always -> READY_TO_PROCESS
    "READY_TO_PROCESS, false, false, READY_TO_PROCESS",
    "READY_TO_PROCESS, false, true,  READY_TO_PROCESS",
    "READY_TO_PROCESS, true,  false, READY_TO_PROCESS",
    "READY_TO_PROCESS, true,  true,  READY_TO_PROCESS",
    // VALID -> depends on the flags, assessment taking precedence over amendment
    "VALID,            false, false, ACCEPTED",
    "VALID,            false, true,  AMENDED",
    "VALID,            true,  false, ASSESSED",
    "VALID,            true,  true,  ASSESSED",
  })
  void resolvesTruthTable(
      ClaimStatus status, boolean hasAssessment, boolean isAmended, DerivedClaimStatus expected) {
    assertThat(DerivedClaimStatusResolver.resolve(status, hasAssessment, isAmended))
        .isEqualTo(expected);
  }

  @Test
  @DisplayName("null hasAssessment / isAmended are treated as false")
  void nullBooleansTreatedAsFalse() {
    assertThat(
            DerivedClaimStatusResolver.resolve(ClaimStatus.VALID, (Boolean) null, (Boolean) null))
        .isEqualTo(DerivedClaimStatus.ACCEPTED);
    assertThat(DerivedClaimStatusResolver.resolve(ClaimStatus.VALID, null, Boolean.TRUE))
        .isEqualTo(DerivedClaimStatus.AMENDED);
    assertThat(DerivedClaimStatusResolver.resolve(ClaimStatus.VALID, Boolean.TRUE, null))
        .isEqualTo(DerivedClaimStatus.ASSESSED);
  }

  @Test
  @DisplayName("null claim_status is rejected")
  void nullStatusRejected() {
    assertThatNullPointerException()
        .isThrownBy(() -> DerivedClaimStatusResolver.resolve(null, false, false));
  }

  @Test
  @DisplayName("enum declaration order is the canonical business ordering")
  void canonicalOrderIsStable() {
    // This ordering is the single source of truth for precedence and sort ordering; if it changes
    // the response contract and sort semantics change with it, so lock it down here.
    assertThat(DerivedClaimStatus.values())
        .containsExactly(
            DerivedClaimStatus.ACCEPTED,
            DerivedClaimStatus.AMENDED,
            DerivedClaimStatus.ASSESSED,
            DerivedClaimStatus.VOIDED,
            DerivedClaimStatus.INVALID,
            DerivedClaimStatus.READY_TO_PROCESS);
  }
}
