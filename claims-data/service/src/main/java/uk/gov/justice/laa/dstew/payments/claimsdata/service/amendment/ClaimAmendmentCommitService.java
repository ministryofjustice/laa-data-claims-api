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
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.ClaimAmendmentValidationMessagePersistenceService;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

/**
 * Service responsible for executing Phase 3 (transactional commit) of the amendment flow.
 *
 * <p>Validation runs outside transactions. This service performs all database writes inside a
 * dedicated {@link Propagation#REQUIRES_NEW} boundary so claim update, amendment history, fee
 * detail persistence and warning supersession remain atomic.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimAmendmentCommitService {

  private final EntityManager entityManager;
  private final ClaimAmendmentPersistenceService persistenceService;
  private final FeeSchemeHandoffFactory handoffFactory;
  private final CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  private final ClaimAmendmentValidationMessagePersistenceService
      validationMessagePersistenceService;

  /**
   * Commits a validated claim amendment in a single transaction.
   *
   * @param validatedClaim detached validated claim ready to merge
   * @param state amendment state built and validated by earlier phases
   * @return persisted claim amendment history row
   * @throws OptimisticLockException when the claim version was updated concurrently
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ClaimAmendment commit(Claim validatedClaim, ClaimAmendmentState state) {
    final Claim managedClaim;
    try {
      managedClaim = entityManager.merge(validatedClaim);
      entityManager.flush();
    } catch (OptimisticLockException ex) {
      log.warn(
          "event={} claimId={} submittedClaimVersion={} conflictPoint={}",
          ClaimAmendmentValidationCode.CLAIM_VERSION_CONFLICT.name(),
          validatedClaim.getId(),
          validatedClaim.getVersion(),
          "final_save");
      throw ex;
    }

    ClaimAmendment amendment = persistenceService.persistSuccessfulAmendment(managedClaim, state);

    FeeCalculationResponse feeCalcResponse = state.getFspResponseContext();
    if (feeCalcResponse != null) {
      CalculatedFeeDetail newFeeDetail =
          handoffFactory.prepareCalculatedFeeDetail(
              managedClaim, state, feeCalcResponse, amendment);
      if (newFeeDetail != null) {
        calculatedFeeDetailRepository.save(newFeeDetail);
      }

      // Persist warning supersession/write only for successful FSP-invoking amendments.
      validationMessagePersistenceService.persistCurrentWarnings(managedClaim, state);
    }

    return amendment;
  }
}
