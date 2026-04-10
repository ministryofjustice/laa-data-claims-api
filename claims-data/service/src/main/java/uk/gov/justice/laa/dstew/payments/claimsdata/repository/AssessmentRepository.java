package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;

/** Repository for accessing {@link Assessment} entities. */
@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
  interface AssessedTotalAmountProjection {
    UUID getSubmissionId();

    BigDecimal getTotal();
  }

  Optional<Assessment> findByIdAndClaimId(UUID assessmentId, UUID claimId);

  Page<Assessment> findByClaimId(UUID claimId, Pageable pageable);

  @Query(
      """
          SELECT SUM(a.assessedTotalInclVat)
          FROM Assessment a
          WHERE a.claim.submission.id = :submissionId
            AND a.createdOn = (
                SELECT MAX(a2.createdOn)
                FROM Assessment a2
                WHERE a2.claim = a.claim
            )
          """)
  BigDecimal getAssessedTotalAmount(@Param("submissionId") UUID submissionId);

  @Query(
      """
      SELECT a.claim.submission.id AS submissionId, SUM(a.assessedTotalInclVat) AS total
      FROM Assessment a
      WHERE a.claim.submission.id IN :submissionIds
        AND a.createdOn = (
            SELECT MAX(a2.createdOn)
            FROM Assessment a2
            WHERE a2.claim = a.claim
        )
      GROUP BY a.claim.submission.id
      """)
  List<AssessedTotalAmountProjection> getAssessedTotalAmounts(
      @Param("submissionIds") List<UUID> submissionIds);
}
