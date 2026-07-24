package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimInterestedDepartment;

/** Persistence access for a claim's interested departments. */
public interface ClaimInterestedDepartmentRepository
    extends JpaRepository<ClaimInterestedDepartment, UUID> {
  List<ClaimInterestedDepartment> findByClaimIdOrderByDepartmentCode(UUID claimId);

  void deleteByClaimId(UUID claimId);
}
