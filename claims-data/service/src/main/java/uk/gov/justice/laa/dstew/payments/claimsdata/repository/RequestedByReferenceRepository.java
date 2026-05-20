package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReference;

/**
 * Repository interface for managing {@link RequestedByReference} entities. *
 *
 * <p>Provides access to governed reference data identifying the categories of parties or systems
 * authorized to request a claim amendment (e.g., 'PROVIDER', 'AUDITOR'). *
 *
 * <p>Data managed by this repository is considered highly stable and business-critical, serving as
 * the parent validation anchor for dependent amendment reasons.
 */
@Repository
public interface RequestedByReferenceRepository extends JpaRepository<RequestedByReference, UUID> {

  /** Look up the reference entity by its stable unique machine code (e.g., 'PROVIDER'). */
  Optional<RequestedByReference> findByCode(String code);
}
