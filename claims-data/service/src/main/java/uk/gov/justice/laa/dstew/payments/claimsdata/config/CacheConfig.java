package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.payments.claimsdata.provider.AmendmentReferenceDataProvider;

/**
 * Enables Spring's caching abstraction and configures a Caffeine-backed cache for the governed
 * amendment reference data.
 *
 * <p>The cache uses a time-to-live (write) expiry, so entries are evicted after {@code
 * laa.claims.api.amendments.cache.refresh} (see {@link ClaimsApiProperties}) and lazily reloaded on
 * the next access. This suits reference data that changes rarely.
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(ClaimsApiProperties.class)
public class CacheConfig {

  /**
   * Builds the cache manager for the amendment reference data cache.
   *
   * @param properties the Claims API configuration providing the cache time-to-live
   * @return the configured cache manager
   */
  @Bean
  public CacheManager cacheManager(ClaimsApiProperties properties) {
    Duration refresh = properties.getAmendments().getCache().getRefresh();
    CaffeineCacheManager cacheManager =
        new CaffeineCacheManager(AmendmentReferenceDataProvider.CACHE_NAME);
    cacheManager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(refresh));
    return cacheManager;
  }
}
