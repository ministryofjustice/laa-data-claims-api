package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import lombok.Value;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Immutable description of why a claim is not eligible for amendment, returned by the eligibility
 * gate on a rejection.
 *
 * <p>Carries the stable {@link ClaimAmendmentErrorCode}, the offending {@link ClaimStatus} (the
 * current status, where available) and a human-readable message, so the parent flow can build the
 * shared structured validation/error response.
 */
@Value
public class ClaimAmendmentEligibilityError {

  ClaimAmendmentErrorCode code;
  ClaimStatus claimStatus;
  String message;
}
