package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Origin of a single field change recorded in an amendment {@link AmendmentDiff}.
 *
 * <ul>
 *   <li>{@link #REQUESTED} - a provider-requested change identified by the changed-field classifier
 *       (DSTEW-1766);
 *   <li>{@link #FSP} - a downstream consequence sourced from the Fee Scheme Platform handoff
 *       (DSTEW-1762).
 * </ul>
 *
 * <p>The serialised JSON value is the stable, upper-case label ({@code "REQUESTED"} / {@code
 * "FSP"}) used in the {@code diff} JSONB column.
 */
public enum ChangeSource {
  REQUESTED("REQUESTED"),
  FSP("FSP");

  private final String jsonValue;

  ChangeSource(String jsonValue) {
    this.jsonValue = jsonValue;
  }

  /**
   * The stable JSON label persisted in the diff.
   *
   * @return the JSON value
   */
  @JsonValue
  public String getJsonValue() {
    return jsonValue;
  }
}
