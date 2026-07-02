package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;

/**
 * Assembles the versioned amendment {@link AmendmentDiff} from the change sources owned by sibling
 * tickets.
 *
 * <p>Requested entries come from the {@link AmendmentChangeDetector} already tagged {@code
 * ChangeSource.REQUESTED}. FSP entries come from the FSP handoff (DSTEW-1762) and are added when
 * that ticket lands; until then the diff contains the Requested entries only.
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

    // FSP-sourced consequence entries (ChangeSource.FSP) are added by DSTEW-1762's handoff.

    return AmendmentDiff.of(changes);
  }
}
