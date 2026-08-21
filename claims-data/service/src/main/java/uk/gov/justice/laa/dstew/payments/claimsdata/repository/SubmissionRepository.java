package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

/** Repository for managing Submission entities. */
@Repository
public interface SubmissionRepository
    extends JpaRepository<Submission, UUID>, JpaSpecificationExecutor<Submission> {

  /**
   * Returns whether a "live" submission already exists for the given office, area of law and
   * submission period. Used by the application-level duplicate guard in {@code
   * SubmissionService.createSubmission}.
   *
   * <p>A submission is "live" unless it has been superseded; the {@code statuses} argument is the
   * set of non-live statuses to exclude (i.e. {@code VALIDATION_FAILED} and {@code REPLACED}),
   * mirroring the partial DB index {@code uq_submission_live_office_aol_period} so a failed or
   * replaced submission does not block a fresh attempt for the same combination.
   *
   * @param officeAccountNumber the office account number
   * @param areaOfLaw the area of law
   * @param submissionPeriod the submission period
   * @param statuses the non-live statuses to exclude from the match
   * @return {@code true} if a live submission already exists for that combination
   */
  boolean existsByOfficeAccountNumberAndAreaOfLawAndSubmissionPeriodAndStatusNotIn(
      String officeAccountNumber,
      AreaOfLaw areaOfLaw,
      String submissionPeriod,
      Collection<SubmissionStatus> statuses);

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
