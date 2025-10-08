package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;

/** Repository for handling CRUD operations on claim summary fee records. */
@Repository
public interface ClaimSummaryFeeRepository extends JpaRepository<ClaimSummaryFee, UUID> {
  Optional<ClaimSummaryFee> findByClaimId(UUID claimId);

  Optional<ClaimSummaryFee> findByClaim(Claim claim);
}
