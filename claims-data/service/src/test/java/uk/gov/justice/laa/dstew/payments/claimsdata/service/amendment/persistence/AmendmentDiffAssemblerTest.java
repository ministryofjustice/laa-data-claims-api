package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ChangeSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;

/** Tests for {@link AmendmentDiffAssembler}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AmendmentDiffAssembler Tests")
class AmendmentDiffAssemblerTest {

  @Mock private AmendmentChangeDetector changeDetector;

  @InjectMocks private AmendmentDiffAssembler assembler;

  private final ClaimAmendmentState state = ClaimAmendmentState.builder().build();

  @Test
  @DisplayName("maps each classified change to a Requested diff entry")
  void mapsClassifiedChangesToRequestedEntries() {
    when(changeDetector.detectChanges(state))
        .thenReturn(List.of(new DiffEntry("claim.feeCode", ChangeSource.REQUESTED, "OLD", "NEW")));

    AmendmentDiff diff = assembler.assemble(state);

    assertThat(diff.schemaVersion()).isEqualTo(AmendmentDiff.CURRENT_SCHEMA_VERSION);
    assertThat(diff.changes()).hasSize(1);
    assertThat(diff.changes().getFirst().changeSource()).isEqualTo(ChangeSource.REQUESTED);
    assertThat(diff.changes().getFirst().fieldIdentifier()).isEqualTo("claim.feeCode");
  }

  @Test
  @DisplayName("produces a well-formed empty diff when there are no changes")
  void producesEmptyDiffWhenNoChanges() {
    when(changeDetector.detectChanges(state)).thenReturn(List.of());

    AmendmentDiff diff = assembler.assemble(state);

    assertThat(diff.schemaVersion()).isEqualTo(AmendmentDiff.CURRENT_SCHEMA_VERSION);
    assertThat(diff.changes()).isEmpty();
  }
}
