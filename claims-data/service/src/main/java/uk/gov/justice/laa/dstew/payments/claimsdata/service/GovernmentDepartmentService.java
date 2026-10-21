package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.GovernmentDepartmentRef;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GovernmentDepartment;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GovernmentDepartmentsList;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.GovernmentDepartmentRefRepository;

/** Read-only service exposing the active government department reference values. */
@Service
@RequiredArgsConstructor
public class GovernmentDepartmentService {

  private final GovernmentDepartmentRefRepository governmentDepartmentRefRepository;

  /**
   * Builds the government departments lookup payload from the active reference values, preserving
   * display order.
   *
   * @return the government departments list
   */
  @Transactional(readOnly = true)
  public GovernmentDepartmentsList getGovernmentDepartments() {
    var governmentDepartments =
        governmentDepartmentRefRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
            .map(GovernmentDepartmentRef::getDisplayLabel)
            .map(label -> GovernmentDepartment.builder().name(label).build())
            .toList();

    return GovernmentDepartmentsList.builder().governmentDepartments(governmentDepartments).build();
  }
}
