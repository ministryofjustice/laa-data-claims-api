package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support;

/**
 * Small failure-translation wrapper for BDD step methods.
 *
 * <p><b>Standing rule (2026-08-13, DSTEW-1813 onward):</b> every step-definition method wraps its
 * body in {@link #step(String, ThrowingRunnable)} (or its supplier overload). The {@code
 * contextDescription} is a plain-English sentence naming the verb + noun and any scenario-scoped
 * identifiers (claim id, tag id, expected value) that make a failure diagnosable at a glance
 * without opening the Java stack trace.
 * <p>Both overloads use functional interfaces that allow {@code throws Exception}, so step lambdas
 * calling e.g. {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport#getClaimHistory(java.util.UUID)}
 * (which declares {@link java.io.IOException}) don't force each step method to swallow-then-rethrow
 * the checked exception.
 *
 * <p>On failure the wrapper rethrows an {@link AssertionError} whose message is {@code [BDD step
 * failed] <contextDescription> — <cause summary>} and chains the original throwable via {@link
 * Throwable#initCause(Throwable)}, so the Cucumber HTML report and the JUnit XML both show the
 * friendly line first and the deep stack second. AssertJ assertions inside the body still use
 * {@code .as("…")} to describe the specific assertion; the wrapper prepends the outer step context
 * so both show side by side.
 * <p>Usage:
 *
 * <pre>{@code
 * @When("I request the claim history timeline")
 * public void iRequestTheClaimHistoryTimeline() {
 *   BddStepFailures.step(
 *       "Requesting claim history timeline for claim " + currentClaimId,
 *       () -> lastHistoryResponse = api.getClaimHistory(currentClaimId));
 * }
 * }</pre>
 *
 */
public final class BddStepFailures {

  private BddStepFailures() {
    // Utility class - no instances.
  }

  /** Runnable that returns nothing and may throw any checked/unchecked exception. */
  @FunctionalInterface
  public interface ThrowingRunnable {
    void run() throws Exception;
  }

  /** Supplier that returns a value and may throw any checked/unchecked exception. */
  @FunctionalInterface
  public interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  /**
   * Executes {@code body} and, on any failure, rethrows an {@link AssertionError} whose first line
   * describes {@code contextDescription}.
   *
   * @param contextDescription a plain-English sentence starting with the verb + noun of what the
   *     step is doing (e.g. "Verifying AMENDMENT metadata field 'requested_by_code' equals
   *     'PROVIDER' for claim {claimId}"). Include the scenario-scoped identifiers that make the
   *     failure diagnosable at a glance.
   * @param body the actual step work — assertions, HTTP calls, DB seeding.
   */
  public static void step(String contextDescription, ThrowingRunnable body) {
    try {
      body.run();
    } catch (Exception | AssertionError cause) {
      throw rewrap(contextDescription, cause);
    }
  }

  /** Supplier overload — same behaviour as {@link #step(String, ThrowingRunnable)}. */
  public static <T> T step(String contextDescription, ThrowingSupplier<T> body) {
    try {
      return body.get();
    } catch (Exception | AssertionError cause) {
      throw rewrap(contextDescription, cause);
    }
  }

  private static AssertionError rewrap(String contextDescription, Throwable cause) {
    String summary = summarise(cause);
    String prefix = "[BDD step failed] " + contextDescription;
    String message = (summary == null || summary.isBlank()) ? prefix : (prefix + " — " + summary);
    AssertionError rewrapped = new AssertionError(message);
    // Preserve original stack + cause so the test report drills down into the real failure point.
    rewrapped.initCause(cause);
    return rewrapped;
  }

  /**
   * Short human-readable summary of the failure. For AssertJ / AssertionError bodies (which already
   * carry a friendly {@code .as("…")} description) we surface the message verbatim so the report
   * shows both the outer step context and the specific assertion side by side. For other throwable
   * types we prefix the simple class name so the reader can tell at a glance whether the failure
   * came from an assertion, an I/O error, an HTTP status, etc.
   */
  private static String summarise(Throwable cause) {
    if (cause == null) {
      return null;
    }
    String raw = cause.getMessage();
    boolean blank = (raw == null || raw.isBlank());
    if (cause instanceof AssertionError) {
      return blank ? cause.getClass().getSimpleName() : raw;
    }
    // For non-assertion throwables with a real message, prefix the class name so the reader can
    // tell at a glance whether the failure came from an I/O error, an HTTP status, etc. When the
    // message is blank/null we would otherwise produce `NullPointerException: NullPointerException`
    // — return just the class name in that case.
    return blank ? cause.getClass().getSimpleName() : cause.getClass().getSimpleName() + ": " + raw;
  }
}
