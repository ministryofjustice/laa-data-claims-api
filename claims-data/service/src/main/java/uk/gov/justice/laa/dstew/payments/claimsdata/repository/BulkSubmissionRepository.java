package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;

/** Repository for accessing {@link BulkSubmission} entities. */
@Repository
public interface BulkSubmissionRepository extends JpaRepository<BulkSubmission, UUID> {

  @Modifying
  @Query(
      "UPDATE BulkSubmission b SET b.status = COALESCE(:status, b.status), "
          + "b.errorCode = COALESCE(:errorCode, b.errorCode), "
          + "b.errorDescription = COALESCE(:errorDescription, b.errorDescription), "
          + "b.updatedByUserId = COALESCE(:updatedByUserId, b.updatedByUserId) "
          + "WHERE b.id = :id")
  int updateBulkSubmission(
      UUID id, String status, String errorCode, String errorDescription, String updatedByUserId);

  @Query("SELECT b.status FROM BulkSubmission b WHERE b.id = :id")
  Optional<BulkSubmissionStatus> findStatusById(UUID id);
}
