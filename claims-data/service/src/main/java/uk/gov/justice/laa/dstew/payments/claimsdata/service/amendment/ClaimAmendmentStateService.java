package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimStateSnapshotMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;

/**
 * Retrieves the current claim aggregate by id, captures the before-state, and builds the in-memory
 * post-amendment state by applying the sparse amendment payload.
 *
 * <p>This step performs no persistence and makes no external calls. A missing claim is reported as
 * {@link Optional#empty()} so the parent orchestration can map it to {@code
 * INVALID_CLAIM_NOT_FOUND} without relying on exception flow.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimAmendmentStateService {

  private final ClaimRepository claimRepository;
  private final ClientRepository clientRepository;
  private final ClaimCaseRepository claimCaseRepository;
  private final ClaimSummaryFeeRepository claimSummaryFeeRepository;
  private final CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  private final AssessmentRepository assessmentRepository;
  private final ClaimStateSnapshotMapper snapshotMapper;
  private final ClaimAmendmentStateBuilder amendmentStateBuilder;

  /**
   * Retrieves the claim and builds the amendment state.
   *
   * @param claimId the claim identifier
   * @param payload the sparse amendment payload as submitted
   * @return the built amendment state, or {@link Optional#empty()} if the claim does not exist
   */
  @Transactional(readOnly = true)
  public Optional<ClaimAmendmentState> retrieveAmendmentState(
      UUID claimId, ClaimAmendmentPayload payload) {

    Optional<Claim> claim = claimRepository.findById(claimId);
    if (claim.isEmpty()) {
      log.debug("No claim found for id {} during amendment retrieval", claimId);
      return Optional.empty();
    }

    ClaimStateSnapshot beforeState =
        snapshotMapper.toSnapshot(
            claim.get(),
            clientRepository.findByClaimId(claimId),
            claimCaseRepository.findByClaimId(claimId),
            claimSummaryFeeRepository.findByClaimId(claimId),
            calculatedFeeDetailRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(claimId),
            assessmentRepository.findFirstByClaimIdOrderByCreatedOnDesc(claimId));

    return Optional.of(amendmentStateBuilder.buildAmendmentState(beforeState, payload));
  }
}
