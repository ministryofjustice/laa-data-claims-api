package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimValidationService.NO_CLAIM_FOUND_WITH_ID_ERROR;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimHistoryRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;

/**
 * Read-only service that returns a claim's activity as a single chronological timeline.
 *
 * <p>Delegates to {@link ClaimHistoryRepository}, which composes the timeline from the claim,
 * amendment and assessment tables in one query. This service performs no additional data access and
 * touches no entities, so it is safe to run outside an open persistence context.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimHistoryService {

  /** Default page size used when a caller does not specify one. */
  private static final int DEFAULT_PAGE_SIZE = 50;

  private final ClaimHistoryRepository claimHistoryRepository;
  private final ClaimRepository claimRepository;

  /**
   * Returns the most recent page of a claim's history timeline, newest event first.
   *
   * @param claimId the claim to retrieve history for
   * @return an ordered list of timeline events (may be empty, never {@code null})
   * @throws ClaimNotFoundException if no claim exists for the given claim id
   */
  @Transactional(readOnly = true)
  public List<ClaimHistoryEventRow> getTimeline(UUID claimId) {
    return load(claimId, DEFAULT_PAGE_SIZE);
  }

  /**
   * Returns the most recent page of a claim's history timeline, newest event first.
   *
   * @param claimId the claim to retrieve history for
   * @param pageSize the maximum number of events to return
   * @return an ordered list of timeline events (may be empty, never {@code null})
   * @throws ClaimNotFoundException if no claim exists for the given claim id
   */
  @Transactional(readOnly = true)
  public List<ClaimHistoryEventRow> getTimeline(UUID claimId, int pageSize) {
    return load(claimId, pageSize);
  }

  private List<ClaimHistoryEventRow> load(UUID claimId, int pageSize) {
    if (!claimRepository.existsById(claimId)) {
      throw new ClaimNotFoundException(String.format(NO_CLAIM_FOUND_WITH_ID_ERROR, claimId));
    }

    List<ClaimHistoryEventRow> timeline = claimHistoryRepository.findHistory(claimId, pageSize);
    log.debug("Loaded {} history event(s) for claim {}", timeline.size(), claimId);
    return timeline;
  }
}
