package uk.gov.justice.laa.dstew.payments.claimsdata.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GovernmentDepartment;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GovernmentDepartmentsList;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.GovernmentDepartmentService;

@ExtendWith(MockitoExtension.class)
class RepositoryGovernmentDepartmentProviderTest {

  @Mock private GovernmentDepartmentService governmentDepartmentService;

  @InjectMocks private RepositoryGovernmentDepartmentProvider provider;

  @Test
  @DisplayName("returns active department names in service order")
  void returnsActiveDepartmentNamesInOrder() {
    when(governmentDepartmentService.getGovernmentDepartments())
        .thenReturn(
            GovernmentDepartmentsList.builder()
                .governmentDepartments(
                    List.of(
                        GovernmentDepartment.builder().name("Ministry of Justice").build(),
                        GovernmentDepartment.builder().name("Home Office").build()))
                .build());

    assertThat(provider.getActiveGovernmentDepartments())
        .containsExactly("Ministry of Justice", "Home Office");
  }

  @Test
  @DisplayName("throws when there are no active departments")
  void throwsWhenNoActiveDepartments() {
    when(governmentDepartmentService.getGovernmentDepartments())
        .thenReturn(GovernmentDepartmentsList.builder().governmentDepartments(List.of()).build());

    assertThatThrownBy(() -> provider.getActiveGovernmentDepartments())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No active Government Departments found");
  }
}
