package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ChangeSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;

/** Tests for {@link AmendmentChangeDetector}. */
@DisplayName("AmendmentChangeDetector Tests")
class AmendmentChangeDetectorTest {

  private final AmendmentChangeDetector detector = new AmendmentChangeDetector();

  private static ClaimAmendmentState state(ClaimStateSnapshot before, ClaimStateSnapshot after) {
    return ClaimAmendmentState.builder().beforeState(before).postAmendmentState(after).build();
  }

  @Test
  @DisplayName("emits a change for a value that differs, across tables, and ignores equal fields")
  void emitsChangesForDifferingValues() {
    ClaimStateSnapshot before =
        ClaimStateSnapshot.builder().feeCode("OLD").clientSurname("Smith").build();
    ClaimStateSnapshot after =
        ClaimStateSnapshot.builder().feeCode("NEW").clientSurname("Smith").build();

    List<DiffEntry> changes = detector.detectChanges(state(before, after));

    assertThat(changes).hasSize(1);
    DiffEntry change = changes.getFirst();
    assertThat(change.fieldIdentifier()).isEqualTo("claim.feeCode");
    assertThat(change.changeSource()).isEqualTo(ChangeSource.REQUESTED);
    assertThat(change.before()).isEqualTo("OLD");
    assertThat(change.after()).isEqualTo("NEW");
  }

  @Test
  @DisplayName("treats an explicit null over a stored value as a clear")
  void treatsExplicitNullAsClear() {
    ClaimStateSnapshot before = ClaimStateSnapshot.builder().clientSurname("Smith").build();
    ClaimStateSnapshot after = ClaimStateSnapshot.builder().clientSurname(null).build();

    List<DiffEntry> changes = detector.detectChanges(state(before, after));

    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst().fieldIdentifier()).isEqualTo("client.clientSurname");
    assertThat(changes.getFirst().before()).isEqualTo("Smith");
    assertThat(changes.getFirst().after()).isNull();
  }

  @Test
  @DisplayName("emits no changes when before and after are identical (omitted / no-op)")
  void emitsNoChangesWhenIdentical() {
    ClaimStateSnapshot snapshot =
        ClaimStateSnapshot.builder().feeCode("SAME").netProfitCostsAmount(null).build();

    List<DiffEntry> changes = detector.detectChanges(state(snapshot, snapshot));

    assertThat(changes).isEmpty();
  }

  @Test
  @DisplayName("emits a change for a claim_summary_fee field")
  void emitsChangeForSummaryFeeField() {
    ClaimStateSnapshot before =
        ClaimStateSnapshot.builder()
            .netProfitCostsAmount(new java.math.BigDecimal("10.00"))
            .build();
    ClaimStateSnapshot after =
        ClaimStateSnapshot.builder()
            .netProfitCostsAmount(new java.math.BigDecimal("25.00"))
            .build();

    List<DiffEntry> changes = detector.detectChanges(state(before, after));

    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst().fieldIdentifier())
        .isEqualTo("claimSummaryFee.netProfitCostsAmount");
  }
}
