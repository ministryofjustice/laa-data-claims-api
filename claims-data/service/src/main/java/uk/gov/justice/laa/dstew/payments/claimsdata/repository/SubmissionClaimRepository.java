package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.SubmissionClaim;

/**
 * Repository for managing claim entities linked to submissions.
 */
@Repository
public interface SubmissionClaimRepository extends JpaRepository<SubmissionClaim, UUID> {
  List<SubmissionClaim> findBySubmissionId(UUID submissionId);

  Optional<SubmissionClaim> findByIdAndSubmissionId(UUID id, UUID submissionId);
}

