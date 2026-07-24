package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimInterestedPublicAuthority;

/** Persistence access for a claim's ordered interested public authorities. */
public interface ClaimInterestedPublicAuthorityRepository
    extends JpaRepository<ClaimInterestedPublicAuthority, UUID> {
  List<ClaimInterestedPublicAuthority> findByClaimIdOrderByDisplayOrder(UUID claimId);

  void deleteByClaimId(UUID claimId);
}
