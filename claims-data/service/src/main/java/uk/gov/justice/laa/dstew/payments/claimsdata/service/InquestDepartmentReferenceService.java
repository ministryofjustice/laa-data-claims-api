package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.InquestDepartmentReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.DepartmentReferenceRepository;

/** Serves the complete governed department list in configured display order. */
@Service
@RequiredArgsConstructor
public class InquestDepartmentReferenceService {
  private final DepartmentReferenceRepository repository;

  /** Returns active and inactive departments so historical codes remain resolvable. */
  @Transactional(readOnly = true)
  public List<InquestDepartmentReference> getAll() {
    return repository.findAllByOrderByDisplayOrderAsc().stream()
        .map(
            entity ->
                new InquestDepartmentReference(
                    entity.getCode(),
                    entity.getDisplayLabel(),
                    entity.getDisplayOrder(),
                    entity.getIsActive()))
        .toList();
  }
}
