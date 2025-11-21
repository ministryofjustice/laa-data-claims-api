package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;

/** Repository for accessing {@link BulkSubmission} entities. */
@Repository
public interface BulkSubmissionRepository extends JpaRepository<BulkSubmission, UUID> {

  @Modifying
  @Query(
      "UPDATE BulkSubmission b SET b.status = :status, b.errorCode = :errorCode, "
          + "b.errorDescription = :errorDescription, b.updatedByUserId = :updatedByUserId "
          + "WHERE b.id = :id")
  int updateBulkSubmission(
      UUID id,
      BulkSubmissionStatus status,
      BulkSubmissionErrorCode errorCode,
      String errorDescription,
      String updatedByUserId);
}
