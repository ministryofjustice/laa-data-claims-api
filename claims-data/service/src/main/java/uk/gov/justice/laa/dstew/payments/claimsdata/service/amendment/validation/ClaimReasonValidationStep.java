package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;

public class ClaimReasonValidationStep {
  public boolean validate(ClaimAmendmentState claimAmendmentState){
    ClaimAmendmentPayload claimRequestPayload = claimAmendmentState.getRequestPayload();

    if(claimRequestPayload.getClaimReason() == null || claimRequestPayload.getClaimReason().isEmpty()){
      claimAmendmentState.addValidationIssue(ClaimReasonValidationError.CLAIM_REASON_MISSING.toValidationIssue());
      return false;
    }

    return true;
  }
}
