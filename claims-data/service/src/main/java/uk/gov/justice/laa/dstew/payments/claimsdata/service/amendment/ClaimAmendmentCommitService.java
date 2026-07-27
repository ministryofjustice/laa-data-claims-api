package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeSchemeHandoffFactory;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.ClaimAmendmentPersistenceService;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

/**
 * Service responsible for executing <b>Phase 3: Commit</b> of the claim amendment orchestration
 * flow
 *
 * <p>Unlike Phase 2 (Validation) which runs entirely outside database transactions to prevent
 * connection holding during external platform network requests, this service executes inside a
 * strict, isolated write transaction boundary ({@link Propagation#REQUIRES_NEW}).
 *
 * <p>Its responsibilities are highly critical to the integrity of the database:
 *
 * <ul>
 *   <li><b>Optimistic Concurrency Control:</b> Reattaches the detached {@link Claim} entity to the
 *       persistence context, anchoring the {@code @Version} check to the snapshot value read at
 *       preparation time (Phase 1). Any concurrent modifications made by other threads or users in
 *       the interim will raise a {@code jakarta.persistence.OptimisticLockException} and discard
 *       the amendment cleanly.
 *   <li><b>Auditable History Tracking:</b> Saves the core {@link ClaimAmendment} history record
 *       containing before/after snapshot JSON states and sparse request payloads.
 *   <li><b>FSP Recalculation Handoff (1595-F):</b> If a Fee Scheme Platform (FSP) calculation
 *       context is active on the state aggregate, this service prepares and persists a new,
 *       amendment-linked {@link CalculatedFeeDetail} record. Old calculations are preserved intact,
 *       and the new calculation row is physically bound to the generated amendment ID.
 * </ul>
 *
 * <p>If any single step fails or throws an exception within this block, the entire transaction
 * rolls back, ensuring that the claim state, audit trail, and FSP monetary changes remain entirely
 * atomic.
 *
 * @see uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentService
 * @see
 *     uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentFspValidationStep
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimAmendmentCommitService {

  private final EntityManager entityManager;
  private final ClaimAmendmentPersistenceService persistenceService;
  private final FeeSchemeHandoffFactory handoffFactory;
  private final CalculatedFeeDetailRepository calculatedFeeDetailRepository;

  /**
   * Commits all pending validated modifications to the database in a single atomic write
   * transaction.
   *
   * <p>This method executes a precise 3-step sequence:
   *
   * <ol>
   *   <li>Merges the detached {@link Claim} aggregate back into the active {@link EntityManager}
   *       context and forces a flush, which executes the versioned {@code UPDATE} and triggers the
   *       optimistic lock check <em>inside</em> this method (rather than later at transaction
   *       commit, where it would escape the guard).
   *   <li>Saves the audit history record for the requested changes via the persistence helper.
   *   <li>Pulls the FSP response context cached in Phase 2, maps it to a database entity, and
   *       records the updated fee details alongside the amendment.
   * </ol>
   *
   * @param validatedClaim the detached {@link Claim} object containing pre-validated state to
   *     reattach
   * @param state the active, in-memory {@link ClaimAmendmentState} holding current metadata and the
   *     cached FSP calculation context
   * @return the persisted {@link ClaimAmendment} audit record
   * @throws jakarta.persistence.OptimisticLockException if a concurrent update was committed prior
   *     to this step
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ClaimAmendment commit(Claim validatedClaim, ClaimAmendmentState state) {

    // 1. Reattach the validated claim and force the versioned UPDATE to run now. The claim is the
    // only entity in this unit of work carrying an @Version - the amendment and fee-detail rows
    // below are always inserts - so this flush is the single point where an optimistic-lock
    // conflict can surface. Flushing here raises it inside the try/catch instead of at the later
    // transaction commit, where the structured guard warning would be missed.
    final Claim managedClaim;
    try {
      managedClaim = entityManager.merge(validatedClaim);
      entityManager.flush();
    } catch (OptimisticLockException ex) {
      // Structured warning for support/investigation at the final transactional guard. Safe fields
      // only - never the amendment payload values or financial details. The current version is not
      // available here (the stale row was updated by a concurrent writer), so only the submitted
      // (prepare-time) version is logged.
      log.warn(
          "event={} claimId={} submittedClaimVersion={} conflictPoint={}",
          ClaimAmendmentValidationCode.CLAIM_VERSION_CONFLICT.name(),
          validatedClaim.getId(),
          validatedClaim.getVersion(),
          "final_save");
      throw ex;
    }

    // 2. Persist the core claim_amendment history record (insert - no version check).
    ClaimAmendment amendment = persistenceService.persistSuccessfulAmendment(managedClaim, state);

    // 3. 1595-F: If an FSP repricing happened, prepare and save the row linked to this amendment
    // (insert - no version check).
    FeeCalculationResponse feeCalcResponse = state.getFspResponseContext();
    if (feeCalcResponse != null) {
      CalculatedFeeDetail newFeeDetail =
          handoffFactory.prepareCalculatedFeeDetail(
              managedClaim, state, feeCalcResponse, amendment);

      if (newFeeDetail != null) {
        calculatedFeeDetailRepository.save(newFeeDetail);
      }
    }

    return amendment;
  }
}
