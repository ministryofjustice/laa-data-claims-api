package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.UUID;
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

  List<ClaimInterestedDepartment> findByClaimId(UUID claimId);
}
