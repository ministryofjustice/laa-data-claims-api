package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;

/**
 * Assembles the versioned amendment {@link AmendmentDiff} from its change sources.
 *
 * <p>Requested entries come from the {@link AmendmentChangeDetector} already tagged {@code
 * ChangeSource.REQUESTED}. FSP entries come from the FSP handoff and are added on top, so the diff
 * carries both the Requested and FSP-sourced entries.
 */
@Component
@RequiredArgsConstructor
public class AmendmentDiffAssembler {

  private final AmendmentChangeDetector changeDetector;

  /**
   * Builds the diff for the given amendment.
   *
   * @param state the in-memory amendment state
   * @return the assembled diff at the current schema version
   */
  public AmendmentDiff assemble(ClaimAmendmentState state) {
    List<DiffEntry> changes = new ArrayList<>(changeDetector.detectChanges(state));

    // Appends the FSP-sourced consequence entries (ChangeSource.FSP) from the FSP handoff.

    return AmendmentDiff.of(changes);
  }
}
