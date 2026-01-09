package uk.gov.justice.laa.dstew.payments.claimsdata.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Service responsible for creating audit revision records following an insert, update or delete.
 */
@Service
public class AuditRevisionListener implements RevisionListener {

  @Override
  public void newRevision(Object revision) {
    var auditRevision = (AuditRevision) revision;
    auditRevision.setUserId(auditRevision.getUserId());
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    auditRevision.setUserId(Uuid7.timeBasedUuid());
    //    UserDetails userDetails = (UserDetails) authentication.getPrincipal();
    //    auditRevision.setUserId(userDetails.getUsername());
  }
}
