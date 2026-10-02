package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/** Repository for managing claim entities linked to submissions. */
@Repository
public interface ClaimRepository
    extends JpaRepository<Claim, UUID>, JpaSpecificationExecutor<Claim> {
  List<Claim> findBySubmissionId(UUID submissionId);

  Optional<Claim> findByIdAndSubmissionId(UUID id, UUID submissionId);

  /**
   * Returns whether a claim already exists for the given submission and line number. Used by the
   * application-level duplicate guard in {@code ClaimService.createClaim}. This checks all rows
   * (including any historical duplicates grandfathered by the partial DB index), so it also covers
   * the old-vs-new case the partial index cannot.
   *
   * @param submissionId the owning submission id
   * @param lineNumber the claim line number
   * @return {@code true} if a claim already exists for that submission and line number
   */
  boolean existsBySubmissionIdAndLineNumber(UUID submissionId, Integer lineNumber);

  @Modifying
  @Query("UPDATE Claim c SET c.status = :status WHERE c.submission.id = :submissionId")
  int updateStatusBySubmissionId(UUID submissionId, ClaimStatus status);

  /**
   * Retrieves paginated VALID claims for a given office code and unique file number (UFN). Returns
   * claims sorted by most recent update first.
   *
   * <p>Used for retention lookup with pagination support for data governance compliance. Only VALID
   * status claims are returned; other statuses (INVALID, VOID, etc.) are excluded.
   *
   * @param officeAccountNumber the office account number from the submission
   * @param uniqueFileNumber the unique file number to search for
   * @param pageable pagination parameters (page index and size)
   * @return page of VALID claims matching the composite key, ordered by updatedOn DESC
   */
  @Query(
      "SELECT c FROM Claim c "
          + "WHERE c.submission.officeAccountNumber = :officeAccountNumber "
          + "AND c.uniqueFileNumber = :uniqueFileNumber "
          + "AND c.status = 'VALID' "
          + "ORDER BY c.updatedOn DESC, c.id ASC")
  Page<Claim> findValidByOfficeAccountNumberAndUniqueFileNumber(
      String officeAccountNumber, String uniqueFileNumber, Pageable pageable);
}
