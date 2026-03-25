package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;

/** Repository for ClaimAmendment entity. */
@Repository
public interface ClaimAmendmentRepository extends JpaRepository<ClaimAmendment, UUID> {
  /**
   * Finds all amendments for a given claim ID.
   *
   * @param claimId the claim ID
   * @return list of ClaimAmendment
   */
  java.util.List<ClaimAmendment> findByClaimId(UUID claimId);
}
