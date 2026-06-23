package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;

/** Repository for accessing {@link AmendmentReasonReferenceEntity} governed reference data. */
@Repository
public interface AmendmentReasonReferenceRepository
    extends JpaRepository<AmendmentReasonReferenceEntity, UUID> {

  /**
   * Returns the active Amendment Reason reference values ordered by Requested By code and display
   * order, so they can be grouped under their Requested By value in display order.
   *
   * @return active Amendment Reason values ordered for grouping
   */
  List<AmendmentReasonReferenceEntity> findByIsActiveTrueOrderByRequestedByCodeAscDisplayOrderAsc();

  /**
   * Returns all Amendment Reason reference values (active and inactive) ordered by Requested By
   * code and display order, so they can be grouped under their Requested By value with the
   * is_active flag preserved (e.g. to resolve display labels for historical amendments).
   *
   * @return all Amendment Reason values ordered for grouping
   */
  List<AmendmentReasonReferenceEntity> findByOrderByRequestedByCodeAscDisplayOrderAsc();
}
