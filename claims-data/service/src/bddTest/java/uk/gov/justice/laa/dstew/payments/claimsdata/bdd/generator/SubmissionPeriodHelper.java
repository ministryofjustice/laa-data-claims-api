package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator;

import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;

/**
 * Java replacement for {@code submissionPeriodHelper.ts}. Returns an unused {@code MMM-uuuu}
 * submission period for the given (office, area-of-law) tuple by walking from the most recent
 * eligible period backwards and skipping any already persisted in the DB.
 *
 * <p>This is intentionally far simpler than the TS original: the BDD harness uses a fresh
 * Testcontainers Postgres so we don't need the provider-contract / FSP-fee API checks the TS code
 * does for the UAT environment.
 */
@Component
public class SubmissionPeriodHelper {

  /**
   * In-process cache of periods already handed out per (office, area-of-law) tuple — guards against
   * issuing the same period twice within a single Cucumber run even before the DB is updated.
   */
  private final ConcurrentMap<String, Set<String>> issued = new ConcurrentHashMap<>();

  private final SubmissionRepository submissionRepository;

  @Autowired
  public SubmissionPeriodHelper(SubmissionRepository submissionRepository) {
    this.submissionRepository = submissionRepository;
  }

  /**
   * Picks a fresh {@code MMM-uuuu} period for the given office + area of law. Walks from the latest
   * complete prior month backwards until we hit one not yet used.
   */
  public String nextAvailablePeriod(String office, AreaOfLaw areaOfLaw) {
    String cacheKey = office + "|" + areaOfLaw.name();
    Set<String> used = issued.computeIfAbsent(cacheKey, k -> ConcurrentHashMap.newKeySet());

    LocalDate cursor = LocalDate.now().minusMonths(1).withDayOfMonth(1);
    LocalDate floor = LocalDate.of(BddTestConstants.EARLIEST_SUBMISSION_YEAR, 1, 1);

    while (!cursor.isBefore(floor)) {
      String candidate = formatPeriod(cursor);
      if (!used.contains(candidate) && !persisted(office, areaOfLaw, candidate)) {
        used.add(candidate);
        return candidate;
      }
      cursor = cursor.minusMonths(1);
    }

    throw new IllegalStateException("Exhausted all candidate submission periods for " + cacheKey);
  }

  private boolean persisted(String office, AreaOfLaw areaOfLaw, String period) {
    // Cheap existence check using the existing JPA query — relies on @Query-generated method or
    // falls back to streaming all. The submission table stays tiny in the BDD harness so this is
    // safe.
    return submissionRepository.findAll().stream()
        .anyMatch(
            s ->
                office.equals(s.getOfficeAccountNumber())
                    && areaOfLaw == s.getAreaOfLaw()
                    && period.equals(s.getSubmissionPeriod()));
  }

  /** Clears the in-process used-period cache (called by {@code @Before} BDD hook). */
  public void reset() {
    issued.clear();
  }

  private static String formatPeriod(LocalDate date) {
    return BddTestConstants.MONTHS.get(date.getMonthValue() - 1) + "-" + date.getYear();
  }
}
