package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;

/**
 * Applies the amended provider-entered {@code claim_case}-table values from the post-amendment
 * state onto the managed {@link ClaimCase} entity.
 *
 * <p>Every amendable claim-case column is copied from the post-amendment snapshot, which already
 * encodes the sparse-payload semantics (omitted fields retain their stored value; an explicit
 * {@code null} clears the field). Identity and audit columns are deliberately left untouched, and
 * this component never issues its own save.
 */
@Component
public class AmendmentClaimCaseUpdater {

  /**
   * Copies the amendable {@code claim_case}-table fields from the post-amendment state onto the
   * managed claim-case entity.
   *
   * @param claimCase the managed claim-case entity to mutate (not saved here)
   * @param postAmendmentState the proposed post-amendment values
   */
  public void applyAmendedFields(ClaimCase claimCase, ClaimStateSnapshot postAmendmentState) {
    claimCase.setCaseId(postAmendmentState.getCaseId());
    claimCase.setUniqueCaseId(postAmendmentState.getUniqueCaseId());
    claimCase.setCaseStageCode(postAmendmentState.getCaseStageCode());
    claimCase.setStageReachedCode(postAmendmentState.getStageReachedCode());
    claimCase.setStandardFeeCategoryCode(postAmendmentState.getStandardFeeCategoryCode());
    claimCase.setOutcomeCode(postAmendmentState.getOutcomeCode());
    claimCase.setDesignatedAccreditedRepresentativeCode(
        postAmendmentState.getDesignatedAccreditedRepresentativeCode());
    claimCase.setIsPostalApplicationAccepted(postAmendmentState.getIsPostalApplicationAccepted());
    claimCase.setIsClient2PostalApplicationAccepted(
        postAmendmentState.getIsClient2PostalApplicationAccepted());
    claimCase.setMentalHealthTribunalReference(
        postAmendmentState.getMentalHealthTribunalReference());
    claimCase.setIsNrmAdvice(postAmendmentState.getIsNrmAdvice());
    claimCase.setFollowOnWork(postAmendmentState.getFollowOnWork());
    claimCase.setTransferDate(postAmendmentState.getTransferDate());
    claimCase.setExemptionCriteriaSatisfied(postAmendmentState.getExemptionCriteriaSatisfied());
    claimCase.setExceptionalCaseFundingReference(
        postAmendmentState.getExceptionalCaseFundingReference());
    claimCase.setIsLegacyCase(postAmendmentState.getIsLegacyCase());
  }
}
