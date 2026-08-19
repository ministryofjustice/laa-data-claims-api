package uk.gov.justice.laa.dstew.payments.claimsdata.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentReferenceData;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AmendmentReferenceDataUnavailableException;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AmendmentReasonReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.RequestedByReferenceRepository;

/**
 * Single, cached access point for the governed amendment reference data (Requested By and Amendment
 * Reason values).
 *
 * <p>The reference data changes rarely, so it is cached under {@link #CACHE_NAME} with a
 * time-to-live eviction policy (see the cache configuration). On the first access after expiry the
 * data is lazily reloaded from the database; within the window all callers share the cached copy.
 *
 * <p>A read failure is standardised here into a single controlled {@link
 * AmendmentReferenceDataUnavailableException} so every consumer reacts consistently. Whether an
 * incomplete result (either list empty) is acceptable is a per-consumer decision and is therefore
 * left to the caller. Neither failures nor empty results are cached, so the system retries on the
 * next call rather than pinning a bad state for the cache TTL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AmendmentReferenceDataProvider {

  /** Name of the cache holding the amendment reference data snapshot. */
  public static final String CACHE_NAME = "amendmentReferenceData";

  private final RequestedByReferenceRepository requestedByReferenceRepository;
  private final AmendmentReasonReferenceRepository amendmentReasonReferenceRepository;

  /**
   * Returns the governed amendment reference data, served from cache when available.
   *
   * <p>Empty results are not cached (see {@code unless}) so an unavailable state is never pinned
   * for the cache TTL.
   *
   * @return the reference data snapshot
   * @throws AmendmentReferenceDataUnavailableException if the reference data cannot be read
   */
  @Cacheable(cacheNames = CACHE_NAME, unless = "#result == null || #result.isEmpty()")
  public ClaimAmendmentReferenceData getReferenceData() {
    log.debug("Loading amendment reference data from the database");
    try {
      return new ClaimAmendmentReferenceData(
          requestedByReferenceRepository.findByOrderByDisplayOrderAsc(),
          amendmentReasonReferenceRepository.findByOrderByRequestedByCodeAscDisplayOrderAsc());
    } catch (DataAccessException ex) {
      log.error("Failed to load governed amendment reference data", ex);
      throw new AmendmentReferenceDataUnavailableException(ex);
    }
  }
}
