package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationErrorLog;

/** Repository for persisting {@link ValidationErrorLog} entries. */
public interface ValidationErrorLogRepository extends JpaRepository<ValidationErrorLog, UUID> {

  @Query(
      "SELECT COUNT(DISTINCT v.claimId) FROM ValidationErrorLog v WHERE v.submissionId = :submissionId")
  long countDistinctClaimIdsBySubmissionId(@Param("submissionId") UUID submissionId);
}
