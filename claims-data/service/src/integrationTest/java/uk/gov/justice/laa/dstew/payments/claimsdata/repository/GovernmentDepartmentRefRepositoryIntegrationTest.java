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
  @DisplayName("findByDisplayLabelIgnoreCase returns the department regardless of name casing")
  void findByDisplayLabelIgnoreCaseReturnsMatchingDepartment() {
    GovernmentDepartmentRef saved = saveDepartment(DEPARTMENT_LABEL);

    var result =
        governmentDepartmentRefRepository.findByDisplayLabelIgnoreCase("mInIsTrY oF jUsTiCe");

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(saved.getId());
  }

  @Test
  @DisplayName("findByDisplayLabelIgnoreCase returns empty when no department has that full name")
  void findByDisplayLabelIgnoreCaseReturnsEmptyWhenNoMatch() {
    saveDepartment(DEPARTMENT_LABEL);

    var result =
        governmentDepartmentRefRepository.findByDisplayLabelIgnoreCase(
            "Department That Does Not Exist");

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
