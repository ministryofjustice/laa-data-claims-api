package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.GovernmentDepartmentRef;

/** Repository for accessing {@link GovernmentDepartmentRef} entities. */
@Repository
public interface GovernmentDepartmentRefRepository
    extends JpaRepository<GovernmentDepartmentRef, UUID> {

  /**
   * Finds all active government departments ordered by display order.
   *
   * @return the active government departments in display order
   */
  List<GovernmentDepartmentRef> findByIsActiveTrueOrderByDisplayOrderAsc();

  Optional<GovernmentDepartmentRef> findByDisplayLabelIgnoreCase(String displayLabel);
}
