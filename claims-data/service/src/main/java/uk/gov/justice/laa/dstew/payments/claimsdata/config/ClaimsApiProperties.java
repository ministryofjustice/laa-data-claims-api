package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the Claims API, rooted at the {@code laa.claims.api} namespace.
 *
 * <p>Centralising the prefix here keeps property paths consistent and discoverable: every Claims
 * API setting lives under {@code laa.claims.api.*}, grouped by feature (for example {@code
 * laa.claims.api.amendments.*}). New settings should be added as nested fields here rather than
 * scattering {@code @Value("${...}")} strings, so the namespace stays maintained and self-evident.
 */
@Getter
@ConfigurationProperties(prefix = "laa.claims.api")
public class ClaimsApiProperties {

  /** Settings for the amendments feature ({@code laa.claims.api.amendments.*}). */
  private final Amendments amendments = new Amendments();

  /** Settings for the amendments feature. */
  @Getter
  @Setter
  public static class Amendments {

    /**
     * Raw configured value for the amendments toggle ({@code laa.claims.api.amendments.enabled}).
     *
     * <p><b>Off by default.</b> Bound as a {@link String} (not a {@code boolean}) so the feature
     * fails safe to off: it is enabled only when this resolves to {@code true} via {@link
     * #isEnabled()}. An absent/null, blank, {@code false} or otherwise invalid value leaves the
     * feature off without failing application start-up. Prefer {@link #isEnabled()} over reading
     * this field directly.
     */
    private String enabled;

    /** Caching settings for the governed amendment reference data. */
    private final Cache cache = new Cache();

    /**
     * Whether the amendments capability is enabled, resolving the raw {@link #enabled} value
     * fail-safe to off.
     *
     * <p>Returns {@code true} only when the configured value is {@code "true"} (case-insensitive,
     * ignoring surrounding whitespace); every other value - absent/null, blank, {@code "false"} or
     * any unrecognised value - returns {@code false}.
     *
     * @return {@code true} only when amendments are explicitly enabled
     */
    public boolean isEnabled() {
      return enabled != null && Boolean.parseBoolean(enabled.trim());
    }

    /** Caching settings for the governed amendment reference data. */
    @Getter
    @Setter
    public static class Cache {

      /**
       * Time-to-live before a cached reference-data entry is evicted and lazily reloaded on the
       * next access ({@code laa.claims.api.amendments.cache.refresh}). Accepts a duration such as
       * {@code 30m} or {@code 2h}.
       */
      private Duration refresh = Duration.ofMinutes(30);
    }
  }
}
