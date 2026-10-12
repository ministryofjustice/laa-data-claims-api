package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimInterestedDepartment;

/**
 * Repository for accessing {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimInterestedDepartment} entities.
 */
@Repository
public interface ClaimInterestedDepartmentRepository
    extends JpaRepository<ClaimInterestedDepartment, UUID> {

  List<ClaimInterestedDepartment> findByClaimIdOrderByDisplayOrderAsc(UUID claimId);

  @EntityGraph(attributePaths = "governmentDepartment")
  List<ClaimInterestedDepartment> findByClaimIdInOrderByClaimIdAscDisplayOrderAsc(
      Collection<UUID> claimIds);
}
