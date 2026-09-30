package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.GovernmentDepartmentRef;

class GovernmentDepartmentRefRepositoryIntegrationTest extends AbstractIntegrationTest {

  private static final String DEPARTMENT_LABEL = "Ministry of Justice";

  @Test
  @DisplayName("findByDisplayLabel returns the department whose full name matches")
  void findByDisplayLabelReturnsMatchingDepartment() {
    GovernmentDepartmentRef saved = saveDepartment(DEPARTMENT_LABEL);

    var result = governmentDepartmentRefRepository.findByDisplayLabel(DEPARTMENT_LABEL);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(saved.getId());
  }

  @Test
  @DisplayName("findByDisplayLabel returns empty when no department has that full name")
  void findByDisplayLabelReturnsEmptyWhenNoMatch() {
    saveDepartment(DEPARTMENT_LABEL);

    var result =
        governmentDepartmentRefRepository.findByDisplayLabel("Department That Does Not Exist");

    assertThat(result).isEmpty();
  }

  private GovernmentDepartmentRef saveDepartment(String displayLabel) {
    return governmentDepartmentRefRepository.saveAndFlush(
        GovernmentDepartmentRef.builder()
            .id(UUID.randomUUID())
            .governmentDepartmentCode("MOJ")
            .displayLabel(displayLabel)
            .isActive(true)
            .displayOrder(1)
            .createdByUserId("TEST")
            .build());
  }
}
