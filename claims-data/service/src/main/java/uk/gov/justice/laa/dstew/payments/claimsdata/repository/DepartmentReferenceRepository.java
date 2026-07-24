package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.DepartmentReference;

/** Persistence access for governed inquest department references. */
public interface DepartmentReferenceRepository extends JpaRepository<DepartmentReference, UUID> {
  List<DepartmentReference> findAllByOrderByDisplayOrderAsc();

  long countByCodeIn(List<String> codes);
}
