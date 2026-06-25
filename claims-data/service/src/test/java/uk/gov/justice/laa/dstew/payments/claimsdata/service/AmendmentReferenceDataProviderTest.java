package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentReferenceData;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AmendmentReferenceDataUnavailableException;
import uk.gov.justice.laa.dstew.payments.claimsdata.provider.AmendmentReferenceDataProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AmendmentReasonReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.RequestedByReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
@DisplayName("AmendmentReferenceDataProvider")
class AmendmentReferenceDataProviderTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

  @Mock private RequestedByReferenceRepository requestedByReferenceRepository;
  @Mock private AmendmentReasonReferenceRepository amendmentReasonReferenceRepository;

  @InjectMocks private AmendmentReferenceDataProvider provider;

  @Test
  @DisplayName("composes the reference data snapshot from both repositories")
  void composesReferenceDataFromRepositories() {
    RequestedByReferenceEntity requestedBy =
        RequestedByReferenceEntity.builder()
            .id(Uuid7.timeBasedUuid())
            .code("PROVIDER")
            .displayLabel("Provider")
            .isActive(true)
            .displayOrder(10)
            .createdByUserId("test")
            .createdOn(FIXED_INSTANT)
            .build();
    AmendmentReasonReferenceEntity reason =
        AmendmentReasonReferenceEntity.builder()
            .id(Uuid7.timeBasedUuid())
            .requestedByCode("PROVIDER")
            .code("PROVIDER_ERROR")
            .displayLabel("Provider Error")
            .isActive(true)
            .displayOrder(10)
            .createdByUserId("test")
            .createdOn(FIXED_INSTANT)
            .build();
    when(requestedByReferenceRepository.findByOrderByDisplayOrderAsc())
        .thenReturn(List.of(requestedBy));
    when(amendmentReasonReferenceRepository.findByOrderByRequestedByCodeAscDisplayOrderAsc())
        .thenReturn(List.of(reason));

    ClaimAmendmentReferenceData data = provider.getReferenceData();

    assertThat(data.requestedBy()).containsExactly(requestedBy);
    assertThat(data.reasons()).containsExactly(reason);
    assertThat(data.isEmpty()).isFalse();
  }

  @Test
  @DisplayName("standardises a read failure into AmendmentReferenceDataUnavailableException")
  void throwsUnavailableWhenRepositoryReadFails() {
    when(requestedByReferenceRepository.findByOrderByDisplayOrderAsc())
        .thenThrow(new DataAccessResourceFailureException("db down"));

    assertThatThrownBy(() -> provider.getReferenceData())
        .isInstanceOf(AmendmentReferenceDataUnavailableException.class)
        .hasMessage("A technical error occurred, please try again after some time");
  }
}
