package uk.gov.justice.laa.dstew.payments.claimsdata.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import java.time.Duration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mockserver.MockServerContainer;
import org.testcontainers.utility.DockerImageName;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.AwsTestConfig;

/**
 * Cucumber Spring boot configuration for BDD end-to-end tests.
 *
 * <p>Boots the full Spring Boot application on a random port so step definitions can exercise the
 * real HTTP stack via {@code RestTemplate} — unlike integration tests, BDD tests must NOT use
 * {@code MockMvc}.
 *
 * <p>A single {@link MockServerContainer} is started per JVM and its URL is registered as the base
 * URL for the claims-validation-core external HTTP calls (Fee Scheme Platform and Provider Details
 * API). Individual step classes stub / verify against it. The PDA (provider-details) read timeout
 * is deliberately overridden to a small value so amendment-path PDA timeout scenarios (DSTEW-1773,
 * DSTEW-1774) can trip a real socket timeout in seconds rather than the production default.
 */
@CucumberContextConfiguration
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AwsTestConfig.class, BddBeansConfiguration.class})
public class CucumberSpringConfiguration {

  /**
   * Small enough to make amendment-path PDA timeout scenarios trip in seconds. Success scenarios
   * ({@code @PDA_4}) must stay comfortably under this budget.
   */
  public static final int PDA_READ_TIMEOUT_MS = 2000;

  /**
   * Kept distinct from {@link #PDA_READ_TIMEOUT_MS} for scenarios that assert amendment-path
   * timeout independence from any hypothetical new-submission PDA timeout ({@code @PDA_7}). No
   * production property currently separates the two, so this is a fixture value the harness reads
   * back through {@code @Value} for the assertion.
   */
  public static final int NEW_SUBMISSION_PDA_READ_TIMEOUT_MS = 30_000;

  @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:latest");

  private static final DockerImageName MOCKSERVER_IMAGE =
      DockerImageName.parse("mockserver/mockserver:5.15.0");

  /** One MockServer per JVM; started eagerly so {@link DynamicPropertySource} can read its URL. */
  public static final MockServerContainer MOCK_SERVER =
      new MockServerContainer(MOCKSERVER_IMAGE)
          .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)));

  static {
    postgresContainer.start();
    MOCK_SERVER.start();
  }

  /**
   * Points the claims-validation-core external URLs at the running MockServer and shortens the
   * amendment-path PDA read timeout so DSTEW-1773 / DSTEW-1774 timeout scenarios can trip in
   * seconds.
   */
  @DynamicPropertySource
  static void validatorProperties(DynamicPropertyRegistry registry) {
    String baseUrl = MOCK_SERVER.getEndpoint();

    registry.add("FEE_SCHEME_PLATFORM_API_URL", () -> baseUrl);
    registry.add("FEE_SCHEME_PLATFORM_API_ACCESS_TOKEN", () -> "");
    registry.add("PROVIDER_DETAILS_API_URL", () -> baseUrl);
    registry.add("PROVIDER_DETAILS_API_ACCESS_TOKEN", () -> "");

    registry.add("laa.dstew.payments.validator.fee-scheme-platform-api.url", () -> baseUrl);
    registry.add("laa.dstew.payments.validator.fee-scheme-platform-api.accessToken", () -> "");
    registry.add("laa.dstew.payments.validator.provider-details-api.url", () -> baseUrl);
    registry.add("laa.dstew.payments.validator.provider-details-api.accessToken", () -> "");
    registry.add(
        "laa.dstew.payments.validator.provider-details-api.authHeader", () -> "X-Authorization");
    registry.add(
        "laa.dstew.payments.validator.provider-details-api.readTimeoutMs",
        () -> String.valueOf(PDA_READ_TIMEOUT_MS));
    // Fixture property the DSTEW-1773 @PDA_7 scenario reads back to assert that the amendment-path
    // PDA timeout is independent of any (hypothetical) new-submission PDA timeout. There is no
    // separate production property today; this key is BDD-only and lives under a bdd.* namespace.
    registry.add(
        "bdd.pda.newSubmissionReadTimeoutMs",
        () -> String.valueOf(NEW_SUBMISSION_PDA_READ_TIMEOUT_MS));
  }
}
