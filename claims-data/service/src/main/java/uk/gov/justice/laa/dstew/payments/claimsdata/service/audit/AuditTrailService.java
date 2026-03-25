package uk.gov.justice.laa.dstew.payments.claimsdata.service.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AuditLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAuditChange;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AuditLogRepository;

/** Service for retrieving and diffing audit log entries for claims. */
@Service
public class AuditTrailService {
  private final AuditLogRepository auditLogRepository;
  private final AuditDiffHelper auditDiffHelper;

  public AuditTrailService(AuditLogRepository auditLogRepository, AuditDiffHelper auditDiffHelper) {
    this.auditLogRepository = auditLogRepository;
    this.auditDiffHelper = auditDiffHelper;
  }

  /**
   * Retrieves the audit trail for a claim and computes field-level diffs for each change.
   *
   * @param claimId the claim ID
   * @return a list of claim audit changes with diffs
   */
  public List<ClaimAuditChange> getClaimAuditTrail(UUID claimId) {
    List<AuditLog> logs =
        auditLogRepository.findByPrimaryKeyOrderByChangedAtAsc(claimId.toString());
    return logs.stream()
        .map(
            log ->
                new ClaimAuditChange()
                    .id(log.getId() != null ? log.getId().intValue() : null)
                    .changedAt(log.getChangedAt())
                    .operation(log.getOperation())
                    .actorUser(log.getActorUser())
                    .actorService(log.getActorService())
                    .objectName(log.getTableName())
                    .diff(
                        auditDiffHelper.computeDiff(
                            log.getOldData(), log.getNewData(), log.getOperation())))
        .toList();
  }
}
