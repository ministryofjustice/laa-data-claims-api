package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.GovernmentDepartmentRef;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GovernmentDepartmentsList;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.GovernmentDepartmentRefRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
@DisplayName("GovernmentDepartmentService")
class GovernmentDepartmentServiceTest {

  @Mock private GovernmentDepartmentRefRepository governmentDepartmentRefRepository;

  @InjectMocks private GovernmentDepartmentService service;

  private GovernmentDepartmentRef department(String code, String label, int order) {
    return GovernmentDepartmentRef.builder()
        .id(Uuid7.timeBasedUuid())
        .governmentDepartmentCode(code)
        .displayLabel(label)
        .isActive(true)
        .displayOrder(order)
        .createdByUserId("test")
        .createdOn(Instant.now())
        .build();
  }

  @Nested
  @DisplayName("getGovernmentDepartments - list assembly")
  class ListAssembly {

    @Test
    @DisplayName("returns an empty list when there are no government departments")
    void returnsEmptyListWhenNoDepartments() {
      when(governmentDepartmentRefRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
          .thenReturn(List.of());

      GovernmentDepartmentsList result = service.getGovernmentDepartments();

      assertThat(result.getGovernmentDepartments()).isEmpty();
    }

    @Test
    @DisplayName("maps each department's display label to its name, preserving order")
    void mapsDisplayLabelsInOrder() {
      when(governmentDepartmentRefRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
          .thenReturn(
              List.of(
                  department("HOME_OFFICE", "Home Office", 10),
                  department("MOJ", "Ministry of Justice", 20)));

      GovernmentDepartmentsList result = service.getGovernmentDepartments();

      assertThat(result.getGovernmentDepartments())
          .extracting("name")
          .containsExactly("Home Office", "Ministry of Justice");
    }
  }
}
