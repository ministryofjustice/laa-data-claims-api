package uk.gov.justice.laa.dstew.payments.claimsdata.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.provider.GovernmentDepartmentProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;

/**
 * Verifies the Claims API replaces the claims-validation-core HTTP Government Department provider
 * with its own repository-backed provider.
 */
@DisplayName("Repository Government Department provider Integration Test")
class RepositoryGovernmentDepartmentProviderIntegrationTest extends AbstractIntegrationTest {

  @Autowired private GovernmentDepartmentProvider governmentDepartmentProvider;

  @Test
  @DisplayName("uses the repository-backed provider and returns the seeded departments")
  void usesRepositoryBackedProvider() {
    assertThat(governmentDepartmentProvider)
        .isInstanceOf(RepositoryGovernmentDepartmentProvider.class);
    assertThat(governmentDepartmentProvider.getActiveGovernmentDepartments()).isNotEmpty();
  }
}
