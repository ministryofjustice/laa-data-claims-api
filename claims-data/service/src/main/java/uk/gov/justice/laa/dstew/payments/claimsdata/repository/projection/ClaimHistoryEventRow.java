package uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-model projection representing a single entry on a claim's history timeline.
 *
 * <p>This is a unified shape emitted by every source table (claim, claim_amendment, assessment) so
 * that heterogeneous business events can be ordered and paged as one chronological stream. It is a
 * pure query DTO: it is never persisted and is intentionally decoupled from the JPA entity graph to
 * avoid lazy loading and N+1 access when reading history.
 *
 * @param eventType the discriminator for the source event, e.g. {@code SUBMISSION}, {@code
 *     AMENDMENT}, {@code ASSESSMENT}
 * @param eventTimestamp the business time the event occurred; the sole driver of timeline ordering
 * @param actorId the user who caused the event; never {@code null} — {@code "SYSTEM"} when the
 *     source record has no user id
 * @param sourceId the primary key of the originating row (UUIDv7), used as the deterministic
 *     ordering tie-breaker and future keyset cursor
 * @param metadata event-specific attributes rendered as JSON by PostgreSQL {@code
 *     jsonb_build_object}
 */
public record ClaimHistoryEventRow(
    String eventType, Instant eventTimestamp, String actorId, UUID sourceId, JsonNode metadata) {}
