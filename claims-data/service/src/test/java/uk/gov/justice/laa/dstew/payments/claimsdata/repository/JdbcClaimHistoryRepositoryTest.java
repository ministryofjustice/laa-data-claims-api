package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimHistoryEventRowMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryPage;

@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcClaimHistoryRepository unit tests")
class JdbcClaimHistoryRepositoryTest {

  @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
  private JdbcClient jdbcClient;

  @Mock private ClaimHistoryEventRowMapper rowMapper;

  @InjectMocks private JdbcClaimHistoryRepository repository;

  @Test
  @DisplayName("findHistory returns empty page when no rows")
  void findHistoryReturnsEmptyPageWhenNoRows() {
    UUID claimId = UUID.randomUUID();

    when(jdbcClient
            .sql(anyString())
            .param(anyString(), any())
            .param(anyString(), any())
            .param(anyString(), any())
            .query(rowMapper)
            .list())
        .thenReturn(List.of());

    ClaimHistoryPage page = repository.findHistory(claimId, 10, 0);

    assertThat(page.getEvents()).isEmpty();
    assertThat(page.getTotalElements()).isEqualTo(0L);
    assertThat(page.getPageNumber()).isEqualTo(0);
    assertThat(page.getPageSize()).isEqualTo(10);
  }

  @Test
  @DisplayName("findHistory uses first row totalCount and computes pageNumber")
  void findHistoryUsesFirstRowTotalCountAndComputesPageNumber() {
    UUID claimId = UUID.randomUUID();

    ClaimHistoryEventRow row =
        new ClaimHistoryEventRow("SUBMISSION", Instant.now(), "u", UUID.randomUUID(), null, 37L);

    when(jdbcClient
            .sql(anyString())
            .param(anyString(), any())
            .param(anyString(), any())
            .param(anyString(), any())
            .query(rowMapper)
            .list())
        .thenReturn(List.of(row));

    int limit = 5;
    int offset = 10;
    ClaimHistoryPage page = repository.findHistory(claimId, limit, offset);

    assertThat(page.getTotalElements()).isEqualTo(37L);
    assertThat(page.getPageNumber()).isEqualTo(offset / limit);
    assertThat(page.getPageSize()).isEqualTo(limit);
  }
}
