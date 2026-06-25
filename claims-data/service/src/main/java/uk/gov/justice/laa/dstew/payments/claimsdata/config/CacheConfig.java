package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>The cache uses a time-to-live (write) expiry, so entries are evicted {@code
 * amendment.reference.refresh-minutes} minutes after they are written and lazily reloaded on the
 * next access. This suits reference data that changes rarely.
 */
@Configuration
@EnableCaching
public class CacheConfig {

  /**
   * Builds the cache manager for the amendment reference data cache.
   *
   * @param refreshMinutes the time-to-live, in minutes, before a cached entry is evicted
   * @return the configured cache manager
   */
  @Bean
  public CacheManager cacheManager(
      @Value("${amendment.reference.refresh-minutes:30}") long refreshMinutes) {
    CaffeineCacheManager cacheManager =
        new CaffeineCacheManager(AmendmentReferenceDataProvider.CACHE_NAME);
    cacheManager.setCaffeine(
        Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(refreshMinutes)));
    return cacheManager;
  }
}
