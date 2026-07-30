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
   * each submission without loading full entity data. Only uses latest CFD per claim.
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
     * <p>This aggregation guarantees the following behavior:
     *
     * <ul>
     *   <li>DSTEW-1538 - Must use scaleNullable
     *   <li>Returns {@code null} if there are no CFD rows associated with the submission.
     *   <li>Returns {@code 0} (Zero) if CFD rows exist but their combined sum is exactly zero.
     *   <li>Returns the exact summed total of the latest CFD rows for all other scenarios.
     * </ul>
     *
     * @return the summed Calculated total amount
     */
    BigDecimal getTotal();
  }

  @Query(
      value =
          """
          SELECT SUM(latest_fees.total_amount)
          FROM (
            SELECT cfd.total_amount,
                   ROW_NUMBER() OVER (PARTITION BY cfd.claim_id ORDER BY cfd.created_on DESC, cfd.id DESC) as rn
            FROM claims.calculated_fee_detail cfd
            INNER JOIN claims.claim c ON c.id = cfd.claim_id
            WHERE c.submission_id = :submissionId
          ) latest_fees
          WHERE latest_fees.rn = 1
          """,
      nativeQuery = true)
  BigDecimal getCalculatedTotalAmount(@Param("submissionId") UUID submissionId);

  /**
   * Returns calculated total amounts for the given submissions.
   *
   * <p>For each submission ID provided, this query returns the sum of {@code totalAmount} from the
   * latest cfd record for each claim belonging to that submission. Results are grouped by
   * submission ID.
   *
   * <p>Submissions with no cfd records are not included in the returned list.
   *
   * @param submissionIds the unique identifiers of the submissions
   * @return a list of projections containing submission IDs and their calculated total amounts
   */
  @Query(
      value =
          """
          SELECT latest_fees.submission_id AS submissionId,
                 SUM(latest_fees.total_amount) AS total
          FROM (
            SELECT c.submission_id,
                   cfd.total_amount,
                   ROW_NUMBER() OVER (PARTITION BY cfd.claim_id ORDER BY cfd.created_on DESC, cfd.id DESC) as rn
            FROM claims.calculated_fee_detail cfd
            INNER JOIN claims.claim c ON c.id = cfd.claim_id
            WHERE c.submission_id IN (:submissionIds)
          ) latest_fees
          WHERE latest_fees.rn = 1
          GROUP BY latest_fees.submission_id
          """,
      nativeQuery = true)
  List<CalculatedTotalAmountProjection> getCalculatedTotalAmounts(
      @Param("submissionIds") List<UUID> submissionIds);
}
