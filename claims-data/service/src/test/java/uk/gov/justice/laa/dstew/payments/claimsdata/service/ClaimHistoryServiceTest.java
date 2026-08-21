package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimHistoryRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;

@ExtendWith(MockitoExtension.class)
class ClaimHistoryServiceTest {

  private static final int DEFAULT_PAGE_SIZE = 20;

  @Mock private ClaimHistoryRepository claimHistoryRepository;
  @Mock private ClaimRepository claimRepository;

  @InjectMocks private ClaimHistoryService claimHistoryService;

  private static ClaimHistoryEventRow submissionRow(UUID sourceId) {
    return new ClaimHistoryEventRow(
        "SUBMISSION",
        Instant.parse("2026-04-22T11:26:00Z"),
        "provider-user-id",
        sourceId,
        JsonNodeFactory.instance.objectNode(),
        1L);
  }

  @Test
  @DisplayName("getTimeline uses default page size when no page size provided")
  void getTimelineUsesDefaultPageSizeWhenNoPageSizeProvided() {
    UUID claimId = UUID.randomUUID();
    ClaimHistoryEventRow row = submissionRow(claimId);
    when(claimRepository.existsById(claimId)).thenReturn(true);
    when(claimHistoryRepository.findHistory(claimId, DEFAULT_PAGE_SIZE, 0))
        .thenReturn(
            new uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimHistoryPage(
                List.of(row), 1L, 0, DEFAULT_PAGE_SIZE));

    uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimHistoryPage result =
        claimHistoryService.getTimeline(claimId, Pageable.unpaged());

    assertThat(result.getEvents()).containsExactly(row);
    verify(claimHistoryRepository).findHistory(claimId, DEFAULT_PAGE_SIZE, 0);
  }

  @Test
  @DisplayName("getTimeline passes through requested page size")
  void getTimelinePassesThroughRequestedPageSize() {
    UUID claimId = UUID.randomUUID();
    when(claimRepository.existsById(claimId)).thenReturn(true);
    when(claimHistoryRepository.findHistory(eq(claimId), eq(10), eq(0)))
        .thenReturn(
            new uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimHistoryPage(
                List.of(), 0L, 0, 10));

    uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimHistoryPage result =
        claimHistoryService.getTimeline(claimId, PageRequest.of(0, 10));

    assertThat(result.getEvents()).isEmpty();
    verify(claimHistoryRepository).findHistory(claimId, 10, 0);
  }

  @Test
  @DisplayName("getTimeline with pageable computes limit and offset")
  void getTimelineWithPageableComputesLimitAndOffset() {
    UUID claimId = UUID.randomUUID();
    when(claimRepository.existsById(claimId)).thenReturn(true);
    when(claimHistoryRepository.findHistory(eq(claimId), eq(10), eq(20)))
        .thenReturn(
            new uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimHistoryPage(
                List.of(), 0L, 2, 10));

    uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimHistoryPage result =
        claimHistoryService.getTimeline(claimId, PageRequest.of(2, 10));

    assertThat(result.getEvents()).isEmpty();
    verify(claimHistoryRepository).findHistory(claimId, 10, 20);
  }

  @Test
  @DisplayName("getTimeline throws NotFound when claim does not exist")
  void getTimelineThrowsNotFoundWhenClaimDoesNotExist() {
    UUID claimId = UUID.randomUUID();
    when(claimRepository.existsById(claimId)).thenReturn(false);

    assertThatThrownBy(() -> claimHistoryService.getTimeline(claimId, Pageable.unpaged()))
        .isInstanceOf(ClaimNotFoundException.class)
        .hasMessageContaining(claimId.toString());

    verifyNoInteractions(claimHistoryRepository);
  }

  @Test
  @DisplayName("getTimeline with page size throws NotFound when claim does not exist")
  void getTimelineWithPageSizeThrowsNotFoundWhenClaimDoesNotExist() {
    UUID claimId = UUID.randomUUID();
    when(claimRepository.existsById(claimId)).thenReturn(false);

    assertThatThrownBy(() -> claimHistoryService.getTimeline(claimId, PageRequest.of(0, 25)))
        .isInstanceOf(ClaimNotFoundException.class);

    verify(claimHistoryRepository, never()).findHistory(claimId, 25, 0);
  }
}
