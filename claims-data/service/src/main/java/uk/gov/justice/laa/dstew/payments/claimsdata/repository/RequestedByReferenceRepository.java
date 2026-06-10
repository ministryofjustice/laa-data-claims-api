package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReference;

/** Repository for accessing {@link RequestedByReference} governed reference data. */
@Repository
public interface RequestedByReferenceRepository extends JpaRepository<RequestedByReference, UUID> {

  /**
   * Returns the active Requested By reference values ordered by their display order.
   *
   * @return active Requested By values in display order
   */
  List<RequestedByReference> findByIsActiveTrueOrderByDisplayOrderAsc();
}
