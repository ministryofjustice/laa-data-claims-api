package uk.gov.justice.laa.dstew.payments.claimsdata.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.AwsTestConfig;

/**
 * Cucumber Spring boot configuration for BDD end-to-end tests.
 *
 * <p>Boots the full Spring Boot application on a random port so step definitions can exercise
 * the real HTTP stack via {@code TestRestTemplate} — unlike integration tests, BDD tests must
 * NOT use {@code MockMvc}.
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
}



