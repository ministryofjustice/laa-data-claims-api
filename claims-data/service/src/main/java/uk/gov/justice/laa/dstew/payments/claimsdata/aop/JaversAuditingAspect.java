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

  //TODO: replace with the actual user ID/name when available, as per DSTEW-88
  private static final String API_USER = "api-user";

  @AfterReturning(pointcut = "execution(* uk.gov.justice.laa.dstew.payments.claimsdata.repository.*.save(..))", returning = "result")
  public void auditSave(JoinPoint joinPoint, Object result) {
    if (result != null) {
      javers.commit(API_USER, result);
    }
  }

  @Before("execution(* uk.gov.justice.laa.dstew.payments.claimsdata.repository.*.deleteById(..)) && args(id)")
  public void auditDelete(JoinPoint joinPoint, Object id) {
    // You need to load the entity before deletion to audit it
    Object repo = joinPoint.getTarget();
    if (repo instanceof JpaRepository rawRepo) {
      Optional<?> entity = rawRepo.findById(id);
      entity.ifPresent(e -> javers.commitShallowDelete(API_USER, e));
    }
  }
}