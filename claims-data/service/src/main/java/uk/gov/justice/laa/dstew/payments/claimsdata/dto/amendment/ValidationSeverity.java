package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

/**
 * Severity of a claim amendment validation finding.
 *
 * <ul>
 *   <li>{@link #WARNING} - non-fatal; advisory only (precise semantics to be defined later).
 *   <li>{@link #ERROR} - non-fatal; a failure that is collected so the remaining steps can still
 *       surface their own findings (precise semantics to be defined later).
 *   <li>{@link #FATAL} - a show-stopper that halts the amendment flow immediately, so no later step
 *       runs and nothing is saved.
 * </ul>
 *
 * <p>Only {@link #FATAL} stops the flow; {@code WARNING} and {@code ERROR} are non-fatal.
 */
public enum ValidationSeverity {
  WARNING,
  ERROR,
  FATAL;

  /**
   * Whether this severity halts the flow immediately.
   *
   * @return {@code true} only for {@link #FATAL}
   */
  public boolean isFatal() {
    return this == FATAL;
  }
}
