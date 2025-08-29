package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationErrorLog;

/**
 * Repository for persisting {@link ValidationErrorLog} entries.
 */
public interface ValidationErrorLogRepository extends JpaRepository<ValidationErrorLog, UUID> {}