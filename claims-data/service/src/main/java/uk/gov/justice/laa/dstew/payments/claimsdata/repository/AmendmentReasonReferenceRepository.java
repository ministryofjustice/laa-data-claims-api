package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReference;

/**
 * Repository interface for managing {@link AmendmentReasonReference} entities. *
 *
 * <p>Handles the governed reference data representing the valid business reasons for modifying a
 * finalized claim. To eliminate free-text drift and maintain referential integrity, these reasons
 * are strictly scoped to a parent {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReference} via a composite
 * relationship.
 */
@Repository
public interface AmendmentReasonReferenceRepository
    extends JpaRepository<AmendmentReasonReference, UUID> {

  /**
   * Fetch all valid, active amendment reasons scoped to a specific requested_by_reference row ID.
   * Useful for building the dependent dropdown lists in the UI.
   */
  List<AmendmentReasonReference> findByRequestedByIdAndIsActiveTrueOrderByDisplayOrderAsc(
      UUID requestedById);

  /** Look up a reason by its unique composite constraint pair. */
  Optional<AmendmentReasonReference> findByRequestedByCodeAndCode(
      String requestedByCode, String code);
}
