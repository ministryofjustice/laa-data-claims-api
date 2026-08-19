package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;

/**
 * Result of the prepare phase of an amendment submission: the (now detached) {@link Claim} entity
 * read for the amendment and the built {@link ClaimAmendmentState}.
 *
 * <p>The {@link #claim} is carried through validation into the commit phase so the write can
 * reattach <em>this</em> instance (anchoring the {@code @Version} optimistic-lock guard to the
 * version read at prepare time) rather than re-reading the row.
 *
 * @param claim the claim as read in the prepare phase (detached once that read transaction closes)
 * @param state the in-memory amendment state (before/post snapshots and the request payload)
 */
public record PreparedAmendment(Claim claim, ClaimAmendmentState state) {}
