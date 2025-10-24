package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/** Repository for managing claim entities linked to submissions. */
@Repository
public interface ClaimRepository
    extends JpaRepository<Claim, UUID>, JpaSpecificationExecutor<Claim> {
  List<Claim> findBySubmissionId(UUID submissionId);

  Optional<Claim> findByIdAndSubmissionId(UUID id, UUID submissionId);

  @Modifying
  @Query("UPDATE Claim c SET c.status = :status WHERE c.submission.id = :submissionId")
  int updateStatusBySubmissionId(UUID submissionId, ClaimStatus status);
}
