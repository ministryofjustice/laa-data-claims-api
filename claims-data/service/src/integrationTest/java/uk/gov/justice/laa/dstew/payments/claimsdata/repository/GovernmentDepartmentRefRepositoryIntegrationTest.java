package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.GovernmentDepartmentRef;

class GovernmentDepartmentRefRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Test
  @DisplayName("findByDisplayLabelIgnoreCase returns the department regardless of name casing")
  void findByDisplayLabelIgnoreCaseReturnsMatchingDepartment() {
    GovernmentDepartmentRef saved = findSeededDepartmentByLabel(DEPARTMENT_MINISTRY_OF_JUSTICE);

    var result =
        governmentDepartmentRefRepository.findByDisplayLabelIgnoreCase("mInIsTrY oF jUsTiCe");

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(saved.getId());
  }

  @Test
  @DisplayName("findByDisplayLabelIgnoreCase returns empty when no department has that name")
  void findByDisplayLabelIgnoreCaseReturnsEmptyWhenNoMatch() {
    var result =
        governmentDepartmentRefRepository.findByDisplayLabelIgnoreCase(
            "Department That Does Not Exist");

    assertThat(result).isEmpty();
  }
}
