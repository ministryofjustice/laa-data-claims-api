package uk.gov.justice.laa.dstew.payments.claimsdata.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claimsdata.client.FeeSchemePlatformRestClient;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.AwsTestConfig;

/**
 * Cucumber Spring boot configuration for BDD end-to-end tests.
 *
 * <p>Boots the full Spring Boot application on a random port so step definitions can exercise the
 * real HTTP stack via {@code RestTemplate} — unlike integration tests, BDD tests must NOT use
 * {@code MockMvc}.
 *
 * <p>The two {@link MockitoBean} declarations replace the external HTTP-facing beans on the
 * amendment flow with Mockito mocks so BDD scenarios can drive the amendment pipeline end-to-end
 * without requiring a real Fee Scheme Platform or Provider Details API endpoint. Default answers
 * are applied per-scenario by {@code BddAmendmentResetHook}. See DSTEW-2301 for background.
 *
 * <p><b>SCOPE RISK — global {@link ValidationService} mock.</b> {@code ValidationService} is the
 * aggregate claim/submission validation facade, not only the PDA HTTP transport. Replacing it as a
 * {@link MockitoBean} on this class means the mock is active for <em>every</em> BDD scenario, not
 * just amendment-harness scenarios. Non-amendment scenarios that depend on real {@code
 * ValidationService} behaviour (resolved-data population, area-of-law resolution, other non-PDA
 * validator side-effects) will silently see {@code valid=true} without those side-effects being
 * applied. Today's regression is green, but that green is fragile — any future scenario that reads
 * {@code resolvedData} could false-positive.
 *
 * <p>The correct long-term fix is to mock the PDA-facing HTTP transport instead of the aggregate
 * facade (or to scope this override with a Cucumber-tag-conditional bean). Both options require
 * refactoring the reusable validation-core package and are out of scope for this harness PR.
 * Tracked as <b>follow-up story</b> — see {@code
 * ~/IdeaProjects/jira_drafts/DSTEW-XXXX_scope_validationservice_mock.md} for the draft ticket body.
 */
@CucumberContextConfiguration
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AwsTestConfig.class, BddBeansConfiguration.class})
public class CucumberSpringConfiguration {

  @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:latest");

  static {
    postgresContainer.start();
  }

  @MockitoBean private FeeSchemePlatformRestClient feeSchemePlatformRestClient;

  /**
   * WARNING — aggregate-facade mock; see class-level Javadoc for the scope-risk explanation.
   * Replace with a targeted PDA-transport mock in the follow-up story before adding scenarios that
   * depend on real {@code ValidationService} side-effects.
   */
  @MockitoBean private ValidationService validationService;
}
