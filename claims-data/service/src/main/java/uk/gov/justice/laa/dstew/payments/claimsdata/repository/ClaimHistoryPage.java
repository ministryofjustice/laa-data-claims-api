package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.Objects;
import lombok.Getter;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;

/** Simple page holder returned by ClaimHistoryRepository to carry events and total count. */
@Getter
public class ClaimHistoryPage {

  private final List<ClaimHistoryEventRow> events;
  private final long totalElements;
  private final int pageNumber;
  private final int pageSize;

  /**
   * Create a new ClaimHistoryPage.
   *
   * @param events list of timeline event rows for the current page
   * @param totalElements total number of matching events across all pages
   * @param pageNumber zero-based page index
   * @param pageSize size of the page
   */
  public ClaimHistoryPage(
      List<ClaimHistoryEventRow> events, long totalElements, int pageNumber, int pageSize) {
    this.events = events;
    this.totalElements = totalElements;
    this.pageNumber = pageNumber;
    this.pageSize = pageSize;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ClaimHistoryPage that = (ClaimHistoryPage) o;
    return totalElements == that.totalElements
        && pageNumber == that.pageNumber
        && pageSize == that.pageSize
        && Objects.equals(events, that.events);
  }

  @Override
  public int hashCode() {
    return Objects.hash(events, totalElements, pageNumber, pageSize);
  }
}
