package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.ClaimAmendmentPersistenceService;

/**
 * Commit phase of an amendment submission (DSTEW-1771): the single atomic write transaction.
 *
 * <p>It reattaches the already-validated {@link Claim} carried from the prepare phase, then
 * persists the amendment record and applies the claim/related writes (DSTEW-1907). Reattaching
 * (rather than re-reading) is what anchors the {@code @Version} optimistic-lock guard to the
 * version read at prepare time: {@link EntityManager#merge} performs the version check against the
 * detached instance, so a concurrent modification since prepare raises {@code
 * jakarta.persistence.OptimisticLockException} (mapped to HTTP 409) and the whole transaction rolls
 * back.
 *
 * <p>This is a separate bean (not a method on {@link ClaimAmendmentService}) so the {@link
 * Transactional} boundary is applied through the Spring proxy - the orchestrator's external PDA/FSP
 * calls must run <b>before</b> this, outside the write transaction.
 */
@Service
@RequiredArgsConstructor
public class ClaimAmendmentCommitService {

  private final EntityManager entityManager;
  private final ClaimAmendmentPersistenceService persistenceService;

  /**
   * Reattaches the validated claim and persists the amendment atomically.
   *
   * @param validatedClaim the claim validated in the prepare phase (detached), whose version
   *     anchors the optimistic-lock guard
   * @param state the in-memory amendment state to persist
   * @return the inserted {@code claim_amendment} row
   */
  @Transactional
  public ClaimAmendment commit(Claim validatedClaim, ClaimAmendmentState state) {
    // Reattach the validated instance so the versioned UPDATE guards against changes since
    // the prepare step. Merge returns the managed instance to write through.
    Claim managedClaim = entityManager.merge(validatedClaim);
    return persistenceService.persistSuccessfulAmendment(managedClaim, state);
  }
}
