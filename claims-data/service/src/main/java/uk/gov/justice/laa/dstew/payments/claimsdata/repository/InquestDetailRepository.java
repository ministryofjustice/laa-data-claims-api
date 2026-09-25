package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.InquestDetail;

/**
 * Repository for accessing {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.entity.InquestDetail} entities.
 */
@Repository
public interface InquestDetailRepository extends JpaRepository<InquestDetail, UUID> {

  Optional<InquestDetail> findByClaimId(UUID claimId);
}
