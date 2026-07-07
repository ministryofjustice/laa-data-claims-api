package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Versioned, self-describing diff persisted as the {@code diff} JSONB column of a {@code
 * claim_amendment} row.
 *
 * <p>Holds only the changed fields (one {@link DiffEntry} each), tagged by {@link ChangeSource} so
 * a reader can render the amendment event and field-level detail without re-deriving the change
 * set.
 *
 * @param schemaVersion the diff schema version (currently {@link #CURRENT_SCHEMA_VERSION})
 * @param changes the changed fields, changed fields only
 */
public record AmendmentDiff(
    @JsonProperty("schema_version") int schemaVersion,
    @JsonProperty("changes") List<DiffEntry> changes) {

  /** The current diff schema version. */
  public static final int CURRENT_SCHEMA_VERSION = 1;

  /**
   * Builds a diff at the current schema version from the supplied changed-field entries.
   *
   * @param changes the changed fields (changed fields only)
   * @return an {@link AmendmentDiff} at {@link #CURRENT_SCHEMA_VERSION}
   */
  public static AmendmentDiff of(List<DiffEntry> changes) {
    return new AmendmentDiff(CURRENT_SCHEMA_VERSION, List.copyOf(changes));
  }
}
