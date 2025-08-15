package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;

/**
 * Repository for managing matter start entities.
 */
@Repository
public interface MatterStartRepository extends JpaRepository<MatterStart, UUID> {
  List<MatterStart> findBySubmissionId(UUID submissionId);
}

