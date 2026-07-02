package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single changed field in an amendment {@link AmendmentDiff}.
 *
 * <p>The before/after values are already fully resolved (see {@link ClaimStateSnapshot}): the
 * amendment payload's absent/present/null tri-state is settled upstream by the {@code JsonNullable}
 * payload (omitted fields are left unchanged and never reach the diff). A {@code null} here is
 * therefore an explicit value (e.g. a cleared field), not "value not available".
 *
 * @param fieldIdentifier the stable domain identifier of the field (not a display label)
 * @param changeSource whether the change was provider-requested or an FSP consequence
 * @param before the value before the amendment (in its natural JSON type; {@code null} means
 *     cleared)
 * @param after the value after the amendment (in its natural JSON type; {@code null} means cleared)
 */
public record DiffEntry(
    @JsonProperty("field_identifier") String fieldIdentifier,
    @JsonProperty("change_source") ChangeSource changeSource,
    @JsonProperty("before") Object before,
    @JsonProperty("after") Object after) {}
