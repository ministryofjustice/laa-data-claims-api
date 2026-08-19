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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimHistoryRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;

@ExtendWith(MockitoExtension.class)
class ClaimHistoryServiceTest {

  private static final int DEFAULT_PAGE_SIZE = 50;

  @Mock private ClaimHistoryRepository claimHistoryRepository;
  @Mock private ClaimRepository claimRepository;

  @InjectMocks private ClaimHistoryService claimHistoryService;

  private static ClaimHistoryEventRow submissionRow(UUID sourceId) {
    return new ClaimHistoryEventRow(
        "SUBMISSION",
        Instant.parse("2026-04-22T11:26:00Z"),
        "provider-user-id",
        sourceId,
        JsonNodeFactory.instance.objectNode());
  }

  @Test
  void getTimeline_usesDefaultPageSize_whenNoPageSizeProvided() {
    UUID claimId = UUID.randomUUID();
    ClaimHistoryEventRow row = submissionRow(claimId);
    when(claimRepository.existsById(claimId)).thenReturn(true);
    when(claimHistoryRepository.findHistory(claimId, DEFAULT_PAGE_SIZE)).thenReturn(List.of(row));

    List<ClaimHistoryEventRow> result = claimHistoryService.getTimeline(claimId);

    assertThat(result).containsExactly(row);
    verify(claimHistoryRepository).findHistory(claimId, DEFAULT_PAGE_SIZE);
  }

  @Test
  void getTimeline_passesThroughRequestedPageSize() {
    UUID claimId = UUID.randomUUID();
    when(claimRepository.existsById(claimId)).thenReturn(true);
    when(claimHistoryRepository.findHistory(eq(claimId), eq(10))).thenReturn(List.of());

    List<ClaimHistoryEventRow> result = claimHistoryService.getTimeline(claimId, 10);

    assertThat(result).isEmpty();
    verify(claimHistoryRepository).findHistory(claimId, 10);
  }

  @Test
  void getTimeline_throwsNotFound_whenClaimDoesNotExist() {
    UUID claimId = UUID.randomUUID();
    when(claimRepository.existsById(claimId)).thenReturn(false);

    assertThatThrownBy(() -> claimHistoryService.getTimeline(claimId))
        .isInstanceOf(ClaimNotFoundException.class)
        .hasMessageContaining(claimId.toString());

    verifyNoInteractions(claimHistoryRepository);
  }

  @Test
  void getTimeline_withPageSize_throwsNotFound_whenClaimDoesNotExist() {
    UUID claimId = UUID.randomUUID();
    when(claimRepository.existsById(claimId)).thenReturn(false);

    assertThatThrownBy(() -> claimHistoryService.getTimeline(claimId, 25))
        .isInstanceOf(ClaimNotFoundException.class);

    verify(claimHistoryRepository, never()).findHistory(claimId, 25);
  }
}
