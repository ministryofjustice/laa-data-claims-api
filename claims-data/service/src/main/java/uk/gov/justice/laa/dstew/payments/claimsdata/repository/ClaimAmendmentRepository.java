package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;

/**
 * Repository interface for managing {@link ClaimAmendment} entities.
 *
 * <p>Handles persistence operations for the core audit trail of successful claim updates, including
 * snapshots and field-level diffs.
 */
@Repository
public interface ClaimAmendmentRepository extends JpaRepository<ClaimAmendment, UUID> {

  /**
   * Fetches the historical amendment audit trail for a single claim.
   *
   * <p>Because the primary keys are generated as UUIDv7, sorting by {@code id DESC} naturally
   * guarantees strict chronological order without needing an external timestamp column lookup.
   *
   * @param claimId the unique identifier of the target claim
   * @return a chronologically descending {@link List} of amendments for the claim
   */
  List<ClaimAmendment> findByClaimIdOrderByIdDesc(UUID claimId);
}
