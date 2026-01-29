package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.projection.ClaimProjection;

/** Repository for managing claim entities linked to submissions. */
@Repository
public interface ClaimRepository
    extends JpaRepository<Claim, UUID>, JpaSpecificationExecutor<Claim> {
  List<Claim> findBySubmissionId(UUID submissionId);

  Optional<Claim> findByIdAndSubmissionId(UUID id, UUID submissionId);

  @EntityGraph(attributePaths = "submission")
  @Query(
      """
        SELECT c
        FROM Claim c
        JOIN c.submission s
        WHERE s.officeAccountNumber = :officeCode
          AND (:submissionId IS NULL OR s.id = :submissionId)
          AND (:submissionStatuses IS NULL OR s.status IN :submissionStatuses)
          AND (:submissionPeriod IS NULL OR s.submissionPeriod = :submissionPeriod)
          AND (:claimStatuses IS NULL OR c.status IN :claimStatuses)
          AND (:feeCode IS NULL OR c.feeCode = :feeCode)
          AND (:uniqueFileNumber IS NULL OR c.uniqueFileNumber = :uniqueFileNumber)
          AND (:caseReferenceNumber IS NULL OR c.caseReferenceNumber = :caseReferenceNumber)
          AND (
                :uniqueClientNumber IS NULL
                OR EXISTS (
                    SELECT 1
                    FROM Client cl
                    WHERE cl.claim = c
                      AND cl.uniqueClientNumber = :uniqueClientNumber
                )
          )
          AND (
                :uniqueCaseId IS NULL
                OR EXISTS (
                    SELECT 1
                    FROM ClaimCase cc
                    WHERE cc.claim = c
                      AND cc.uniqueCaseId = :uniqueCaseId
                )
          )
        """)
  Page<ClaimProjection> findClaimsWithSubmission(
      @Param("officeCode") String officeCode,
      @Param("submissionId") UUID submissionId,
      @Param("submissionStatuses") List<SubmissionStatus> submissionStatuses,
      @Param("feeCode") String feeCode,
      @Param("uniqueFileNumber") String uniqueFileNumber,
      @Param("uniqueClientNumber") String uniqueClientNumber,
      @Param("uniqueCaseId") String uniqueCaseId,
      @Param("claimStatuses") List<ClaimStatus> claimStatuses,
      @Param("submissionPeriod") String submissionPeriod,
      @Param("caseReferenceNumber") String caseReferenceNumber,
      Pageable pageable);

  @Modifying
  @Query("UPDATE Claim c SET c.status = :status WHERE c.submission.id = :submissionId")
  int updateStatusBySubmissionId(UUID submissionId, ClaimStatus status);

  @Modifying
  @Query("UPDATE Claim c SET c.hasAssessment = :hasAssessment WHERE c.id = :id")
  int updateAssessmentStatus(UUID id, boolean hasAssessment);
}
