package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;

/** Repository for accessing {@link Assessment} entities. */
@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
  Optional<Assessment> findByIdAndClaimId(UUID assessmentId, UUID claimId);
}
