package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReference;

/** Repository for accessing {@link AmendmentReasonReference} governed reference data. */
@Repository
public interface AmendmentReasonReferenceRepository
    extends JpaRepository<AmendmentReasonReference, UUID> {

  /**
   * Returns the active Amendment Reason reference values ordered by Requested By code and display
   * order, so they can be grouped under their Requested By value in display order.
   *
   * @return active Amendment Reason values ordered for grouping
   */
  List<AmendmentReasonReference> findByIsActiveTrueOrderByRequestedByCodeAscDisplayOrderAsc();
}
