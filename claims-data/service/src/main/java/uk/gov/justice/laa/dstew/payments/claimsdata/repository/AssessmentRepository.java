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

  /**
   * Projection for assessed total amounts grouped by submission.
   *
   * <p>Used by {@link #getAssessedTotalAmounts(List)} to return the assessed total amount for each
   * submission without loading full entity data.
   */
  interface AssessedTotalAmountProjection {

    /**
     * Returns the identifier of the submission.
     *
     * @return the submission ID
     */
    UUID getSubmissionId();

    /**
     * Returns the assessed total amount for the submission.
     *
     * @return the summed assessed total amount
     */
    BigDecimal getTotal();
  }

  /**
   * Finds an assessment by its identifier and associated claim identifier.
   *
   * @param assessmentId the unique identifier of the assessment
   * @param claimId the unique identifier of the claim
   * @return an {@link Optional} containing the matching assessment, or empty if none found
   */
  Optional<Assessment> findByIdAndClaimId(UUID assessmentId, UUID claimId);

  /**
   * Returns a paginated list of assessments for the given claim.
   *
   * @param claimId the unique identifier of the claim
   * @param pageable pagination information
   * @return a page of assessments associated with the claim
   */
  Page<Assessment> findByClaimId(UUID claimId, Pageable pageable);

  /**
   * Returns the assessed total amount for the given submission.
   *
   * <p>This is calculated as the sum of {@code assessedTotalInclVat} from the latest assessment for
   * each claim belonging to the submission. If no assessments exist for any claim in the
   * submission, this method returns {@code null}.
   *
   * @param submissionId the unique identifier of the submission
   * @return the summed assessed total amount for the submission, or {@code null} if no assessments
   *     exist
   */
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

  /**
   * Returns assessed total amounts for the given submissions.
   *
   * <p>For each submission ID provided, this query returns the sum of {@code assessedTotalInclVat}
   * from the latest assessment for each claim belonging to that submission. Results are grouped by
   * submission ID.
   *
   * <p>Submissions with no assessments are not included in the returned list.
   *
   * @param submissionIds the unique identifiers of the submissions
   * @return a list of projections containing submission IDs and their assessed total amounts
   */
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
