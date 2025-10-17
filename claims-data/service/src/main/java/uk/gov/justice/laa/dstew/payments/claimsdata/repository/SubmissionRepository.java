package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;

/** Repository for managing Submission entities. */
@Repository
public interface SubmissionRepository
    extends JpaRepository<Submission, UUID>, JpaSpecificationExecutor<Submission> {

  @Query(
      "SELECT SUM(cfd.totalAmount)"
          + "FROM Claim c "
          + "JOIN CalculatedFeeDetail cfd ON cfd.claim = c "
          + "WHERE c.submission.id = :submissionId ")
  BigDecimal getCalculatedTotalAmount(@Param("submissionId") UUID submissionId);
}
