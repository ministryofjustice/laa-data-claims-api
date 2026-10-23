package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the Claims API, rooted at the {@code laa.claims.api} namespace.
 *
 * <p>Centralising the prefix here keeps property paths consistent and discoverable: every Claims
 * API setting lives under {@code laa.claims.api.*}, with selected capabilities grouped under {@code
 * features}. New settings should be added as nested fields here rather than scattering
 * {@code @Value("${...}")} strings, so the namespace stays maintained and self-evident.
 */
@Getter
@ConfigurationProperties(prefix = "laa.claims.api")
public class ClaimsApiProperties {

  /** Feature-specific Claims API settings. */
  @Getter
  @Setter
  public static class Features {

    /**
     * Raw validated-pending-approval feature flag ({@code
     * laa.claims.api.features.validated-pending-approval}).
     *
     * <p>Bound as a {@link String} so missing and invalid values fail safe to disabled.
     */
    private String validatedPendingApproval;

    public boolean isValidatedPendingApprovalEnabled() {
      return isExplicitlyTrue(validatedPendingApproval);
    }

    private static boolean isExplicitlyTrue(String value) {
      return value != null && Boolean.parseBoolean(value.trim());
    }
  }

  /** Cache settings for amendment reference data ({@code laa.claims.api.amendments.cache.*}). */
  private final Amendments amendments = new Amendments();

  /** Settings for the inquests feature ({@code laa.claims.api.inquests.*}). */
  private final Inquests inquests = new Inquests();

  private final Features features = new Features();

  /** Settings for the inquests feature. */
  @Getter
  @Setter
  public static class Inquests {

    /**
     * Raw configured value for the inquests toggle ({@code laa.claims.api.inquests.enabled}).
     *
     * <p>Bound as a {@link String} so missing and invalid values fail safe to disabled.
     */
    private String enabled;

    /**
     * Whether the inquests capability is enabled.
     *
     * @return {@code true} only when the configured value is explicitly {@code true}
     */
    public boolean isEnabled() {
      return enabled != null && Boolean.parseBoolean(enabled.trim());
    }
  }

  /** Settings for the amendment reference-data cache. */
  @Getter
  public static class Amendments {

    /** Caching settings for the governed amendment reference data. */
    private final Cache cache = new Cache();

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
