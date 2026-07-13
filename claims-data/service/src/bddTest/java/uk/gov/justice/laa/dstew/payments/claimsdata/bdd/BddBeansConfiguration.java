package uk.gov.justice.laa.dstew.payments.claimsdata.bdd;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddValidationMessageStepSupport;

/**
 * Spring configuration for Cucumber BDD beans. {@code @Bean} methods cannot live on the
 * {@code @CucumberContextConfiguration} class.
 */
@Configuration
public class BddBeansConfiguration {

  @Bean
  public RestTemplate bddRestTemplate() {
    // Use Spring's JDK-based request factory (Java 11+ HttpClient) so that HTTP PATCH is
    // supported out of the box. The default SimpleClientHttpRequestFactory delegates to
    // HttpURLConnection which still does not implement PATCH on all JDKs, and the BDD
    // harness needs PATCH to drive the bulk-submission status transitions used by the
    // duplicate-check scenarios.
    return new RestTemplate(new JdkClientHttpRequestFactory());
  }

  /**
   * Lazily resolves the embedded server base URL. The {@code local.server.port} property is only
   * populated once the embedded web server has actually started, so resolution must be deferred
   * past bean construction.
   */
  @Bean
  public BddServerInfo bddServerInfo(Environment environment) {
    return new BddServerInfo(environment);
  }

  @Bean
  public BddApiStepSupport bddApiStepSupport() {
    return new BddApiStepSupport();
  }

  @Bean
  public BddValidationMessageStepSupport bddValidationMessageStepSupport() {
    return new BddValidationMessageStepSupport();
  }

  @Bean
  public BulkSubmissionFileGenerator legalHelpFileGenerator() {
    return new BulkSubmissionFileGenerator();
  }

  /** Resolves the running embedded server's base URL on each call. */
  public static final class BddServerInfo {
    private final Environment environment;

    public BddServerInfo(Environment environment) {
      this.environment = environment;
    }

    public String baseUrl() {
      String port = environment.getProperty("local.server.port");
      if (port == null || port.isBlank()) {
        throw new IllegalStateException(
            "local.server.port is not yet available — embedded server has not started.");
      }
      return "http://localhost:" + port;
    }
  }
}
