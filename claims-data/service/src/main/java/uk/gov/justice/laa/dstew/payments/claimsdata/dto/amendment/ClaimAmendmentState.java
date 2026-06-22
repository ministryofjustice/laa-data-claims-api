package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import lombok.Builder;
import lombok.Data;

/**
 * In-memory aggregate describing a claim amendment in progress, passed from the retrieval/build
 * step to downstream validation, history and persistence tasks.
 *
 * <p>It bundles the three pieces the amendment flow needs:
 *
 * <ul>
 *   <li>{@link #beforeState} - the current stored values (basis for the {@code beforeState} JSONB);
 *   <li>{@link #requestPayload} - the sparse, presence-aware submission (basis for the {@code
 *       requestPayload} JSONB);
 *   <li>{@link #postAmendmentState} - the proposed amended values, built by applying the sparse
 *       payload onto the before-state (omitted fields retain stored values; explicit nulls are
 *       retained as requested clears).
 * </ul>
 *
 * <p>The before/after snapshots plus the payload's field presence are sufficient to compute the
 * {@code diff} JSONB.
 */
@Data
@Builder
public class ClaimAmendmentState {

  private ClaimStateSnapshot beforeState;

  private ClaimAmendmentPayload requestPayload;

  private ClaimStateSnapshot postAmendmentState;
}
