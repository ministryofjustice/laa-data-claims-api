package uk.gov.justice.laa.dstew.payments.claimsdata.provider;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.provider.GovernmentDepartmentProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GovernmentDepartment;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.GovernmentDepartmentService;

/**
 * Repository-backed implementation of {@link GovernmentDepartmentProvider}.
 *
 * <p>Used when the claims-validation-core library is embedded directly inside the Claims API
 * service, which owns the Government Department reference data. Replaces the library's HTTP
 * provider, so the Claims API does not call its own {@code /system/references/departments}
 * endpoint.
 *
 * <p>Delegates to {@link GovernmentDepartmentService}, so validation uses the same active
 * departments, in display order, as that endpoint.
 */
@RequiredArgsConstructor
@Component
public class RepositoryGovernmentDepartmentProvider implements GovernmentDepartmentProvider {

  private final GovernmentDepartmentService governmentDepartmentService;

  /**
   * Returns the active Government Department names in display order.
   *
   * @return the active Government Department names
   * @throws IllegalStateException if there are no active Government Departments, matching the
   *     library's HTTP provider so the validator reports a technical error
   */
  @Override
  public List<String> getActiveGovernmentDepartments() {
    List<String> governmentDepartments =
        governmentDepartmentService.getGovernmentDepartments().getGovernmentDepartments().stream()
            .map(GovernmentDepartment::getName)
            .toList();
    if (governmentDepartments.isEmpty()) {
      throw new IllegalStateException("No active Government Departments found");
    }
    return governmentDepartments;
  }
}
