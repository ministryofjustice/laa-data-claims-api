package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.PreparedAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimStateSnapshotMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.state.ClaimAmendmentStateBuilder;

/**
 * Retrieves the current claim aggregate by id, captures the before-state, and builds the in-memory
 * post-amendment state by applying the sparse amendment payload.
 *
 * <p>This step performs no persistence and makes no external calls. A missing claim is reported as
 * {@link Optional#empty()} so the parent orchestration can map it to {@code
 * INVALID_CLAIM_NOT_FOUND} without relying on exception flow.
 *
 * <p>On success it returns a {@link PreparedAmendment} carrying both the built state and the
 * managed {@link Claim} entity read here, so the caller can thread that exact instance (with its
 * {@code @Version}) into the commit phase rather than re-reading the row.
 *
 * <p>No transaction is declared here on purpose: this read is one step of the single atomic
 * amendment transaction (retrieve, validate, then save) owned by the parent orchestrator. It must
 * therefore run within the caller's transaction, which also keeps the persistence context open for
 * the mapper's lazy navigation (e.g. {@code claim.submission}).
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
   * @param claim the claim
   * @param payload the sparse amendment payload as submitted
   * @return the prepared amendment (the managed claim and the built state), or {@link
   *     Optional#empty()} if the claim does not exist
   */
  public Optional<PreparedAmendment> retrieveAmendmentState(
      UUID claimId, ClaimAmendmentPayload payload) {

    Optional<Claim> claim = claimRepository.findById(claimId);
    if (claim.isEmpty()) {
      log.debug("No claim found for id {} during amendment retrieval", claimId);
      return Optional.empty();
    }

    ClaimStateSnapshot beforeState =
        snapshotMapper.toSnapshot(
            claim,
            clientRepository.findByClaimId(claim.getId()),
            claimCaseRepository.findByClaimId(claim.getId()),
            claimSummaryFeeRepository.findByClaimId(claim.getId()),
            calculatedFeeDetailRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(
                claim.getId()),
            assessmentRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(claim.getId()));

    ClaimAmendmentState state = amendmentStateBuilder.buildAmendmentState(beforeState, payload);
    return Optional.of(new PreparedAmendment(claim.get(), state));
  }
}
