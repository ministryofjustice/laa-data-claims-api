package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REASON_PROVIDER_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REQUESTED_BY_PROVIDER;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentOutcome;
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

  @Test
  @DisplayName("Maps an ESCAPE_CASE_ASSESSMENT row into an ASSESSMENT event with full metadata")
  void mapsEscapeCaseAssessment_toAssessmentEvent() {
    UUID assessmentId = Uuid7.timeBasedUuid();
    persistAssessment(
        assessmentId,
        AssessmentType.ESCAPE_CASE_ASSESSMENT,
        AssessmentOutcome.REDUCED_TO_FIXED_FEE,
        "Escape fee case assessment");

    ClaimHistoryEventRow event = findAssessmentEvent(assessmentId);

    assertThat(event.eventType()).isEqualTo("ASSESSMENT");
    assertThat(event.actorId()).isEqualTo(USER_ID);
    assertThat(event.eventTimestamp()).isNotNull();
    assertThat(event.metadata().get("assessment_type").asText())
        .isEqualTo("ESCAPE_CASE_ASSESSMENT");
    assertThat(event.metadata().get("assessment_outcome").asText())
        .isEqualTo("REDUCED_TO_FIXED_FEE");
    assertThat(event.metadata().get("assessment_reason").asText())
        .isEqualTo("Escape fee case assessment");
  }

  @Test
  @DisplayName("Maps a STAGE_DISBURSEMENT_ASSESSMENT row into an ASSESSMENT event")
  void mapsStageDisbursementAssessment_toAssessmentEvent() {
    UUID assessmentId = Uuid7.timeBasedUuid();
    persistAssessment(
        assessmentId,
        AssessmentType.STAGE_DISBURSEMENT_ASSESSMENT,
        AssessmentOutcome.PAID_IN_FULL,
        "Stage disbursement assessment");

    ClaimHistoryEventRow event = findAssessmentEvent(assessmentId);

    assertThat(event.eventType()).isEqualTo("ASSESSMENT");
    assertThat(event.metadata().get("assessment_type").asText())
        .isEqualTo("STAGE_DISBURSEMENT_ASSESSMENT");
    assertThat(event.metadata().get("assessment_outcome").asText()).isEqualTo("PAID_IN_FULL");
    assertThat(event.metadata().get("assessment_reason").asText())
        .isEqualTo("Stage disbursement assessment");
  }

  @Test
  @DisplayName("Maps an assessment_type = 'VOID' row into a VOID event without an outcome")
  void mapsVoidAssessment_toVoidEvent() {
    UUID assessmentId = Uuid7.timeBasedUuid();
    // A void carries no outcome; assessment_reason holds the void reason.
    persistAssessment(assessmentId, AssessmentType.VOID, null, "Voided in error");

    ClaimHistoryEventRow event = findAssessmentEvent(assessmentId);

    assertThat(event.eventType()).isEqualTo("VOID");
    assertThat(event.metadata().get("assessment_type").asText()).isEqualTo("VOID");
    assertThat(event.metadata().get("assessment_reason").asText()).isEqualTo("Voided in error");
    // VOID metadata intentionally omits the outcome key entirely.
    assertThat(event.metadata().has("assessment_outcome")).isFalse();
  }

  @Test
  @DisplayName("Maps a legacy row with a null assessment_type into an ASSESSMENT event")
  void mapsLegacyNullAssessmentType_toAssessmentEvent() {
    UUID assessmentId = Uuid7.timeBasedUuid();
    persistAssessment(
        assessmentId,
        AssessmentType.ESCAPE_CASE_ASSESSMENT,
        AssessmentOutcome.NILLED,
        "Legacy assessment");

    // assessment_type is NOT NULL in the schema; relax it for this rolled-back transaction so we
    // can
    // reproduce a genuine legacy row whose type was never populated.
    jdbcClient
        .sql("ALTER TABLE claims.assessment ALTER COLUMN assessment_type DROP NOT NULL")
        .update();
    jdbcClient
        .sql("UPDATE claims.assessment SET assessment_type = NULL WHERE id = :id")
        .param("id", assessmentId)
        .update();

    ClaimHistoryEventRow event = findAssessmentEvent(assessmentId);

    // A null type falls through the CASE to ASSESSMENT; no fabricated type value is invented.
    assertThat(event.eventType()).isEqualTo("ASSESSMENT");
    assertThat(event.metadata().get("assessment_type").isNull()).isTrue();
  }

  @Test
  @DisplayName("Retains a null assessment_outcome as an explicit JSON null (no fabricated value)")
  void retainsNullAssessmentOutcome_asJsonNull() {
    UUID assessmentId = Uuid7.timeBasedUuid();
    persistAssessment(assessmentId, AssessmentType.ESCAPE_CASE_ASSESSMENT, null, "Outcome pending");

    ClaimHistoryEventRow event = findAssessmentEvent(assessmentId);

    assertThat(event.eventType()).isEqualTo("ASSESSMENT");
    // The key is present but null - no default or placeholder is substituted.
    assertThat(event.metadata().get("assessment_outcome").isNull()).isTrue();
    assertThat(event.metadata().get("assessment_reason").asText()).isEqualTo("Outcome pending");
  }

  @Test
  @DisplayName("Interleaves assessment and void events chronologically with the submission event")
  void interleavesAssessmentAndVoidEventsChronologicallyWithSubmission() {
    Instant submissionTimestamp =
        claimHistoryRepository.findHistory(CLAIM_1_ID, 50).getFirst().eventTimestamp();

    UUID earlierAssessmentId = Uuid7.timeBasedUuid();
    UUID laterVoidId = Uuid7.timeBasedUuid();
    persistAssessment(
        earlierAssessmentId,
        AssessmentType.ESCAPE_CASE_ASSESSMENT,
        AssessmentOutcome.PAID_IN_FULL,
        "Before submission");
    persistAssessment(laterVoidId, AssessmentType.VOID, null, "After submission");

    // Position the assessment before, and the void after, the submission event (created_on is set
    // by
    // @CreationTimestamp on insert, so force deterministic timestamps with a raw update).
    forceCreatedOn(earlierAssessmentId, submissionTimestamp.minusSeconds(60));
    forceCreatedOn(laterVoidId, submissionTimestamp.plusSeconds(60));

    List<ClaimHistoryEventRow> events = claimHistoryRepository.findHistory(CLAIM_1_ID, 50);

    // Newest first: VOID (after) -> SUBMISSION -> ASSESSMENT (before).
    assertThat(events).hasSize(3);
    assertThat(events)
        .extracting(ClaimHistoryEventRow::eventType)
        .containsExactly("VOID", "SUBMISSION", "ASSESSMENT");
    assertThat(events)
        .extracting(ClaimHistoryEventRow::sourceId)
        .containsExactly(laterVoidId, CLAIM_1_ID, earlierAssessmentId);
  }

  @Test
  @DisplayName("Returns no assessment or void events when the claim has no assessment rows")
  void returnsNoAssessmentOrVoidEvents_whenClaimHasNoAssessments() {
    List<ClaimHistoryEventRow> events = claimHistoryRepository.findHistory(CLAIM_1_ID, 50);

    assertThat(events)
        .extracting(ClaimHistoryEventRow::eventType)
        .doesNotContain("ASSESSMENT", "VOID");
  }

  // ---------- AMENDMENT events (DSTEW-1813 / DSTEW-1814) ----------

  @Test
  @DisplayName(
      "Maps a single claim_amendment row into an AMENDMENT event with metadata and changes")
  void mapsSingleAmendmentToAmendmentEvent() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    persistAmendment(
        amendmentId,
        diff(change("client_surname", "\"Smyth\"", "\"Smith\"", "REQUESTED")),
        Instant.parse("2026-05-02T09:14:00Z"));

    ClaimHistoryEventRow event = findAmendmentEvent(amendmentId);

    assertThat(event.eventType()).isEqualTo("AMENDMENT");
    assertThat(event.sourceId()).isEqualTo(amendmentId);
    assertThat(event.actorId()).isEqualTo(USER_ID);
    assertThat(event.eventTimestamp()).isEqualTo(Instant.parse("2026-05-02T09:14:00Z"));
    assertThat(event.metadata().get("requested_by_code").asText()).isEqualTo(REQUESTED_BY_PROVIDER);
    assertThat(event.metadata().get("amendment_reason_code").asText())
        .isEqualTo(REASON_PROVIDER_ERROR);

    var changes = event.metadata().get("changes");
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).get("field_identifier").asText()).isEqualTo("client_surname");
    assertThat(changes.get(0).get("before").asText()).isEqualTo("Smyth");
    assertThat(changes.get(0).get("after").asText()).isEqualTo("Smith");
    assertThat(changes.get(0).get("change_source").asText()).isEqualTo("REQUESTED");
  }

  @Test
  @DisplayName("Distinguishes REQUESTED changes from FSP consequences in the changes list")
  void distinguishesRequestedAndFspChanges() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    persistAmendment(
        amendmentId,
        diff(
            change("client_surname", "\"Smyth\"", "\"Smith\"", "REQUESTED"),
            change("calculated_fee_detail.total_amount", "\"100.00\"", "\"125.00\"", "FSP")),
        Instant.parse("2026-05-02T09:14:00Z"));

    ClaimHistoryEventRow event = findAmendmentEvent(amendmentId);

    var changes = event.metadata().get("changes");
    assertThat(changes).hasSize(2);
    assertThat(changes.get(0).get("change_source").asText()).isEqualTo("REQUESTED");
    assertThat(changes.get(1).get("change_source").asText()).isEqualTo("FSP");
  }

  @Test
  @DisplayName("Retains a cleared client-2 surname as an explicit JSON null after value")
  void retainsClearedClient2SurnameAsExplicitNull() {
    // Mirrors "patch client 2 name to null": a provider-requested clear of client.client2Surname
    // must surface in history as before=<previous value>, after=explicit JSON null (a cleared
    // value, distinguishable from an absent key). change_source is REQUESTED (provider-driven).
    UUID amendmentId = Uuid7.timeBasedUuid();
    persistAmendment(
        amendmentId,
        diff(change("client.client2Surname", "\"Bloggs\"", "null", "REQUESTED")),
        Instant.parse("2026-05-02T09:14:00Z"));

    ClaimHistoryEventRow event = findAmendmentEvent(amendmentId);

    var change = event.metadata().get("changes").get(0);
    assertThat(change.get("field_identifier").asText()).isEqualTo("client.client2Surname");
    assertThat(change.get("change_source").asText()).isEqualTo("REQUESTED");
    // before carries the previous value...
    assertThat(change.get("before").asText()).isEqualTo("Bloggs");
    // ...and after is an explicit JSON null: the key is present and null, not omitted.
    assertThat(change.has("after")).isTrue();
    assertThat(change.get("after").isNull()).isTrue();
  }

  @Test
  @DisplayName("The AMENDMENT metadata never exposes the raw request payload or before-state")
  void amendmentMetadataOmitsRawPayloadAndBeforeState() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    persistAmendment(
        amendmentId,
        diff(change("client_surname", "\"Smyth\"", "\"Smith\"", "REQUESTED")),
        Instant.parse("2026-05-02T09:14:00Z"));

    ClaimHistoryEventRow event = findAmendmentEvent(amendmentId);

    assertThat(event.metadata().has("request_payload")).isFalse();
    assertThat(event.metadata().has("before_state")).isFalse();
    assertThat(event.metadata().has("beforeState")).isFalse();
  }

  @Test
  @DisplayName("Returns each amendment as its own event in reverse-chronological order")
  void returnsEachAmendmentAsOwnEventInChronologicalOrder() {
    UUID earlierAmendmentId = Uuid7.timeBasedUuid();
    UUID laterAmendmentId = Uuid7.timeBasedUuid();
    persistAmendment(
        earlierAmendmentId,
        diff(change("client_surname", "\"Smyth\"", "\"Smith\"", "REQUESTED")),
        Instant.parse("2026-05-02T09:14:00Z"));
    persistAmendment(
        laterAmendmentId,
        diff(change("fee_code", "\"OLD\"", "\"NEW\"", "REQUESTED")),
        Instant.parse("2026-05-03T10:00:00Z"));

    List<ClaimHistoryEventRow> amendments =
        claimHistoryRepository.findHistory(CLAIM_1_ID, 50).stream()
            .filter(event -> "AMENDMENT".equals(event.eventType()))
            .toList();

    // Newest amendment first, so the latest amendment is unambiguously derivable for the banner.
    assertThat(amendments)
        .extracting(ClaimHistoryEventRow::sourceId)
        .containsExactly(laterAmendmentId, earlierAmendmentId);
  }

  @Test
  @DisplayName("Orders same-timestamp amendments deterministically by source id descending")
  void ordersSameTimestampAmendmentsBySourceIdDescending() {
    UUID idA = Uuid7.timeBasedUuid();
    UUID idB = Uuid7.timeBasedUuid();
    Instant shared = Instant.parse("2026-05-02T09:14:00Z");
    persistAmendment(idA, diff(change("client_surname", "\"A\"", "\"B\"", "REQUESTED")), shared);
    persistAmendment(idB, diff(change("fee_code", "\"A\"", "\"B\"", "REQUESTED")), shared);

    List<UUID> amendmentOrder =
        claimHistoryRepository.findHistory(CLAIM_1_ID, 50).stream()
            .filter(event -> "AMENDMENT".equals(event.eventType()))
            .map(ClaimHistoryEventRow::sourceId)
            .toList();

    UUID higher = idA.compareTo(idB) > 0 ? idA : idB;
    UUID lower = idA.compareTo(idB) > 0 ? idB : idA;
    assertThat(amendmentOrder).containsExactly(higher, lower);
  }

  @Test
  @DisplayName("Interleaves an amendment chronologically with submission and assessment events")
  void interleavesAmendmentWithSubmissionAndAssessment() {
    Instant submissionTimestamp =
        claimHistoryRepository.findHistory(CLAIM_1_ID, 50).getFirst().eventTimestamp();

    UUID earlierAssessmentId = Uuid7.timeBasedUuid();
    UUID laterAmendmentId = Uuid7.timeBasedUuid();
    persistAssessment(
        earlierAssessmentId,
        AssessmentType.ESCAPE_CASE_ASSESSMENT,
        AssessmentOutcome.PAID_IN_FULL,
        "Before submission");
    forceCreatedOn(earlierAssessmentId, submissionTimestamp.minusSeconds(60));
    persistAmendment(
        laterAmendmentId,
        diff(change("client_surname", "\"Smyth\"", "\"Smith\"", "REQUESTED")),
        submissionTimestamp.plusSeconds(60));

    List<ClaimHistoryEventRow> events = claimHistoryRepository.findHistory(CLAIM_1_ID, 50);

    // Newest first: AMENDMENT (after) -> SUBMISSION -> ASSESSMENT (before).
    assertThat(events).hasSize(3);
    assertThat(events)
        .extracting(ClaimHistoryEventRow::eventType)
        .containsExactly("AMENDMENT", "SUBMISSION", "ASSESSMENT");
    assertThat(events)
        .extracting(ClaimHistoryEventRow::sourceId)
        .containsExactly(laterAmendmentId, CLAIM_1_ID, earlierAssessmentId);
  }

  @Test
  @DisplayName("Returns no AMENDMENT event when the claim has no claim_amendment row")
  void returnsNoAmendmentEventWhenClaimHasNoAmendment() {
    // A failed/rejected attempt persists no claim_amendment row, so it never appears (AC4).
    List<ClaimHistoryEventRow> events = claimHistoryRepository.findHistory(CLAIM_1_ID, 50);

    assertThat(events).extracting(ClaimHistoryEventRow::eventType).doesNotContain("AMENDMENT");
  }

  private ClaimHistoryEventRow findAmendmentEvent(UUID amendmentId) {
    return claimHistoryRepository.findHistory(CLAIM_1_ID, 50).stream()
        .filter(event -> amendmentId.equals(event.sourceId()))
        .findFirst()
        .orElseThrow();
  }

  private void persistAmendment(UUID id, String diffJson, Instant createdOn) {
    claimAmendmentRepository.save(
        ClaimAmendment.builder()
            .id(id)
            .claim(claimRepository.getReferenceById(CLAIM_1_ID))
            .requestedByCode(REQUESTED_BY_PROVIDER)
            .amendmentReasonCode(REASON_PROVIDER_ERROR)
            .beforeState("{}")
            .requestPayload("{}")
            .diff(diffJson)
            .createdByUserId(USER_ID)
            .createdOn(OffsetDateTime.ofInstant(createdOn, ZoneOffset.UTC))
            .build());
    claimAmendmentRepository.flush();
  }

  private static String diff(String... changeObjects) {
    return "{\"schema_version\":1,\"changes\":[" + String.join(",", changeObjects) + "]}";
  }

  private static String change(String field, String beforeJson, String afterJson, String source) {
    return "{\"field_identifier\":\""
        + field
        + "\",\"before\":"
        + beforeJson
        + ",\"after\":"
        + afterJson
        + ",\"change_source\":\""
        + source
        + "\"}";
  }

  private ClaimHistoryEventRow findAssessmentEvent(UUID assessmentId) {
    return claimHistoryRepository.findHistory(CLAIM_1_ID, 50).stream()
        .filter(event -> assessmentId.equals(event.sourceId()))
        .findFirst()
        .orElseThrow();
  }

  private void persistAssessment(
      UUID id, AssessmentType type, AssessmentOutcome outcome, String reason) {
    assessmentRepository.save(
        getAssessmentBuilder()
            .id(id)
            .claim(claimRepository.getReferenceById(CLAIM_1_ID))
            .claimSummaryFee(claimSummaryFeeRepository.getReferenceById(CLAIM_1_SUMMARY_FEE_ID))
            .assessmentType(type)
            .assessmentOutcome(outcome)
            .assessmentReason(reason)
            .allowedTotalVat(new BigDecimal("100.00"))
            .allowedTotalInclVat(new BigDecimal("120.00"))
            .build());
    assessmentRepository.flush();
  }

  private void forceCreatedOn(UUID assessmentId, Instant createdOn) {
    jdbcClient
        .sql("UPDATE claims.assessment SET created_on = :ts WHERE id = :id")
        .param("ts", OffsetDateTime.ofInstant(createdOn, ZoneOffset.UTC))
        .param("id", assessmentId)
        .update();
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
