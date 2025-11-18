package uk.gov.justice.laa.dstew.payments.claimsdata.monitoring;

import io.sentry.SentryLevel;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter to monitor authentication/authorization failures and capture them to Sentry.
 *
 * <p>This filter runs after authentication and checks the response status. If it's 401 or 403, it
 * captures the failure to Sentry with context.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE) // Run last to capture final response status
@Slf4j
public class AuthFailureMonitoringFilter extends OncePerRequestFilter {

  private SentryHelper sentryHelper;

  /** Default constructor for tests. */
  public AuthFailureMonitoringFilter() {
    this.sentryHelper = null;
  }

  /**
   * Constructor with SentryHelper for production use.
   *
   * @param sentryHelper Sentry helper for error tracking
   */
  @Autowired(required = false)
  public AuthFailureMonitoringFilter(SentryHelper sentryHelper) {
    this.sentryHelper = sentryHelper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    filterChain.doFilter(request, response);

    // After request processing, check if it was an auth failure
    int status = response.getStatus();
    if (status == 401 || status == 403) {
      captureAuthFailure(request, status);
    }
  }

  private void captureAuthFailure(HttpServletRequest request, int status) {
    String endpoint = request.getRequestURI();
    String method = request.getMethod();
    String authType = status == 401 ? "authentication" : "authorization";

    log.warn("🔴 AUTH FAILURE DETECTED - Status: {}, Endpoint: {} {}", status, method, endpoint);

    // Capture to Sentry if available
    if (sentryHelper != null) {
      String message = String.format("%s failure: %s %s", authType, method, endpoint);
      sentryHelper.captureMessage(
          message,
          SentryLevel.WARNING,
          Map.of(
              "error.category", authType,
              "http.status", String.valueOf(status),
              "http.method", method,
              "http.endpoint", endpoint));
      log.warn("✅ Captured {} failure to Sentry: {} {}", authType, method, endpoint);
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // Don't monitor actuator endpoints
    // TODO: Consider if we want to monitor other endpoints too
    String path = request.getRequestURI();
    return path.startsWith("/actuator/");
  }
}
