package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.math.BigDecimal;
import java.util.List;
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

  /**
   * Projection for Calculated total amounts grouped by submission.
   *
   * <p>Used by {@link #getCalculatedTotalAmounts(List)} to return the Calculated total amount for
   * each submission without loading full entity data.
   */
  interface CalculatedTotalAmountProjection {

    /**
     * Returns the identifier of the submission.
     *
     * @return the submission ID
     */
    UUID getSubmissionId();

    /**
     * Returns the Calculated total amount for the submission.
     *
     * @return the summed Calculated total amount
     */
    BigDecimal getTotal();
  }

  @Query(
      """
        SELECT SUM(cfd.totalAmount)
        FROM Claim c
        JOIN CalculatedFeeDetail cfd ON cfd.claim = c
        WHERE c.submission.id = :submissionId
      """)
  BigDecimal getCalculatedTotalAmount(@Param("submissionId") UUID submissionId);

  /**
   * Returns calculated total amounts for the given submissions.
   *
   * <p>For each submission ID provided, this query returns the sum of {@code
   * calculatedTotalInclVat} from the cfd record for each claim belonging to that submission.
   * Results are grouped by submission ID.
   *
   * <p>Submissions with no cfd records are not included in the returned list.
   *
   * @param submissionIds the unique identifiers of the submissions
   * @return a list of projections containing submission IDs and their calculated total amounts
   */
  @Query(
      """
       SELECT c.submission.id AS submissionId, SUM(cfd.totalAmount) AS total
       FROM Claim c
       JOIN CalculatedFeeDetail cfd ON cfd.claim = c
       WHERE c.submission.id IN :submissionIds
       GROUP BY c.submission.id
      """)
  List<CalculatedTotalAmountProjection> getCalculatedTotalAmounts(
      @Param("submissionIds") List<UUID> submissionIds);
}
