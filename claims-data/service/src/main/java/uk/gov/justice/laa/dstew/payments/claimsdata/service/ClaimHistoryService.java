package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimValidationService.NO_CLAIM_FOUND_WITH_ID_ERROR;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimHistoryRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.PageableUtils;

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

  private final ClaimHistoryRepository claimHistoryRepository;
  private final ClaimRepository claimRepository;

  /**
   * Pageable-aware overload. If {@code pageable} is {@link Pageable#isUnpaged() unpaged} the
   * default page size is used and page 0 is returned. Otherwise the pageable's page number and size
   * are converted to a limit/offset pair and applied to the underlying query.
   */
  @Transactional(readOnly = true)
  public List<ClaimHistoryEventRow> getTimeline(UUID claimId, Pageable pageable) {
    if (pageable == null || pageable.isUnpaged()) {
      return load(claimId, PageableUtils.DEFAULT_PAGE_SIZE, PageableUtils.DEFAULT_PAGE_NUMBER);
    }

    int pageSize = pageable.getPageSize();
    long offset = pageable.getOffset();
    return load(claimId, pageSize, (int) offset);
  }

  private List<ClaimHistoryEventRow> load(UUID claimId, int pageSize, int offset) {
    if (!claimRepository.existsById(claimId)) {
      throw new ClaimNotFoundException(String.format(NO_CLAIM_FOUND_WITH_ID_ERROR, claimId));
    }
    List<ClaimHistoryEventRow> timeline =
        claimHistoryRepository.findHistory(claimId, pageSize, offset);
    log.debug("Loaded {} history event(s) for claim {}", timeline.size(), claimId);
    return timeline;
  }
}
