package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.InquestDetail;

/** Persistence access for optional claim inquest details. */
public interface InquestDetailRepository extends JpaRepository<InquestDetail, UUID> {
  Optional<InquestDetail> findByClaimId(UUID claimId);
}
