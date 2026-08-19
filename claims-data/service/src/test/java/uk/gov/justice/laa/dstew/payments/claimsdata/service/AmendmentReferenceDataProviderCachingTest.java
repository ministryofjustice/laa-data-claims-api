package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.CacheConfig;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentReferenceData;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.provider.AmendmentReferenceDataProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AmendmentReasonReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.RequestedByReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@SpringJUnitConfig(classes = {CacheConfig.class, AmendmentReferenceDataProvider.class})
@TestPropertySource(properties = "laa.claims.api.amendments.cache.refresh=30m")
@DisplayName("AmendmentReferenceDataProvider caching")
class AmendmentReferenceDataProviderCachingTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

  @MockitoBean private RequestedByReferenceRepository requestedByReferenceRepository;
  @MockitoBean private AmendmentReasonReferenceRepository amendmentReasonReferenceRepository;

  @Autowired private AmendmentReferenceDataProvider provider;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void clearCache() {
    Objects.requireNonNull(cacheManager.getCache(AmendmentReferenceDataProvider.CACHE_NAME))
        .clear();
  }

  private RequestedByReferenceEntity requestedBy() {
    return RequestedByReferenceEntity.builder()
        .id(Uuid7.timeBasedUuid())
        .code("PROVIDER")
        .displayLabel("Provider")
        .isActive(true)
        .displayOrder(10)
        .createdByUserId("test")
        .createdOn(FIXED_INSTANT)
        .build();
  }

  private AmendmentReasonReferenceEntity reason() {
    return AmendmentReasonReferenceEntity.builder()
        .id(Uuid7.timeBasedUuid())
        .requestedByCode("PROVIDER")
        .code("PROVIDER_ERROR")
        .displayLabel("Provider Error")
        .isActive(true)
        .displayOrder(10)
        .createdByUserId("test")
        .createdOn(FIXED_INSTANT)
        .build();
  }

  @Test
  @DisplayName("serves a second call from cache without re-hitting the repositories")
  void servesSecondCallFromCache() {
    when(requestedByReferenceRepository.findByOrderByDisplayOrderAsc())
        .thenReturn(List.of(requestedBy()));
    when(amendmentReasonReferenceRepository.findByOrderByRequestedByCodeAscDisplayOrderAsc())
        .thenReturn(List.of(reason()));

    provider.getReferenceData();
    ClaimAmendmentReferenceData second = provider.getReferenceData();

    assertThat(second.requestedBy()).hasSize(1);
    assertThat(second.reasons()).hasSize(1);
    verify(requestedByReferenceRepository, times(1)).findByOrderByDisplayOrderAsc();
    verify(amendmentReasonReferenceRepository, times(1))
        .findByOrderByRequestedByCodeAscDisplayOrderAsc();
  }

  @Test
  @DisplayName("does not cache an empty (unavailable) result, so the next call retries")
  void doesNotCacheEmptyResult() {
    when(requestedByReferenceRepository.findByOrderByDisplayOrderAsc()).thenReturn(List.of());
    when(amendmentReasonReferenceRepository.findByOrderByRequestedByCodeAscDisplayOrderAsc())
        .thenReturn(List.of());

    provider.getReferenceData();
    provider.getReferenceData();

    verify(requestedByReferenceRepository, times(2)).findByOrderByDisplayOrderAsc();
    verify(amendmentReasonReferenceRepository, times(2))
        .findByOrderByRequestedByCodeAscDisplayOrderAsc();
  }
}
