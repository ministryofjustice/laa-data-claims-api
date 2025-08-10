package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimEntity;

/**
 * Repository for managing claim entities.
 */
@Repository
public interface ClaimRepository extends JpaRepository<ClaimEntity, Long> {}