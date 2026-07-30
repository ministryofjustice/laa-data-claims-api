package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;

/**
 * Applies the amended provider-entered values across all amendable tables onto the managed
 * entities: {@code claim} (plus {@code is_amended = true}), {@code client}, {@code claim_case} and
 * {@code claim_summary_fee}.
 *
 * <p>The related entities are loaded by claim id within the active amendment transaction and
 * mutated in place; nothing is saved here. The claim column writes (and the version increment) are
 * folded into the guarded claim update owned by DSTEW-1753, while the related-table writes
 * participate in the same atomic transaction (DSTEW-1771). A related row is only updated when it
 * exists.
 */
@Component
@RequiredArgsConstructor
public class AmendmentEntitiesWriter {

  private final AmendmentClaimUpdater claimUpdater;
  private final AmendmentClientUpdater clientUpdater;
  private final AmendmentClaimCaseUpdater claimCaseUpdater;
  private final AmendmentClaimSummaryFeeUpdater claimSummaryFeeUpdater;
  private final ClientRepository clientRepository;
  private final ClaimCaseRepository claimCaseRepository;
  private final ClaimSummaryFeeRepository claimSummaryFeeRepository;

  /**
   * Applies the post-amendment values onto the managed claim and its related entities, marks the
   * claim amended and stamps the amending user onto {@code updated_by_user_id}.
   *
   * <p>The {@code updated_on} timestamp ({@code @UpdateTimestamp}) and the optimistic-lock {@code
   * version} ({@code @Version}) are refreshed automatically by Hibernate when the guarded claim
   * update flushes, so only the acting user is set explicitly here (DSTEW-2051 Finding B).
   *
   * @param claim the managed claim being amended (mutated in place; not saved here)
   * @param postAmendmentState the proposed post-amendment values
   * @param amendedByUserId the id of the user submitting the amendment, stamped onto {@code
   *     updated_by_user_id}
   */
  public void applyAmendedValues(
      Claim claim, ClaimStateSnapshot postAmendmentState, String amendedByUserId) {
    claimUpdater.applyAmendedFields(claim, postAmendmentState);
    claim.setAmended(true);
    claim.setUpdatedByUserId(amendedByUserId);

    clientRepository
        .findByClaimId(claim.getId())
        .ifPresent(client -> clientUpdater.applyAmendedFields(client, postAmendmentState));
    claimCaseRepository
        .findByClaimId(claim.getId())
        .ifPresent(claimCase -> claimCaseUpdater.applyAmendedFields(claimCase, postAmendmentState));
    claimSummaryFeeRepository
        .findByClaimId(claim.getId())
        .ifPresent(
            summaryFee ->
                claimSummaryFeeUpdater.applyAmendedFields(summaryFee, postAmendmentState));
  }
}
