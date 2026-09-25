package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.GovernmentDepartmentRef;

/**
 * Repository for accessing {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.entity.GovernmentDepartmentRef} entities.
 */
@Repository
public interface GovernmentDepartmentRefRepository
    extends JpaRepository<GovernmentDepartmentRef, UUID> {}
