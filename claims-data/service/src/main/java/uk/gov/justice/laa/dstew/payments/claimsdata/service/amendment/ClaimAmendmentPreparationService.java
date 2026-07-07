package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.PreparedAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;

/**
 * Prepare phase of an amendment submission (DSTEW-1771): retrieves the claim and builds the
 * before/post-amendment state (DSTEW-1763).
 *
 * <p>This is the one part of the flow that needs an open persistence context: it runs in a
 * <b>read-only</b> transaction so the retrieval mapper's lazy navigation (e.g. {@code
 * claim.submission}) works and so the claim and its state are read consistently. It performs no
 * writes.
 *
 * <p>It deliberately does <b>not</b> run validation: the validation steps (including the external
 * PDA/FSP steps) run afterwards with no held transaction, so those external calls never hold a DB
 * connection open. The returned {@link PreparedAmendment#claim()} is detached once this transaction
 * closes and is reattached by the commit phase.
 */
@Service
@RequiredArgsConstructor
public class ClaimAmendmentPreparationService {

  private final ClaimAmendmentStateService stateService;

  /**
   * Retrieves the claim and builds the amendment state.
   *
   * @param claimId the claim being amended
   * @param payload the sparse, presence-aware amendment payload as submitted
   * @return the prepared amendment (the read claim and the built state)
   * @throws ClaimNotFoundException if no claim exists for {@code claimId}
   */
  @Transactional(readOnly = true)
  public PreparedAmendment prepare(UUID claimId, ClaimAmendmentPayload payload) {
    return stateService
        .retrieveAmendmentState(claimId, payload)
        .orElseThrow(() -> new ClaimNotFoundException("No claim found with id " + claimId));
  }
}
