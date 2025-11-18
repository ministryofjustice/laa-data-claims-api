package uk.gov.justice.laa.dstew.payments.claimsdata.monitoring;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Simple helper for capturing messages and exceptions to Sentry.
 *
 * <p>Based on LAA's pattern of simple capture_message/capture_exception methods.
 */
@Component
@Slf4j
public class SentryHelper {

  /**
   * Capture a message to Sentry with optional context.
   *
   * @param message the message to capture
   * @param level the severity level (info, warning, error)
   * @param tags optional tags to add context
   */
  public void captureMessage(String message, SentryLevel level, Map<String, String> tags) {
    try {
      Sentry.configureScope(
          scope -> {
            if (tags != null) {
              tags.forEach(scope::setTag);
            }
          });
      Sentry.captureMessage(message, level);
      log.debug("Captured message to Sentry: {}", message);
    } catch (Exception e) {
      log.warn("Failed to capture message to Sentry: {}", message, e);
    }
  }

  /**
   * Capture a message to Sentry at ERROR level.
   *
   * @param message the message to capture
   */
  public void captureMessage(String message) {
    captureMessage(message, SentryLevel.ERROR, null);
  }

  /**
   * Capture an exception to Sentry with optional context.
   *
   * @param exception the exception to capture
   * @param tags optional tags to add context
   */
  public void captureException(Throwable exception, Map<String, String> tags) {
    try {
      Sentry.configureScope(
          scope -> {
            if (tags != null) {
              tags.forEach(scope::setTag);
            }
          });
      Sentry.captureException(exception);
      log.debug("Captured exception to Sentry: {}", exception.getMessage());
    } catch (Exception e) {
      log.warn("Failed to capture exception to Sentry", e);
    }
  }

  /**
   * Capture an exception to Sentry.
   *
   * @param exception the exception to capture
   */
  public void captureException(Throwable exception) {
    captureException(exception, null);
  }
}
