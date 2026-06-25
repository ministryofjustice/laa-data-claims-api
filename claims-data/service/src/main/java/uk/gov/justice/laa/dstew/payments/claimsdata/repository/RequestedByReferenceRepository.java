package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;

/** Repository for accessing {@link RequestedByReferenceEntity} governed reference data. */
@Repository
public interface RequestedByReferenceRepository
    extends JpaRepository<RequestedByReferenceEntity, UUID> {

  /**
   * Returns the active Requested By reference values ordered by their display order.
   *
   * @return active Requested By values in display order
   */
  List<RequestedByReferenceEntity> findByIsActiveTrueOrderByDisplayOrderAsc();

  /**
   * Returns all Requested By reference values (active and inactive) ordered by display order, so
   * the lookup can expose every value with its is_active flag (e.g. to resolve display labels for
   * historical amendments).
   *
   * @return all Requested By values in display order
   */
  List<RequestedByReferenceEntity> findByOrderByDisplayOrderAsc();
}
