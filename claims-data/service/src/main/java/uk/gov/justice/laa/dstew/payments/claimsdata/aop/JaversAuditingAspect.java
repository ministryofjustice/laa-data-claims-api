package uk.gov.justice.laa.dstew.payments.claimsdata.aop;

import java.util.Optional;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.javers.core.Javers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

/**
 * An aspect responsible for auditing changes to entities using Javers.
 * This aspect intercepts save and delete operations in the specified
 * repository package and commits changes to Javers for auditing.
 */
@Aspect
@Component
public class JaversAuditingAspect {
  private final Javers javers;

  public JaversAuditingAspect(Javers javers) {
    this.javers = javers;
  }

  // TODO: replace with the actual user ID/name when available, as per DSTEW-88
  private static final String API_USER = "api-user";

  /**
   * Audits the save operation performed on an entity within the specified repository package.
   * This is run after the successful execution of the save method in any of the classes in the
   * specified repository package. It uses Javers to save it in the audit log table (jv_snapshot)
   *
   * @param joinPoint the join point providing reflective access to the intercepted method
   * @param result the result of the save operation, representing the saved entity
   */
  @AfterReturning(
      pointcut = "execution(* uk.gov.justice.laa.dstew.payments.claimsdata.repository.*.save(..))",
      returning = "result")
  public void auditSave(JoinPoint joinPoint, Object result) {
    if (result != null) {
      javers.commit(API_USER, result);
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
  @Before("execution(* uk.gov.justice.laa.dstew.payments.claimsdata.repository.*.deleteById(..)) "
          + "&& args(id)")
  public void auditDelete(JoinPoint joinPoint, Object id) {
    // You need to load the entity before deletion to audit it
    Object repo = joinPoint.getTarget();
    if (repo instanceof JpaRepository rawRepo) {
      Optional<?> entity = rawRepo.findById(id);
      entity.ifPresent(e -> javers.commitShallowDelete(API_USER, e));
    }
  }
}