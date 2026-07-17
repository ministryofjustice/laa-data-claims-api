package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;

/**
 * Read-model repository that assembles a single claim's complete history as a unified,
 * chronological timeline.
 *
 * <p>Implementations must satisfy the query with a <strong>single</strong> SQL statement (no
 * per-source queries merged in Java, no entity loading and no lazy initialisation), returning
 * events ordered by {@code event_timestamp DESC, source_id DESC}. The UUIDv7 {@code source_id}
 * provides a deterministic tie-breaker and the seed for future keyset (cursor) pagination.
 */
public interface ClaimHistoryRepository {

  /**
   * Retrieves the most recent slice of a claim's history timeline.
   *
   * @param claimId the claim whose history should be retrieved
   * @param limit the maximum number of events to return (page size)
   * @return events ordered newest-first by {@code event_timestamp DESC, source_id DESC}
   */
  List<ClaimHistoryEventRow> findHistory(UUID claimId, int limit);
}
