package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import java.util.List;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;

/**
 * Outcome of a claim amendment submission.
 *
 * <p>Exactly one of the two fields is populated:
 *
 * <ul>
 *   <li>on success, {@link #amendment} holds the persisted {@code claim_amendment} row and {@link
 *       #errors} is empty;
 *   <li>on validation rejection, {@link #errors} holds the collected failures and {@link
 *       #amendment} is {@code null} (nothing was persisted).
 * </ul>
 *
 * <p>Surfacing rejection as data (rather than an exception) keeps the orchestrator decoupled from
 * the HTTP failure-mapping owned by the request-boundary tickets (DSTEW-1752/1754); the caller
 * translates {@link #errors} into the structured failure response.
 */
public record ClaimAmendmentResult(
    ClaimAmendment amendment, List<ClaimAmendmentValidationError> errors) {

  /**
   * A successful submission.
   *
   * @param amendment the persisted amendment record
   * @return a success result
   */
  public static ClaimAmendmentResult success(ClaimAmendment amendment) {
    return new ClaimAmendmentResult(amendment, List.of());
  }

  /**
   * A rejected submission carrying the collected validation errors.
   *
   * @param errors the validation errors (non-empty)
   * @return a rejection result
   */
  public static ClaimAmendmentResult rejected(List<ClaimAmendmentValidationError> errors) {
    return new ClaimAmendmentResult(null, List.copyOf(errors));
  }

  /**
   * Whether the amendment was persisted.
   *
   * @return {@code true} if successful
   */
  public boolean isSuccess() {
    return amendment != null;
  }
}
