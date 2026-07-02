package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;

/**
 * Attaches the single amendment-driven {@code calculated_fee_detail} row prepared by the FSP
 * handoff (DSTEW-1762).
 *
 * <p>On a successful <b>pricing</b> amendment, the implementation links the FSP-prepared
 * calculated-fee row to this amendment (sets {@code claim_amendment_id} and persists {@code
 * is_price_changed}); on a non-pricing amendment it links none. The link is one-to-one and
 * non-mandatory, and the {@code UNIQUE (claim_amendment_id)} constraint enforces at most one row
 * per amendment.
 *
 * <p>This is currently a no-op placeholder: it attaches nothing until DSTEW-1762 supplies the FSP
 * handoff, at which point {@link #attach} is implemented in place.
 */
@Component
public class AmendmentCalculatedFeeWriter {

  /**
   * Attaches the amendment-driven calculated-fee row, if any, to the persisted amendment.
   *
   * @param amendment the persisted {@code claim_amendment} row (its id attributes the child row)
   * @param state the in-memory amendment state, carrying the FSP outcome to persist
   */
  public void attach(ClaimAmendment amendment, ClaimAmendmentState state) {
    // No calculated-fee row to attach until DSTEW-1762 supplies the FSP handoff.
  }
}
