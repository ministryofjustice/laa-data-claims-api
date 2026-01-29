package uk.gov.justice.laa.dstew.payments.claimsdata.aop;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.javers.core.Javers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;

/**
 * An aspect responsible for auditing changes to entities using Javers. This aspect intercepts save
 * and delete operations in the specified repository package and commits changes to Javers for
 * auditing.
 */
@Slf4j
@Aspect
@Component
public class JaversAuditingAspect {
  private final Javers javers;

  public JaversAuditingAspect(Javers javers) {
    this.javers = javers;
  }

  private static final String API_USER = "api-user";

  /**
   * Audits the save operation performed on an entity within the specified repository package. This
   * is run after the successful execution of the save method in any of the classes in the specified
   * repository package. It uses Javers to save it in the audit log table (jv_snapshot)
   *
   * @param joinPoint the join point providing reflective access to the intercepted method
   * @param result the result of the save operation, representing the saved entity
   */
  @AfterReturning(
      pointcut = "execution(* uk.gov.justice.laa.dstew.payments.claimsdata.repository.*.save(..))",
      returning = "result")
  public void auditSave(JoinPoint joinPoint, Object result) {
    if (result != null) {
      String apiUser = getApiUser(joinPoint.getArgs()[0]);

      // If we're in a Spring-managed transaction, defer audit until the tx commits
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              public void afterCommit() {
                try {
                  javers.commit(apiUser, result);
                } catch (Exception e) {
                  // Do not break the already-committed business tx; log and continue (common)
                  log.error(
                      "JaVers audit commit failed after business commit. entity={}, user={}",
                      result,
                      apiUser,
                      e);
                }
              }
            });
      } else {
        // No tx => commit immediately
        javers.commit(apiUser, result);
      }
    }
  }

  /**
   * Audits the delete operation performed on an entity within the specified repository package.
   * This is run before the successful execution of the deleteById method in any of the classes in
   * the specified repository package. It gets the entity being deleted and uses Javers to save it
   * in the audit log table (jv_snapshot).
   *
   * @param joinPoint the join point providing reflective access to the intercepted method
   * @param id Id of the entity being deleted, used to fetch the data to be audited, before it gets
   *     deleted.
   */
  @Before(
      "execution(* uk.gov.justice.laa.dstew.payments.claimsdata.repository.*.deleteById(..)) "
          + "&& args(id)")
  public void auditDelete(JoinPoint joinPoint, Object id) {
    // You need to load the entity before deletion to audit it
    Object repo = joinPoint.getTarget();
    if (repo instanceof JpaRepository rawRepo) {
      Optional<?> entity = rawRepo.findById(id);
      entity.ifPresent(e -> javers.commitShallowDelete(getApiUser(joinPoint.getArgs()[0]), e));
    }
  }

  private String getApiUser(Object entityType) {

    return switch (entityType) {
      case BulkSubmission bs -> bs.getCreatedByUserId();
      case Submission s -> s.getCreatedByUserId();
      case Claim c -> c.getCreatedByUserId();
      case MatterStart m -> m.getCreatedByUserId();
      case Client cl -> cl.getCreatedByUserId();
      case CalculatedFeeDetail cfd -> cfd.getCreatedByUserId();
      case ClaimSummaryFee csf -> csf.getCreatedByUserId();
      case ClaimCase cc -> cc.getCreatedByUserId();
      default -> API_USER;
    };
  }
}
