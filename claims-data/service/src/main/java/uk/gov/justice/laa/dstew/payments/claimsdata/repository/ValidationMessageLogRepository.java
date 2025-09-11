package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;

/** Repository for persisting {@link ValidationMessageLog} entries. */
public interface ValidationMessageLogRepository extends JpaRepository<ValidationMessageLog, UUID> {

  @Query(
      "SELECT COUNT(DISTINCT v.claimId) FROM ValidationMessageLog v WHERE v.submissionId = :submissionId")
  long countDistinctClaimIdsBySubmissionId(@Param("submissionId") UUID submissionId);
}
