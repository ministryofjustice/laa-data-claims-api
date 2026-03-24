package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AuditLog;

/** Repository for accessing audit log entries. */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
  List<AuditLog> findByTableNameAndPrimaryKeyOrderByChangedAtAsc(
      String tableName, String primaryKey);
}
