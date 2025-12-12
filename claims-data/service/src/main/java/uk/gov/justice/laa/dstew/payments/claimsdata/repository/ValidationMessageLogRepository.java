package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

/** Repository for persisting {@link ValidationMessageLog} entries. */
public interface ValidationMessageLogRepository extends JpaRepository<ValidationMessageLog, UUID> {

  @Query(
      "SELECT COUNT(DISTINCT v.claimId) "
          + "FROM ValidationMessageLog v "
          + "WHERE v.submissionId = :submissionId "
          + "AND (:type IS NULL OR v.type = :type)")
  long countDistinctClaimIdsBySubmissionIdAndType(
      @Param("submissionId") UUID submissionId, @Param("type") ValidationMessageType type);

  long countAllByClaimIdAndType(UUID claimId, ValidationMessageType type);
}
