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
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
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

  // ----------------------------------------------------------------------------------------------
  // AMENDMENT events (DSTEW-1815 — FSP history indicators derived from amendment-linked data)
  // ----------------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "Maps a claim_amendment into an AMENDMENT event exposing requester, reason and changes")
  void mapsAmendmentEvent_withRequesterReasonAndChanges() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    // A non-pricing amendment: a provider-requested change with no linked calculated_fee_detail.
    persistAmendment(
        amendmentId,
        "{\"schema_version\":1,\"changes\":[{\"field_identifier\":\"claim.feeCode\","
            + "\"change_source\":\"Requested\",\"before\":\"OLD\",\"after\":\"NEW\"}]}");

    ClaimHistoryEventRow event = findAmendmentEvent(amendmentId);

    assertThat(event.eventType()).isEqualTo("AMENDMENT");
    assertThat(event.actorId()).isEqualTo(USER_ID);
    assertThat(event.metadata().get("requested_by_code").asText()).isEqualTo("PROVIDER");
    assertThat(event.metadata().get("amendment_reason_code").asText()).isEqualTo("PROVIDER_ERROR");
    // No linked fee row: repricing did not run, so no fabricated pricing/escape metadata.
    assertThat(event.metadata().get("pricing_recalculated").asBoolean()).isFalse();
    assertThat(event.metadata().get("price_changed").asBoolean()).isFalse();
    assertThat(event.metadata().get("escape_case_logged").asBoolean()).isFalse();
    assertThat(event.metadata().get("changes").isArray()).isTrue();
    assertThat(event.metadata().get("changes")).hasSize(1);
    assertThat(event.metadata().get("changes").get(0).get("field_identifier").asText())
        .isEqualTo("claim.feeCode");
  }

  @Test
  @DisplayName(
      "Pricing amendment with a monetary change: pricing_recalculated & price_changed true")
  void pricingAmendment_priceChanged() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    persistAmendment(amendmentId, emptyDiff());
    linkCalculatedFeeDetail(amendmentId, true, false);

    ClaimHistoryEventRow event = findAmendmentEvent(amendmentId);

    assertThat(event.metadata().get("pricing_recalculated").asBoolean()).isTrue();
    assertThat(event.metadata().get("price_changed").asBoolean()).isTrue();
    assertThat(event.metadata().get("escape_case_logged").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("Pricing amendment with no monetary change: pricing_recalculated true, price false")
  void pricingAmendment_priceUnchanged() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    persistAmendment(amendmentId, emptyDiff());
    linkCalculatedFeeDetail(amendmentId, false, false);

    ClaimHistoryEventRow event = findAmendmentEvent(amendmentId);

    assertThat(event.metadata().get("pricing_recalculated").asBoolean()).isTrue();
    assertThat(event.metadata().get("price_changed").asBoolean()).isFalse();
  }

  @Test
  @DisplayName(
      "Amendment that caused the escape transition (false -> true): escape_case_logged true")
  void amendmentCausedEscapeTransition_logsEscape() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    // FSP-sourced fee.escapeCaseFlag transition from false to true is the transition source.
    persistAmendment(amendmentId, escapeDiff(false, true));
    linkCalculatedFeeDetail(amendmentId, true, true);

    ClaimHistoryEventRow event = findAmendmentEvent(amendmentId);

    assertThat(event.metadata().get("escape_case_logged").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("Amendment on an already-escaped claim (no transition): escape_case_logged false")
  void alreadyEscapedClaim_doesNotLogEscape() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    // No FSP escapeCaseFlag transition entry in the diff (an already-escaped claim never records
    // one), even though the linked fee still carries escape_case_flag = true. The indicator must be
    // derived from the transition, not the state.
    persistAmendment(
        amendmentId,
        "{\"schema_version\":1,\"changes\":[{\"field_identifier\":\"fee.totalAmount\","
            + "\"change_source\":\"FSP\",\"before\":\"100.00\",\"after\":\"120.00\"}]}");
    linkCalculatedFeeDetail(amendmentId, true, true);

    ClaimHistoryEventRow event = findAmendmentEvent(amendmentId);

    assertThat(event.metadata().get("pricing_recalculated").asBoolean()).isTrue();
    assertThat(event.metadata().get("escape_case_logged").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("Escape de-escalation (true -> false) is not logged as an escape transition")
  void escapeDeEscalation_doesNotLogEscape() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    persistAmendment(amendmentId, escapeDiff(true, false));
    linkCalculatedFeeDetail(amendmentId, true, false);

    ClaimHistoryEventRow event = findAmendmentEvent(amendmentId);

    assertThat(event.metadata().get("escape_case_logged").asBoolean()).isFalse();
  }

  @Test
  @DisplayName(
      "Multiple amendments flipping escape back and forth: each event reflects its own transition")
  void multipleAmendments_escapeFlipFlop_resolvedPerAmendment() {
    // Three amendments on the same claim, each with its own FSP escapeCaseFlag transition and its
    // own linked fee row. The read model must resolve each event independently from that
    // amendment's diff — never aggregating across amendments or reading current claim state.
    UUID escalateId = Uuid7.timeBasedUuid();
    UUID deEscalateId = Uuid7.timeBasedUuid();
    UUID reEscalateId = Uuid7.timeBasedUuid();

    // 1st amendment: escalates (false -> true) with a monetary change.
    persistAmendment(escalateId, escapeDiff(false, true));
    linkCalculatedFeeDetail(escalateId, true, true);
    // 2nd amendment: de-escalates (true -> false) with a monetary change.
    persistAmendment(deEscalateId, escapeDiff(true, false));
    linkCalculatedFeeDetail(deEscalateId, true, false);
    // 3rd amendment: re-escalates (false -> true) with NO monetary change.
    persistAmendment(reEscalateId, escapeDiff(false, true));
    linkCalculatedFeeDetail(reEscalateId, false, true);

    ClaimHistoryEventRow escalate = findAmendmentEvent(escalateId);
    ClaimHistoryEventRow deEscalate = findAmendmentEvent(deEscalateId);
    ClaimHistoryEventRow reEscalate = findAmendmentEvent(reEscalateId);

    // Escape is logged only on the amendments that caused a false -> true transition.
    assertThat(escalate.metadata().get("escape_case_logged").asBoolean()).isTrue();
    assertThat(deEscalate.metadata().get("escape_case_logged").asBoolean()).isFalse();
    assertThat(reEscalate.metadata().get("escape_case_logged").asBoolean()).isTrue();

    // price_changed is likewise independent per amendment (from each linked fee row).
    assertThat(escalate.metadata().get("price_changed").asBoolean()).isTrue();
    assertThat(deEscalate.metadata().get("price_changed").asBoolean()).isTrue();
    assertThat(reEscalate.metadata().get("price_changed").asBoolean()).isFalse();

    // All three ran repricing (each has a linked calculated_fee_detail).
    assertThat(escalate.metadata().get("pricing_recalculated").asBoolean()).isTrue();
    assertThat(deEscalate.metadata().get("pricing_recalculated").asBoolean()).isTrue();
    assertThat(reEscalate.metadata().get("pricing_recalculated").asBoolean()).isTrue();
  }

  @Test
  @DisplayName(
      "Multiple amendments: price_changed is resolved per amendment (changed, unchanged, changed)")
  void multipleAmendments_priceChangedMix_resolvedPerAmendment() {
    // Three repricing amendments, none causing an escape transition, with differing monetary
    // outcomes. Each event's price_changed must reflect only its own linked fee row.
    UUID firstChanged = Uuid7.timeBasedUuid();
    UUID secondUnchanged = Uuid7.timeBasedUuid();
    UUID thirdChanged = Uuid7.timeBasedUuid();

    persistAmendment(firstChanged, emptyDiff());
    linkCalculatedFeeDetail(firstChanged, true, false);
    persistAmendment(secondUnchanged, emptyDiff());
    linkCalculatedFeeDetail(secondUnchanged, false, false);
    persistAmendment(thirdChanged, emptyDiff());
    linkCalculatedFeeDetail(thirdChanged, true, false);

    ClaimHistoryEventRow first = findAmendmentEvent(firstChanged);
    ClaimHistoryEventRow second = findAmendmentEvent(secondUnchanged);
    ClaimHistoryEventRow third = findAmendmentEvent(thirdChanged);

    assertThat(first.metadata().get("price_changed").asBoolean()).isTrue();
    assertThat(second.metadata().get("price_changed").asBoolean()).isFalse();
    assertThat(third.metadata().get("price_changed").asBoolean()).isTrue();

    // No escape transition on any of them, and all ran repricing.
    for (ClaimHistoryEventRow event : List.of(first, second, third)) {
      assertThat(event.metadata().get("pricing_recalculated").asBoolean()).isTrue();
      assertThat(event.metadata().get("escape_case_logged").asBoolean()).isFalse();
    }
  }

  @Test
  @DisplayName(
      "Provider-requested change with repricing but no monetary change: P=true, C=false, E=false")
  void requestedChangeWithRepricing_noPriceChange_noEscape() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    // A provider-requested (non-FSP) field change; repricing ran but produced the same total and no
    // escape. Validates all three indicators plus the REQUESTED change_source passthrough.
    persistAmendment(
        amendmentId,
        "{\"schema_version\":1,\"changes\":[{\"field_identifier\":\"claim.caseReferenceNumber\","
            + "\"change_source\":\"Requested\",\"before\":\"REF-1\",\"after\":\"REF-2\"}]}");
    linkCalculatedFeeDetail(amendmentId, false, false);

    ClaimHistoryEventRow event = findAmendmentEvent(amendmentId);

    assertThat(event.metadata().get("pricing_recalculated").asBoolean()).isTrue();
    assertThat(event.metadata().get("price_changed").asBoolean()).isFalse();
    assertThat(event.metadata().get("escape_case_logged").asBoolean()).isFalse();
    assertThat(event.metadata().get("changes")).hasSize(1);
    assertThat(event.metadata().get("changes").get(0).get("change_source").asText())
        .isEqualTo("Requested");
  }

  @Test
  @DisplayName(
      "Rich amendment with REQUESTED and FSP changes: all indicators true, changes preserved")
  void richAmendment_requestedAndFspChanges_allIndicatorsTrue() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    // A provider-requested field change plus FSP consequences: a total change and an escape
    // transition. Repricing ran, price changed, and the escape transition is logged.
    persistAmendment(
        amendmentId,
        "{\"schema_version\":1,\"changes\":["
            + "{\"field_identifier\":\"claim.netProfitCostsAmount\",\"change_source\":\"Requested\","
            + "\"before\":\"100.00\",\"after\":\"150.00\"},"
            + "{\"field_identifier\":\"fee.totalAmount\",\"change_source\":\"FSP\","
            + "\"before\":\"100.00\",\"after\":\"180.00\"},"
            + "{\"field_identifier\":\"fee.escapeCaseFlag\",\"change_source\":\"FSP\","
            + "\"before\":false,\"after\":true}]}");
    linkCalculatedFeeDetail(amendmentId, true, true);

    ClaimHistoryEventRow event = findAmendmentEvent(amendmentId);

    assertThat(event.metadata().get("pricing_recalculated").asBoolean()).isTrue();
    assertThat(event.metadata().get("price_changed").asBoolean()).isTrue();
    assertThat(event.metadata().get("escape_case_logged").asBoolean()).isTrue();
    assertThat(event.metadata().get("changes")).hasSize(3);
  }

  private static String emptyDiff() {
    return "{\"schema_version\":1,\"changes\":[]}";
  }

  @Test
  @DisplayName("A claim with no amendment rows produces no AMENDMENT event (failed amendments)")
  void noAmendmentRows_produceNoAmendmentEvent() {
    // A failed amendment never persists a claim_amendment row, so no AMENDMENT event can appear.
    List<ClaimHistoryEventRow> events = claimHistoryRepository.findHistory(CLAIM_1_ID, 50);

    assertThat(events).extracting(ClaimHistoryEventRow::eventType).doesNotContain("AMENDMENT");
  }

  private static String escapeDiff(boolean before, boolean after) {
    return "{\"schema_version\":1,\"changes\":[{\"field_identifier\":\"fee.escapeCaseFlag\","
        + "\"change_source\":\"FSP\",\"before\":"
        + before
        + ",\"after\":"
        + after
        + "}]}";
  }

  private ClaimAmendment persistAmendment(UUID id, String diffJson) {
    ClaimAmendment amendment =
        ClaimAmendment.builder()
            .id(id)
            .claim(claimRepository.getReferenceById(CLAIM_1_ID))
            .requestedByCode("PROVIDER")
            .amendmentReasonCode("PROVIDER_ERROR")
            .beforeState("{}")
            .requestPayload("{}")
            .diff(diffJson)
            .createdByUserId(USER_ID)
            .createdOn(OffsetDateTime.now())
            .build();
    claimAmendmentRepository.save(amendment);
    claimAmendmentRepository.flush();
    return amendment;
  }

  private void linkCalculatedFeeDetail(UUID amendmentId, boolean priceChanged, boolean escapeFlag) {
    CalculatedFeeDetail fee =
        CalculatedFeeDetail.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claimRepository.getReferenceById(CLAIM_1_ID))
            .claimSummaryFee(claimSummaryFeeRepository.getReferenceById(CLAIM_1_SUMMARY_FEE_ID))
            .claimAmendment(claimAmendmentRepository.getReferenceById(amendmentId))
            .isPriceChanged(priceChanged)
            .escapeCaseFlag(escapeFlag)
            .totalAmount(new BigDecimal("120.00"))
            .createdByUserId(USER_ID)
            .createdOn(OffsetDateTime.now())
            .build();
    calculatedFeeDetailRepository.save(fee);
    calculatedFeeDetailRepository.flush();
  }

  private ClaimHistoryEventRow findAmendmentEvent(UUID amendmentId) {
    return claimHistoryRepository.findHistory(CLAIM_1_ID, 50).stream()
        .filter(event -> amendmentId.equals(event.sourceId()))
        .findFirst()
        .orElseThrow();
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
