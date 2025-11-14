package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import io.sentry.SentryOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Sentry application monitoring. This class provides additional
 * configuration for Sentry beyond the auto-configuration. The Spring Boot starter handles the
 * actual initialization via application.yml properties. This config class is always loaded but
 * Sentry itself only initializes when sentry.dsn is configured.
 */
@Configuration
public class SentryConfig {

  private static final Logger logger = LoggerFactory.getLogger(SentryConfig.class);

  @Value("${sentry.enabled:false}")
  private boolean sentryEnabled;

  @Value("${sentry.dsn:}")
  private String sentryDsn;

  @Value("${sentry.environment:unknown}")
  private String environment;

  @Value("${sentry.traces-sample-rate:1.0}")
  private Double tracesSampleRate;

  /**
   * Logs Sentry initialization status. This is called after Spring Boot's auto-configuration has
   * initialized Sentry.
   */
  @PostConstruct
  public void logSentryStatus() {
    logger.info("=== SENTRY CONFIGURATION ===");
    logger.info(
        "Sentry configuration loaded - enabled: {}, dsn: {}, environment: {}, traces-sample-rate: {}",
        sentryEnabled,
        sentryDsn != null && !sentryDsn.isEmpty() ? "***configured***" : "NOT SET",
        environment,
        tracesSampleRate);

    if (sentryEnabled && (sentryDsn == null || sentryDsn.isEmpty())) {
      logger.warn("Sentry is enabled but DSN is not configured!");
    } else if (sentryEnabled && sentryDsn != null && !sentryDsn.isEmpty()) {
      logger.info("✅ Sentry monitoring is ACTIVE for environment: {}", environment);
      logger.info("Sentry will capture exceptions and performance data");
    } else {
      logger.info("Sentry monitoring is DISABLED");
    }
    logger.info("=== END SENTRY CONFIGURATION ===");
  }

  /**
   * Configures Sentry to scrub PII (Personally Identifiable Information) from events. This bean is
   * picked up by Sentry's auto-configuration and filters sensitive data before sending to Sentry.
   *
   * <p>TODO: Review if there is a better way of doing this, does Sentry have built-in PII scrubbing
   * methods?
   *
   * @return SentryOptions.BeforeSendCallback that scrubs PII from events
   */
  @Bean
  public SentryOptions.BeforeSendCallback beforeSendCallback() {
    return (event, hint) -> {
      // Scrub request data that might contain PII
      if (event.getRequest() != null) {
        // Remove query parameters that might contain sensitive data
        event.getRequest().setQueryString(null);

        // Remove cookies that might contain session tokens or user data
        event.getRequest().setCookies(null);

        // Scrub headers that might contain auth tokens or sensitive data
        if (event.getRequest().getHeaders() != null) {
          event.getRequest().getHeaders().remove("Authorization");
          event.getRequest().getHeaders().remove("X-API-Key");
          event.getRequest().getHeaders().remove("Cookie");
          event.getRequest().getHeaders().remove("Set-Cookie");
        }
      }

      // Scrub user information
      if (event.getUser() != null) {
        event.getUser().setEmail(null);
        event.getUser().setUsername(null);
        event.getUser().setIpAddress(null);
      }

      // Scrub breadcrumbs that might contain sensitive data
      if (event.getBreadcrumbs() != null) {
        event
            .getBreadcrumbs()
            .forEach(
                breadcrumb -> {
                  if (breadcrumb.getData() != null) {
                    breadcrumb.getData().remove("password");
                    breadcrumb.getData().remove("token");
                    breadcrumb.getData().remove("api_key");
                    breadcrumb.getData().remove("authorization");
                  }
                });
      }

      logger.debug("Event scrubbed for PII before sending to Sentry");
      return event;
    };
  }
}
