package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimWarningCountProjection;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ValidationMessageWithClaimDetailsProjection;

/** Repository for persisting {@link ValidationMessageLog} entries. */
public interface ValidationMessageLogRepository extends JpaRepository<ValidationMessageLog, UUID> {

  long CURRENT_SUPERSEDED_BY_VERSION = 0L;

  /**
   * Count current validation-message claim IDs for a submission, defaulting warning reads to the
   * current sentinel value.
   */
  default long countDistinctClaimIdsBySubmissionIdAndType(
      UUID submissionId, ValidationMessageType type) {
    return countDistinctClaimIdsBySubmissionIdAndType(
        submissionId, type, ValidationMessageType.WARNING, CURRENT_SUPERSEDED_BY_VERSION);
  }

  @Query(
      """
        SELECT COUNT(DISTINCT v.claimId)
        FROM ValidationMessageLog v
        WHERE v.submissionId = :submissionId
        AND (:type IS NULL OR v.type = :type)
        AND (
          :type IS NULL
          OR :type <> :warningType
          OR v.supersededByVersion = :currentSupersededByVersion
        )
        """)
  long countDistinctClaimIdsBySubmissionIdAndType(
      @Param("submissionId") UUID submissionId,
      @Param("type") ValidationMessageType type,
      @Param("warningType") ValidationMessageType warningType,
      @Param("currentSupersededByVersion") Long currentSupersededByVersion);

  /**
   * Count current validation messages for a claim, defaulting warning reads to the current sentinel
   * value.
   */
  default long countAllByClaimIdAndType(UUID claimId, ValidationMessageType type) {
    return countAllByClaimIdAndType(
        claimId, type, ValidationMessageType.WARNING, CURRENT_SUPERSEDED_BY_VERSION);
  }

  @Query(
      """
      SELECT COUNT(v)
      FROM ValidationMessageLog v
      WHERE v.claimId = :claimId
        AND v.type = :type
        AND (
          :type IS NULL
          OR :type <> :warningType
          OR v.supersededByVersion = :currentSupersededByVersion
        )
      """)
  long countAllByClaimIdAndType(
      @Param("claimId") UUID claimId,
      @Param("type") ValidationMessageType type,
      @Param("warningType") ValidationMessageType warningType,
      @Param("currentSupersededByVersion") Long currentSupersededByVersion);

  /**
   * Count current warning totals for the supplied claims, defaulting warning reads to the current
   * sentinel value.
   */
  default List<ClaimWarningCountProjection> countWarningsByClaimIdsAndType(
      Collection<UUID> claimIds, ValidationMessageType type) {
    return countWarningsByClaimIdsAndType(
        claimIds, type, ValidationMessageType.WARNING, CURRENT_SUPERSEDED_BY_VERSION);
  }

  @Query(
      """
           SELECT v.claimId AS claimId,
                  COUNT(v)   AS warningCount
           FROM ValidationMessageLog v
           WHERE v.claimId IN :claimIds
             AND v.type = :type
             AND (
               :type IS NULL
               OR :type <> :warningType
               OR v.supersededByVersion = :currentSupersededByVersion
             )
           GROUP BY v.claimId
           """)
  List<ClaimWarningCountProjection> countWarningsByClaimIdsAndType(
      @Param("claimIds") Collection<UUID> claimIds,
      @Param("type") ValidationMessageType type,
      @Param("warningType") ValidationMessageType warningType,
      @Param("currentSupersededByVersion") Long currentSupersededByVersion);

  /**
   * Find current validation messages for the supplied filters, defaulting warning reads to the
   * current sentinel value.
   */
  default Page<ValidationMessageWithClaimDetailsProjection> findWithClaimDetailsByFilters(
      UUID submissionId,
      UUID claimId,
      ValidationMessageType type,
      String source,
      Pageable pageable) {
    return findWithClaimDetailsByFilters(
        submissionId,
        claimId,
        type,
        source,
        ValidationMessageType.WARNING,
        CURRENT_SUPERSEDED_BY_VERSION,
        pageable);
  }

  @Query(
      """
           SELECT v.id              AS id,
                  v.submissionId    AS submissionId,
                  v.claimId         AS claimId,
                  v.type            AS type,
                  v.source          AS source,
                  v.displayMessage  AS displayMessage,
                  v.messageCode     AS messageCode,
                  cl.uniqueFileNumber        AS uniqueFileNumber,
                  cli.clientForename         AS clientForename,
                  cli.clientSurname          AS clientSurname,
                  cli.uniqueClientNumber     AS uniqueClientNumber,
                  cli.client2Forename        AS client2Forename,
                  cli.client2Surname         AS client2Surname,
                  cli.client2Ucn             AS client2Ucn
           FROM ValidationMessageLog v
           LEFT JOIN Claim cl ON cl.id = v.claimId
           LEFT JOIN Client cli ON cli.claim.id = v.claimId
           WHERE v.submissionId = :submissionId
             AND (:claimId IS NULL OR v.claimId = :claimId)
             AND (:type IS NULL OR v.type = :type)
             AND (
               :type IS NULL
               OR :type <> :warningType
               OR v.supersededByVersion = :currentSupersededByVersion
             )
             AND (:source IS NULL OR v.source = :source)
           """)
  Page<ValidationMessageWithClaimDetailsProjection> findWithClaimDetailsByFilters(
      @Param("submissionId") UUID submissionId,
      @Param("claimId") UUID claimId,
      @Param("type") ValidationMessageType type,
      @Param("source") String source,
      @Param("warningType") ValidationMessageType warningType,
      @Param("currentSupersededByVersion") Long currentSupersededByVersion,
      Pageable pageable);

  @Modifying
  @Transactional
  @Query(
      """
      UPDATE ValidationMessageLog v
         SET v.supersededByVersion = :supersededByVersion
       WHERE v.claimId = :claimId
         AND v.source = :source
         AND v.type = :type
         AND v.supersededByVersion = :currentSupersededByVersion
      """)
  int supersedeCurrentByClaimIdAndSource(
      @Param("claimId") UUID claimId,
      @Param("source") String source,
      @Param("type") ValidationMessageType type,
      @Param("supersededByVersion") Long supersededByVersion,
      @Param("currentSupersededByVersion") Long currentSupersededByVersion);
}
