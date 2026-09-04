package uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ClaimHistoryPage equality and hashCode tests")
class ClaimHistoryPageTest {

  @Test
  @DisplayName("equals and hashCode for identical content")
  void equalsAndHashCodeForIdenticalContent() {
    UUID sourceId = UUID.randomUUID();
    ObjectNode metadata = JsonNodeFactory.instance.objectNode();
    metadata.put("submission_period", "APR-2026");
    ClaimHistoryEventRow event =
        new ClaimHistoryEventRow(
            "SUBMISSION",
            Instant.parse("2026-04-22T11:26:00Z"),
            "provider-user-id",
            sourceId,
            metadata,
            1L);
    ClaimHistoryPage a = new ClaimHistoryPage(List.of(event), 1L, 0, 20);
    ClaimHistoryPage b = new ClaimHistoryPage(List.of(event), 1L, 0, 20);

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  @DisplayName("not equal when events differ")
  void notEqualWhenEventsDiffer() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    ObjectNode meta1 = JsonNodeFactory.instance.objectNode();
    meta1.put("k", "v");
    ObjectNode meta2 = JsonNodeFactory.instance.objectNode();
    meta2.put("other", "x");

    ClaimHistoryEventRow e1 =
        new ClaimHistoryEventRow(
            "SUBMISSION", Instant.parse("2026-04-22T11:26:00Z"), "user-a", id1, meta1, 1L);
    ClaimHistoryEventRow e2 =
        new ClaimHistoryEventRow("ASSESSMENT", Instant.now(), "user-b", id2, meta2, 1L);
    ClaimHistoryPage a = new ClaimHistoryPage(List.of(e1), 1L, 0, 20);
    ClaimHistoryPage b = new ClaimHistoryPage(List.of(e2), 1L, 0, 20);

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  @DisplayName("not equal when totalElements, pageNumber or pageSize differ")
  void notEqualWhenOtherFieldsDiffer() {
    UUID id = UUID.randomUUID();
    ObjectNode metadata = JsonNodeFactory.instance.objectNode();
    metadata.put("k", "v");
    ClaimHistoryEventRow event =
        new ClaimHistoryEventRow(
            "SUBMISSION", OffsetDateTime.now().toInstant(), "user", id, metadata, 1L);
    ClaimHistoryPage base = new ClaimHistoryPage(List.of(event), 1L, 0, 20);

    ClaimHistoryPage diffTotal = new ClaimHistoryPage(List.of(event), 2L, 0, 20);
    ClaimHistoryPage diffPageNumber = new ClaimHistoryPage(List.of(event), 1L, 1, 20);
    ClaimHistoryPage diffPageSize = new ClaimHistoryPage(List.of(event), 1L, 0, 10);

    assertThat(base).isNotEqualTo(diffTotal);
    assertThat(base).isNotEqualTo(diffPageNumber);
    assertThat(base).isNotEqualTo(diffPageSize);
  }

  @Test
  @DisplayName("equals is reflexive and returns false for null or other types")
  void equalsReflexiveAndDifferentClass() {
    UUID id = UUID.randomUUID();
    ObjectNode metadata = JsonNodeFactory.instance.objectNode();
    metadata.put("k", "v");
    ClaimHistoryEventRow event =
        new ClaimHistoryEventRow(
            "SUBMISSION", OffsetDateTime.now().toInstant(), "user", id, metadata, 1L);
    ClaimHistoryPage page = new ClaimHistoryPage(List.of(event), 1L, 0, 20);

    assertThat(page).isEqualTo(page);
    assertThat(page.equals(null)).isFalse();
    assertThat(page.equals("some-string")).isFalse();
  }

  @Test
  @DisplayName("totalPages: exact division, remainder, single and zero cases")
  void totalPagesCalculationCases() {
    // exact division
    ClaimHistoryPage exact = new ClaimHistoryPage(List.of(), 20L, 0, 10);
    assertThat(exact.getTotalPages()).isEqualTo(2);

    // remainder
    ClaimHistoryPage rem = new ClaimHistoryPage(List.of(), 21L, 0, 10);
    assertThat(rem.getTotalPages()).isEqualTo(3);

    // single element
    ClaimHistoryPage single = new ClaimHistoryPage(List.of(), 1L, 0, 10);
    assertThat(single.getTotalPages()).isEqualTo(1);

    // zero elements
    ClaimHistoryPage zero = new ClaimHistoryPage(List.of(), 0L, 0, 10);
    assertThat(zero.getTotalPages()).isZero();
  }

  @Test
  @DisplayName("non-positive pageSize yields zero totalPages")
  void nonPositivePageSizeYieldsZero() {
    ClaimHistoryPage zeroSize = new ClaimHistoryPage(List.of(), 10L, 0, 0);
    assertThat(zeroSize.getTotalPages()).isZero();

    ClaimHistoryPage negativeSize = new ClaimHistoryPage(List.of(), 10L, 0, -5);
    assertThat(negativeSize.getTotalPages()).isZero();
  }
}
