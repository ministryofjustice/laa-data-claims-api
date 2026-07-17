package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_SUMMARY_FEE_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getAssessmentBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("JdbcClaimHistoryRepository Integration Test")
class JdbcClaimHistoryRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired private ClaimHistoryRepository claimHistoryRepository;
  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void setup() {
    seedClaimsData();
    claimRepository.flush();
  }

  @Test
  @DisplayName("Maps a claim's parent submission into a SUBMISSION event")
  void mapsSubmissionEvent() {
    List<ClaimHistoryEventRow> events = claimHistoryRepository.findHistory(CLAIM_1_ID, 50);

    assertThat(events).hasSize(1);
    ClaimHistoryEventRow event = events.getFirst();
    assertThat(event.eventType()).isEqualTo("SUBMISSION");
    assertThat(event.sourceId()).isEqualTo(CLAIM_1_ID);
    assertThat(event.actorId()).isEqualTo(USER_ID);
    assertThat(event.eventTimestamp()).isNotNull();
    assertThat(event.metadata().get("submission_period").asText()).isEqualTo("JAN-2025");
    assertThat(event.metadata().get("office_account_number").asText())
        .isEqualTo(OFFICE_ACCOUNT_NUMBER_1);
    assertThat(event.metadata().get("area_of_law").asText()).isEqualTo("LEGAL_HELP");
  }

  @Test
  @DisplayName("Falls back to SYSTEM when the source row holds no user id")
  void fallsBackToSystemActor() {
    // created_by_user_id is NOT NULL in the schema; relax it for this rolled-back transaction so we
    // can prove the COALESCE(..., 'SYSTEM') fallback against a genuinely null user id.
    jdbcClient
        .sql("ALTER TABLE claims.claim ALTER COLUMN created_by_user_id DROP NOT NULL")
        .update();
    jdbcClient
        .sql("UPDATE claims.claim SET created_by_user_id = NULL WHERE id = :id")
        .param("id", CLAIM_1_ID)
        .update();

    List<ClaimHistoryEventRow> events = claimHistoryRepository.findHistory(CLAIM_1_ID, 50);

    assertThat(events).hasSize(1);
    assertThat(events.getFirst().actorId()).isEqualTo("SYSTEM");
  }

  @Test
  @DisplayName("Orders same-timestamp events deterministically by source id descending")
  void ordersSameTimestampEventsBySourceIdDescending() {
    // Two assessments on the same claim. JPA auditing sets created_on on save, so we force both to
    // an identical timestamp with a raw update to genuinely exercise the same-timestamp tie-break.
    UUID idA = Uuid7.timeBasedUuid();
    UUID idB = Uuid7.timeBasedUuid();
    assessmentRepository.saveAll(
        List.of(sameTimestampAssessment(idA), sameTimestampAssessment(idB)));
    assessmentRepository.flush();

    Instant sharedTimestamp = Instant.parse("2026-04-22T11:26:00Z");
    jdbcClient
        .sql("UPDATE claims.assessment SET created_on = :ts WHERE id IN (:idA, :idB)")
        .param("ts", OffsetDateTime.ofInstant(sharedTimestamp, ZoneOffset.UTC))
        .param("idA", idA)
        .param("idB", idB)
        .update();

    List<ClaimHistoryEventRow> events = claimHistoryRepository.findHistory(CLAIM_1_ID, 50);

    // The full timeline is ordered by (event_timestamp DESC, source_id DESC), deterministically.
    List<ClaimHistoryEventRow> expectedOrder =
        events.stream()
            .sorted(
                Comparator.comparing(ClaimHistoryEventRow::eventTimestamp)
                    .thenComparing(ClaimHistoryEventRow::sourceId)
                    .reversed())
            .toList();
    assertThat(events).containsExactlyElementsOf(expectedOrder);

    // The two assessments share a timestamp; the larger source_id must come first (tie-break).
    List<UUID> assessmentOrder =
        events.stream()
            .map(ClaimHistoryEventRow::sourceId)
            .filter(id -> id.equals(idA) || id.equals(idB))
            .toList();
    UUID higher = idA.compareTo(idB) > 0 ? idA : idB;
    UUID lower = idA.compareTo(idB) > 0 ? idB : idA;
    assertThat(assessmentOrder).containsExactly(higher, lower);
  }

  private Assessment sameTimestampAssessment(UUID id) {
    return getAssessmentBuilder()
        .id(id)
        .claim(claimRepository.getReferenceById(CLAIM_1_ID))
        .claimSummaryFee(claimSummaryFeeRepository.getReferenceById(CLAIM_1_SUMMARY_FEE_ID))
        .assessmentType(AssessmentType.ESCAPE_CASE_ASSESSMENT)
        .assessmentReason("Same-timestamp assessment")
        .allowedTotalVat(new BigDecimal("100.00"))
        .allowedTotalInclVat(new BigDecimal("120.00"))
        .createdOn(CREATED_ON)
        .build();
  }
}
