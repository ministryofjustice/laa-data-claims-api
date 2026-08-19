package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;

/**
 * Applies the amended provider-entered {@code claim}-table values from the post-amendment state
 * onto the managed {@link Claim} entity.
 *
 * <p>Every amendable claim column is copied from the post-amendment snapshot, which already encodes
 * the sparse-payload semantics (omitted fields retain their stored value; an explicit {@code null}
 * clears the field). Copying the full set therefore both applies requested changes and preserves
 * untouched values in one pass.
 *
 * <p>This deliberately does <b>not</b> touch identity, audit, lifecycle ({@code status}), {@code
 * is_amended}, {@code has_assessment} or the optimistic-lock {@code version} columns: setting
 * {@code is_amended} and the version increment are owned by the guarded claim update (DSTEW-1753),
 * and this component never issues its own save. Client, claim-case and claim-summary-fee values are
 * out of scope here (the guarded update in DSTEW-1753 targets the {@code claim} table).
 */
@Component
public class AmendmentClaimUpdater {

  /**
   * Copies the amendable {@code claim}-table fields from the post-amendment state onto the managed
   * claim entity.
   *
   * @param claim the managed claim entity to mutate (not saved here)
   * @param postAmendmentState the proposed post-amendment values
   */
  public void applyAmendedFields(Claim claim, ClaimStateSnapshot postAmendmentState) {
    claim.setScheduleReference(postAmendmentState.getScheduleReference());
    claim.setLineNumber(postAmendmentState.getLineNumber());
    claim.setCaseReferenceNumber(postAmendmentState.getCaseReferenceNumber());
    claim.setUniqueFileNumber(postAmendmentState.getUniqueFileNumber());
    claim.setCaseStartDate(postAmendmentState.getCaseStartDate());
    claim.setCaseConcludedDate(postAmendmentState.getCaseConcludedDate());
    claim.setMatterTypeCode(postAmendmentState.getMatterTypeCode());
    claim.setCrimeMatterTypeCode(postAmendmentState.getCrimeMatterTypeCode());
    claim.setFeeSchemeCode(postAmendmentState.getFeeSchemeCode());
    claim.setFeeCode(postAmendmentState.getFeeCode());
    claim.setProcurementAreaCode(postAmendmentState.getProcurementAreaCode());
    claim.setAccessPointCode(postAmendmentState.getAccessPointCode());
    claim.setDeliveryLocation(postAmendmentState.getDeliveryLocation());
    claim.setRepresentationOrderDate(postAmendmentState.getRepresentationOrderDate());
    claim.setSuspectsDefendantsCount(postAmendmentState.getSuspectsDefendantsCount());
    claim.setPoliceStationCourtAttendancesCount(
        postAmendmentState.getPoliceStationCourtAttendancesCount());
    claim.setPoliceStationCourtPrisonId(postAmendmentState.getPoliceStationCourtPrisonId());
    claim.setDsccNumber(postAmendmentState.getDsccNumber());
    claim.setMaatId(postAmendmentState.getMaatId());
    claim.setPrisonLawPriorApprovalNumber(postAmendmentState.getPrisonLawPriorApprovalNumber());
    claim.setDutySolicitor(postAmendmentState.getDutySolicitor());
    claim.setYouthCourt(postAmendmentState.getYouthCourt());
    claim.setSchemeId(postAmendmentState.getSchemeId());
    claim.setMediationSessionsCount(postAmendmentState.getMediationSessionsCount());
    claim.setMediationTimeMinutes(postAmendmentState.getMediationTimeMinutes());
    claim.setOutreachLocation(postAmendmentState.getOutreachLocation());
    claim.setReferralSource(postAmendmentState.getReferralSource());
  }
}
