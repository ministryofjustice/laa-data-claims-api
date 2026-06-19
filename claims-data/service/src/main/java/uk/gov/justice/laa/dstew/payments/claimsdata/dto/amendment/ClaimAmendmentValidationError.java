package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import lombok.Value;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Immutable description of a single claim amendment validation failure, returned by a validation
 * step.
 *
 * <p>This workflow only produces errors (there are no warnings). An error is either:
 *
 * <ul>
 *   <li><b>fatal</b> ({@code fatal == true}) - a show-stopper that halts the pipeline immediately;
 *       no later step runs, so this also guarantees no downstream PDA/FSP call is made; or
 *   <li><b>non-fatal</b> ({@code fatal == false}) - collected and carried on so that the remaining
 *       steps can surface their own failures; the user is then shown every collected error
 *       together.
 * </ul>
 *
 * <p>Any non-empty set of errors (fatal or not) fails the amendment and nothing is persisted.
 *
 * <p>Carries the stable {@link ClaimAmendmentErrorCode}, the offending {@link ClaimStatus} (the
 * current status, where available) and a human-readable message, so the parent flow can build the
 * shared structured validation/error response.
 */
@Value
public class ClaimAmendmentValidationError {

  ClaimAmendmentErrorCode code;
  ClaimStatus claimStatus;
  String message;
  boolean fatal;
}
