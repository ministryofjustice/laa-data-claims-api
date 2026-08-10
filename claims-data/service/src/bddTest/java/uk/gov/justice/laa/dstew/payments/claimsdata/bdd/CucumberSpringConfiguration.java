package uk.gov.justice.laa.dstew.payments.claimsdata.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
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
 */
@CucumberContextConfiguration
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AwsTestConfig.class, BddBeansConfiguration.class})
public class CucumberSpringConfiguration {

  private static final DockerImageName MOCKSERVER_IMAGE =
      DockerImageName.parse("mockserver/mockserver:5.15.0");

  @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:latest");

  public static final MockServerContainer MOCK_SERVER =
      new MockServerContainer(MOCKSERVER_IMAGE)
          .waitingFor(Wait.forListeningPort().withStartupTimeout(java.time.Duration.ofSeconds(60)));

  static {
    postgresContainer.start();
    MOCK_SERVER.start();
  }

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
    // The DSTEW-1773 BDD scenarios need one global timeout that allows a 2s success but still
    // times out a much slower response. Per-scenario overrides would require refreshing the Spring
    // test context, so the step definitions record the requested values while this startup value
    // drives the actual runtime behaviour.
    registry.add("laa.dstew.payments.validator.provider-details-api.readTimeoutMs", () -> "3000");
    registry.add("resilience4j.retry.instances.pdaRetry.maxAttempts", () -> "1");
  }
}
