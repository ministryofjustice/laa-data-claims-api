package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentFeatureFlagValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentReferenceValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentUserIdValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimStatusValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimVersionValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.PreparedAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;

/**
 * End-to-end orchestrator for a single claim amendment (the atomic-save story, DSTEW-1771). It
 * sequences the flow, keeping each part in the right transaction boundary:
 *
 * <ol>
 *   <li><b>Prepare</b> ({@link ClaimAmendmentPreparationService}, read-only transaction): retrieve
 *       the claim and build the state (DSTEW-1763). This is the only part that needs an open
 *       persistence context (for the mapper's lazy navigation).
 *   <li><b>Validate</b> ({@link ClaimAmendmentValidationService}, <b>no held transaction</b>): run
 *       the ordered validation steps. This includes the external PDA (DSTEW-1646/1772-1774) and FSP
 *       (DSTEW-1758-1762) steps, which are ordinary error-collecting steps interspersed in {@code
 *       STEP_ORDER}; running with no held transaction is exactly what lets those external calls sit
 *       inline without holding a DB connection or claim-row lock open. On any error the amendment
 *       is rejected and nothing is written.
 *   <li><b>Commit</b> ({@link ClaimAmendmentCommitService}, write transaction): reattach the
 *       prepared claim (anchoring the {@code @Version} optimistic-lock guard to the version read at
 *       prepare time) and persist the amendment record and claim/related writes (DSTEW-1907)
 *       atomically.
 * </ol>
 *
 * <p>This class is intentionally <b>not</b> {@code @Transactional}: only prepare (read) and commit
 * (write) own a transaction, and the validation sequence in between owns none - so the external
 * calls it contains never run inside a transaction. A concurrent modification detected by the
 * commit guard raises {@code jakarta.persistence.OptimisticLockException} (mapped to HTTP 409) and
 * rolls the write back.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimAmendmentService {

  private final ClaimAmendmentPreparationService preparationService;
  private final ClaimAmendmentValidationService validationService;
  private final ClaimAmendmentCommitService commitService;

  /**
   * Submits an amendment for the given claim end to end: prepare (retrieve), validate (including
   * the external PDA/FSP steps), then an atomic commit.
   *
   * @param claimId the claim being amended
   * @param payload the sparse, presence-aware amendment payload as submitted
   * @return a success result carrying the persisted amendment, or a rejection carrying the
   *     collected validation errors
   * @throws ClaimNotFoundException if no claim exists for {@code claimId}
   */
  public ClaimAmendmentResult submitAmendment(UUID claimId, ClaimAmendmentPayload payload) {
    // Phase 1 - prepare: retrieve + build state in a read-only transaction (no writes).
    PreparedAmendment prepared = preparationService.prepare(claimId, payload);

    // Phase 2 - validate: run the ordered steps with no held transaction, so the inline external
    // PDA/FSP steps never hold a DB connection open.
    List<ClaimAmendmentValidationError> errors =
        validationService.validateAmendmentRequest(prepared.state());
    if (!errors.isEmpty()) {
      log.debug(
          "Amendment for claim {} rejected with {} validation error(s)", claimId, errors.size());
      return ClaimAmendmentResult.rejected(errors);
    }

    // Phase 3 - commit: single atomic write transaction; reattaches the prepared claim.
    ClaimAmendment amendment = commitService.commit(prepared.claim(), prepared.state());
    log.debug("Persisted amendment {} for claim {}", amendment.getId(), claimId);
    return ClaimAmendmentResult.success(amendment);
  }
}
