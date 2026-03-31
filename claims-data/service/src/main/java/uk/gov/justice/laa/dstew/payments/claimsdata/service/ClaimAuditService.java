package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javers.core.Changes;
import org.javers.core.diff.changetype.PropertyChange;
import org.javers.core.ChangesByObject;
import org.javers.core.Javers;
import org.javers.repository.jql.QueryBuilder;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.ClaimAuditEvent;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.*;

/** Service to query Javers and produce claim audit events using the Change API JSON output. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimAuditService {

  private final Javers javers;

  /**
   * Return audit events for the given claim id ordered by timestamp ascending. This implementation
   * serializes Javers changes to JSON and parses them to avoid direct dependency on runtime
   * change-type classes while still using Javers 7.10.0 behavior.
   */
  public List<ClaimAuditEvent> getAuditEventsForClaim(String claimId) {
    List<ClaimAuditEvent> auditEvents = new ArrayList<>();
    try {
      Changes changes = javers.findChanges(QueryBuilder.byInstanceId(claimId, Claim.class).withChildValueObjects().build());
        changes.groupByCommit().forEach(byCommit -> {
            log.info("commit " + byCommit.getCommit().getId());

            Changes changesById = javers.findChanges(QueryBuilder.byClass(Client.class, Submission.class, CalculatedFeeDetail.class, ClaimSummaryFee.class).withCommitId(byCommit.getCommit().getId()).build());

            List<ClaimAuditEvent.ClaimAuditPropertyChange> changesA = extractPropertyChanges(byCommit.groupByObject());
            List<ClaimAuditEvent.ClaimAuditPropertyChange> changesB = extractPropertyChanges(changesById.groupByObject());
            List<ClaimAuditEvent.ClaimAuditPropertyChange> allChanges = new ArrayList<>();
            allChanges.addAll(changesA);
            allChanges.addAll(changesB);

            auditEvents.add(ClaimAuditEvent.builder()
                    .timestamp(byCommit.getCommit().getCommitDateInstant())
                    .author(byCommit.getCommit().getAuthor())
                    .commitId(byCommit.getCommit().getId().toString())
                    //.changeType(byCommit.getChanges().get(0).getChangeType().name()) // Assuming all changes in the commit have the same type
                    .changes(allChanges)
                    .build());
        });

    } catch (Exception e) {
      log.error("Error fetching audit events for claim id {}: {}", claimId, e.getMessage(), e);
    }
    return auditEvents;
  }

  private List<ClaimAuditEvent.ClaimAuditPropertyChange> extractPropertyChanges(List<ChangesByObject> changesByObject) {
      List<ClaimAuditEvent.ClaimAuditPropertyChange> propertyChanges = new ArrayList<>();
      changesByObject.forEach(changes -> propertyChanges.addAll(extractPropertyChanges(changes)));
      return propertyChanges;
  }

  private List<ClaimAuditEvent.ClaimAuditPropertyChange> extractPropertyChanges(ChangesByObject changes) {
      return changes.getPropertyChanges().stream().map(c -> buildClaimAuditEvent(changes.getGlobalId().toString(),c)).toList();
  }

  private ClaimAuditEvent.ClaimAuditPropertyChange buildClaimAuditEvent(String id, PropertyChange<?> change) {
      return ClaimAuditEvent.ClaimAuditPropertyChange.builder()
              .property(change.getPropertyName())
              .objectName(id)
                      .oldValue(change.getLeft())
                      .newValue(change.getRight())
                      .build();
  }
 }
