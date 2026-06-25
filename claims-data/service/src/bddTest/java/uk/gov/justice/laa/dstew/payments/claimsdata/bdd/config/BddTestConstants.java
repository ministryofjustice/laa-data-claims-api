package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config;

import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Central holder for constants reused across BDD step definitions, support helpers and generators.
 *
 * <p>The values here were previously duplicated across multiple classes (e.g. {@code MONTHS},
 * {@code POLL_INTERVAL}, {@code DEFAULT_OFFICE}, API paths). Keeping them in a single place avoids
 * drift between scenarios and gives future tests a single import point for the canonical defaults
 * used by the BDD harness.
 *
 * <p>This class is intentionally a {@code final} non-instantiable holder of {@code public static
 * final} values rather than a Spring {@code @Configuration} - none of the values need DI, and
 * referring to them statically keeps step definitions concise.
 */
public final class BddTestConstants {

  private BddTestConstants() {
    // utility class - no instances
  }

  // ---------------------------------------------------------------------------
  // Submission period generation
  // ---------------------------------------------------------------------------

  /** Three-letter month abbreviations used when formatting {@code MMM-uuuu} submission periods. */
  public static final List<String> MONTHS =
      List.of("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC");

  /** Earliest year considered when picking an unused submission period. */
  public static final int EARLIEST_SUBMISSION_YEAR = 2018;

  // ---------------------------------------------------------------------------
  // Default test data
  // ---------------------------------------------------------------------------

  /** Office account number reused across scenarios that don't override the office explicitly. */
  public static final String DEFAULT_OFFICE = "0U099L";

  /** Pool of office account numbers used by the Legal Help file generator. */
  public static final List<String> DEFAULT_OFFICES =
      List.of("0U099L", "0P322F", "2L847Q", "2N199K", "2P746R", "1T102C");

  /** Default Legal Help fee codes used when no per-claim override is supplied. */
  public static final List<String> DEFAULT_FEE_CODES = List.of("CAPA", "COM");

  // ---------------------------------------------------------------------------
  // Async polling
  // ---------------------------------------------------------------------------

  /** Interval between successive polls when waiting for an async outcome. */
  public static final Duration POLL_INTERVAL = Duration.ofMillis(250);

  /**
   * Short polling budget used when waiting for bulk-submission terminal status. The harness does
   * not run the event-service, so transitions are not expected to complete; the short budget keeps
   * the suite fast while still allowing the immediate {@code READY_FOR_PARSING} status to surface.
   */
  public static final Duration BULK_STATUS_POLL_TIMEOUT = Duration.ofSeconds(3);

  /** Longer polling budget for async validation-message / DB-persistence assertions. */
  public static final Duration VALIDATION_POLL_TIMEOUT = Duration.ofSeconds(10);

  /** Terminal states the bulk-submission summary endpoint can legitimately reach. */
  public static final Set<String> BULK_TERMINAL_STATES =
      Set.of("PARSING_COMPLETED", "PARSING_FAILED", "VALIDATION_FAILED", "VALIDATION_SUCCEEDED");

  // ---------------------------------------------------------------------------
  // API paths (relative to the running server's base URL)
  // ---------------------------------------------------------------------------

  /** {@code POST /api/v1/bulk-submissions}. */
  public static final String POST_BULK_SUBMISSION_PATH = API_URI_PREFIX + "/bulk-submissions";

  /** {@code GET /api/v1/submissions}. */
  public static final String GET_SUBMISSIONS_PATH = API_URI_PREFIX + "/submissions";

  /** {@code GET /api/v1/submissions/{id}}. */
  public static final String GET_SUBMISSION_BY_ID_PATH = API_URI_PREFIX + "/submissions/{id}";

  /** {@code GET /api/v1/bulk-submissions/{id}}. */
  public static final String GET_BULK_SUBMISSION_BY_ID_PATH =
      API_URI_PREFIX + "/bulk-submissions/{id}";

  /** {@code GET /api/v1/bulk-submissions/{id}/summary}. */
  public static final String BULK_SUBMISSION_SUMMARY_PATH =
      API_URI_PREFIX + "/bulk-submissions/{id}/summary";

  /** {@code PATCH /api/v1/bulk-submissions/{id}}. */
  public static final String PATCH_BULK_SUBMISSION_PATH = API_URI_PREFIX + "/bulk-submissions/{id}";

  /** {@code GET /api/v1/validation-messages}. */
  public static final String GET_VALIDATION_MESSAGES_PATH = API_URI_PREFIX + "/validation-messages";
}
