package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration class to disable security autoconfiguration when running cron jobs. This is
 * necessary to avoid the Spring startup error about a missing HttpSecurity bean. For the cronjob
 * profile we set "spring.main.web-application-type: none" meaning there is no embedded web server,
 * network ports, HTTP endpoints (i.e. no attack surface) or the security config that comes with it.
 *
 * <p>The @ImportAutoConfiguration annotation is used to exclude the
 * SecurityFilterChainAutoConfiguration class, effectively disabling the default security filter
 * chain provided by the application.
 *
 * <p>No additional beans are defined in this class.
 */
@Configuration
@Profile("replication-summary-cronjob")
@ImportAutoConfiguration(
    exclude = {uk.gov.laa.springboot.auth.SecurityFilterChainAutoConfiguration.class})
public class NullSecurityConfig {
  // No beans needed
}
