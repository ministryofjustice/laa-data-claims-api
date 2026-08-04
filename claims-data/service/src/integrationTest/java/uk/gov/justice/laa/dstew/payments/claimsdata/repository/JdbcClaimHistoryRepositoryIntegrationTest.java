package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REASON_PROVIDER_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REQUESTED_BY_PROVIDER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_SUMMARY_FEE_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("JdbcClaimHistoryRepository Integration Test")
class JdbcClaimHistoryRepositoryIntegrationTest extends AbstractIntegrationTest {

  private static final int HISTORY_LIMIT = 50;
  private static final Instant AMENDMENT_TIMESTAMP = Instant.parse("2026-05-02T09:14:00Z");

  // Event types.
  private static final String SUBMISSION = "SUBMISSION";
  private static final String ASSESSMENT = "ASSESSMENT";
  private static final String VOID = "VOID";
  private static final String AMENDMENT = "AMENDMENT";

  // Metadata keys.
  private static final String KEY_ASSESSMENT_TYPE = "assessment_type";
  private static final String KEY_ASSESSMENT_OUTCOME = "assessment_outcome";
  private static final String KEY_ASSESSMENT_REASON = "assessment_reason";
  private static final String KEY_CHANGES = "changes";
  private static final String KEY_CHANGE_SOURCE = "change_source";
  private static final String KEY_FIELD_IDENTIFIER = "field_identifier";
  private static final String KEY_PRICING_RECALCULATED = "pricing_recalculated";
  private static final String KEY_PRICE_CHANGED = "price_changed";
  private static final String KEY_ESCAPE_CASE_LOGGED = "escape_case_logged";

  // Change sources.
  private static final String SOURCE_REQUESTED = "REQUESTED";
  private static final String SOURCE_FSP = "FSP";

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
    List<ClaimHistoryEventRow> events = findHistory();

    assertThat(events).hasSize(1);
    ClaimHistoryEventRow event = events.getFirst();
    assertThat(event.eventType()).isEqualTo(SUBMISSION);
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

    List<ClaimHistoryEventRow> events = findHistory();

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

    List<ClaimHistoryEventRow> events = findHistory();

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
  void mapsEscapeCaseAssessmentToAssessmentEvent() {
    UUID assessmentId = Uuid7.timeBasedUuid();
    persistAssessment(
        assessmentId,
        AssessmentType.ESCAPE_CASE_ASSESSMENT,
        AssessmentOutcome.REDUCED_TO_FIXED_FEE,
        "Escape fee case assessment");

    ClaimHistoryEventRow event = findEvent(assessmentId);

    assertThat(event.eventType()).isEqualTo(ASSESSMENT);
    assertThat(event.actorId()).isEqualTo(USER_ID);
    assertThat(event.eventTimestamp()).isNotNull();
    assertThat(event.metadata().get(KEY_ASSESSMENT_TYPE).asText())
        .isEqualTo("ESCAPE_CASE_ASSESSMENT");
    assertThat(event.metadata().get(KEY_ASSESSMENT_OUTCOME).asText())
        .isEqualTo("REDUCED_TO_FIXED_FEE");
    assertThat(event.metadata().get(KEY_ASSESSMENT_REASON).asText())
        .isEqualTo("Escape fee case assessment");
  }

  @Test
  @DisplayName("Maps a STAGE_DISBURSEMENT_ASSESSMENT row into an ASSESSMENT event")
  void mapsStageDisbursementAssessmentToAssessmentEvent() {
    UUID assessmentId = Uuid7.timeBasedUuid();
    persistAssessment(
        assessmentId,
        AssessmentType.STAGE_DISBURSEMENT_ASSESSMENT,
        AssessmentOutcome.PAID_IN_FULL,
        "Stage disbursement assessment");

    ClaimHistoryEventRow event = findEvent(assessmentId);

    assertThat(event.eventType()).isEqualTo(ASSESSMENT);
    assertThat(event.metadata().get(KEY_ASSESSMENT_TYPE).asText())
        .isEqualTo("STAGE_DISBURSEMENT_ASSESSMENT");
    assertThat(event.metadata().get(KEY_ASSESSMENT_OUTCOME).asText()).isEqualTo("PAID_IN_FULL");
    assertThat(event.metadata().get(KEY_ASSESSMENT_REASON).asText())
        .isEqualTo("Stage disbursement assessment");
  }

  @Test
  @DisplayName("Maps an assessment_type = 'VOID' row into a VOID event without an outcome")
  void mapsVoidAssessmentToVoidEvent() {
    UUID assessmentId = Uuid7.timeBasedUuid();
    // A void carries no outcome; assessment_reason holds the void reason.
    persistAssessment(assessmentId, AssessmentType.VOID, null, "Voided in error");

    ClaimHistoryEventRow event = findEvent(assessmentId);

    assertThat(event.eventType()).isEqualTo(VOID);
    assertThat(event.metadata().get(KEY_ASSESSMENT_TYPE).asText()).isEqualTo(VOID);
    assertThat(event.metadata().get(KEY_ASSESSMENT_REASON).asText()).isEqualTo("Voided in error");
    // VOID metadata intentionally omits the outcome key entirely.
    assertThat(event.metadata().has(KEY_ASSESSMENT_OUTCOME)).isFalse();
  }

  @Test
  @DisplayName("Maps a legacy row with a null assessment_type into an ASSESSMENT event")
  void mapsLegacyNullAssessmentTypeToAssessmentEvent() {
    UUID assessmentId = Uuid7.timeBasedUuid();
    persistAssessment(
        assessmentId,
        AssessmentType.ESCAPE_CASE_ASSESSMENT,
        AssessmentOutcome.NILLED,
        "Legacy assessment");

    // assessment_type is NOT NULL in the schema; relax it for this rolled-back transaction so we
    // can reproduce a genuine legacy row whose type was never populated.
    jdbcClient
        .sql("ALTER TABLE claims.assessment ALTER COLUMN assessment_type DROP NOT NULL")
        .update();
    jdbcClient
        .sql("UPDATE claims.assessment SET assessment_type = NULL WHERE id = :id")
        .param("id", assessmentId)
        .update();

    ClaimHistoryEventRow event = findEvent(assessmentId);

    // A null type falls through the CASE to ASSESSMENT; no fabricated type value is invented.
    assertThat(event.eventType()).isEqualTo(ASSESSMENT);
    assertThat(event.metadata().get(KEY_ASSESSMENT_TYPE).isNull()).isTrue();
  }

  @Test
  @DisplayName("Retains a null assessment_outcome as an explicit JSON null (no fabricated value)")
  void retainsNullAssessmentOutcomeAsJsonNull() {
    UUID assessmentId = Uuid7.timeBasedUuid();
    persistAssessment(assessmentId, AssessmentType.ESCAPE_CASE_ASSESSMENT, null, "Outcome pending");

    ClaimHistoryEventRow event = findEvent(assessmentId);

    assertThat(event.eventType()).isEqualTo(ASSESSMENT);
    // The key is present but null - no default or placeholder is substituted.
    assertThat(event.metadata().get(KEY_ASSESSMENT_OUTCOME).isNull()).isTrue();
    assertThat(event.metadata().get(KEY_ASSESSMENT_REASON).asText()).isEqualTo("Outcome pending");
  }

  @Test
  @DisplayName("Interleaves assessment and void events chronologically with the submission event")
  void interleavesAssessmentAndVoidEventsChronologicallyWithSubmission() {
    Instant submissionTimestamp = findHistory().getFirst().eventTimestamp();

    UUID earlierAssessmentId = Uuid7.timeBasedUuid();
    UUID laterVoidId = Uuid7.timeBasedUuid();
    persistAssessment(
        earlierAssessmentId,
        AssessmentType.ESCAPE_CASE_ASSESSMENT,
        AssessmentOutcome.PAID_IN_FULL,
        "Before submission");
    persistAssessment(laterVoidId, AssessmentType.VOID, null, "After submission");

    // Position the assessment before, and the void after, the submission event (created_on is set
    // by @CreationTimestamp on insert, so force deterministic timestamps with a raw update).
    forceCreatedOn(earlierAssessmentId, submissionTimestamp.minusSeconds(60));
    forceCreatedOn(laterVoidId, submissionTimestamp.plusSeconds(60));

    List<ClaimHistoryEventRow> events = findHistory();

    // Newest first: VOID (after) -> SUBMISSION -> ASSESSMENT (before).
    assertThat(events).hasSize(3);
    assertThat(events)
        .extracting(ClaimHistoryEventRow::eventType)
        .containsExactly(VOID, SUBMISSION, ASSESSMENT);
    assertThat(events)
        .extracting(ClaimHistoryEventRow::sourceId)
        .containsExactly(laterVoidId, CLAIM_1_ID, earlierAssessmentId);
  }

  @Test
  @DisplayName("Returns no assessment or void events when the claim has no assessment rows")
  void returnsNoAssessmentOrVoidEventsWhenClaimHasNoAssessments() {
    List<ClaimHistoryEventRow> events = findHistory();

    assertThat(events).extracting(ClaimHistoryEventRow::eventType).doesNotContain(ASSESSMENT, VOID);
  }

  // ----------------------------------------------------------------------------------------------
  // AMENDMENT events
  //   - Change tracking / ordering (DSTEW-1813 / DSTEW-1814)
  //   - FSP history indicators derived from amendment-linked data (DSTEW-1815)
  // ----------------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "Maps a single claim_amendment into an AMENDMENT event with requester, reason, indicators"
          + " and changes")
  void mapsSingleAmendmentToAmendmentEvent() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    // A provider-requested change with no linked calculated_fee_detail (repricing did not run).
    persistAmendment(
        amendmentId,
        diff(change("client_surname", "\"Smyth\"", "\"Smith\"", SOURCE_REQUESTED)),
        AMENDMENT_TIMESTAMP);

    ClaimHistoryEventRow event = findEvent(amendmentId);

    assertThat(event.eventType()).isEqualTo(AMENDMENT);
    assertThat(event.sourceId()).isEqualTo(amendmentId);
    assertThat(event.actorId()).isEqualTo(USER_ID);
    assertThat(event.eventTimestamp()).isEqualTo(AMENDMENT_TIMESTAMP);
    assertThat(event.metadata().get("requested_by_code").asText()).isEqualTo(REQUESTED_BY_PROVIDER);
    assertThat(event.metadata().get("amendment_reason_code").asText())
        .isEqualTo(REASON_PROVIDER_ERROR);

    // No linked fee row: repricing did not run, so no fabricated pricing/escape metadata.
    assertThat(event.metadata().get(KEY_PRICING_RECALCULATED).asBoolean()).isFalse();
    assertThat(event.metadata().get(KEY_PRICE_CHANGED).asBoolean()).isFalse();
    assertThat(event.metadata().get(KEY_ESCAPE_CASE_LOGGED).asBoolean()).isFalse();

    var changes = event.metadata().get(KEY_CHANGES);
    assertThat(changes.isArray()).isTrue();
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).get(KEY_FIELD_IDENTIFIER).asText()).isEqualTo("client_surname");
    assertThat(changes.get(0).get("before").asText()).isEqualTo("Smyth");
    assertThat(changes.get(0).get("after").asText()).isEqualTo("Smith");
    assertThat(changes.get(0).get(KEY_CHANGE_SOURCE).asText()).isEqualTo(SOURCE_REQUESTED);
  }

  @Test
  @DisplayName(
      "Pricing amendment with a monetary change: pricing_recalculated & price_changed true")
  void pricingAmendmentPriceChanged() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    persistAmendment(amendmentId, emptyDiff());
    linkCalculatedFeeDetail(amendmentId, true, false);

    ClaimHistoryEventRow event = findEvent(amendmentId);

    assertThat(event.metadata().get(KEY_PRICING_RECALCULATED).asBoolean()).isTrue();
    assertThat(event.metadata().get(KEY_PRICE_CHANGED).asBoolean()).isTrue();
    assertThat(event.metadata().get(KEY_ESCAPE_CASE_LOGGED).asBoolean()).isFalse();
  }

  @Test
  @DisplayName("Pricing amendment with no monetary change: pricing_recalculated true, price false")
  void pricingAmendmentPriceUnchanged() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    persistAmendment(amendmentId, emptyDiff());
    linkCalculatedFeeDetail(amendmentId, false, false);

    ClaimHistoryEventRow event = findEvent(amendmentId);

    assertThat(event.metadata().get(KEY_PRICING_RECALCULATED).asBoolean()).isTrue();
    assertThat(event.metadata().get(KEY_PRICE_CHANGED).asBoolean()).isFalse();
  }

  @Test
  @DisplayName(
      "Amendment that caused the escape transition (false -> true): escape_case_logged true")
  void amendmentCausedEscapeTransitionLogsEscape() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    // FSP-sourced fee.escapeCaseFlag transition from false to true is the transition source.
    persistAmendment(amendmentId, escapeDiff(false, true));
    linkCalculatedFeeDetail(amendmentId, true, true);

    ClaimHistoryEventRow event = findEvent(amendmentId);

    assertThat(event.metadata().get(KEY_ESCAPE_CASE_LOGGED).asBoolean()).isTrue();
  }

  @Test
  @DisplayName("Amendment on an already-escaped claim (no transition): escape_case_logged false")
  void alreadyEscapedClaimDoesNotLogEscape() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    // No FSP escapeCaseFlag transition entry in the diff (an already-escaped claim never records
    // one), even though the linked fee still carries escape_case_flag = true. The indicator must be
    // derived from the transition, not the state.
    persistAmendment(
        amendmentId, diff(change("fee.totalAmount", "\"100.00\"", "\"120.00\"", SOURCE_FSP)));
    linkCalculatedFeeDetail(amendmentId, true, true);

    ClaimHistoryEventRow event = findEvent(amendmentId);

    assertThat(event.metadata().get(KEY_PRICING_RECALCULATED).asBoolean()).isTrue();
    assertThat(event.metadata().get(KEY_ESCAPE_CASE_LOGGED).asBoolean()).isFalse();
  }

  @Test
  @DisplayName("Escape de-escalation (true -> false) is not logged as an escape transition")
  void escapeDeEscalationDoesNotLogEscape() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    persistAmendment(amendmentId, escapeDiff(true, false));
    linkCalculatedFeeDetail(amendmentId, true, false);

    ClaimHistoryEventRow event = findEvent(amendmentId);

    assertThat(event.metadata().get(KEY_ESCAPE_CASE_LOGGED).asBoolean()).isFalse();
  }

  @Test
  @DisplayName(
      "Multiple amendments flipping escape back and forth: each event reflects its own transition")
  void multipleAmendmentsEscapeFlipFlopResolvedPerAmendment() {
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

    ClaimHistoryEventRow escalate = findEvent(escalateId);
    ClaimHistoryEventRow deEscalate = findEvent(deEscalateId);
    ClaimHistoryEventRow reEscalate = findEvent(reEscalateId);

    // Escape is logged only on the amendments that caused a false -> true transition.
    assertThat(escalate.metadata().get(KEY_ESCAPE_CASE_LOGGED).asBoolean()).isTrue();
    assertThat(deEscalate.metadata().get(KEY_ESCAPE_CASE_LOGGED).asBoolean()).isFalse();
    assertThat(reEscalate.metadata().get(KEY_ESCAPE_CASE_LOGGED).asBoolean()).isTrue();

    // price_changed is likewise independent per amendment (from each linked fee row).
    assertThat(escalate.metadata().get(KEY_PRICE_CHANGED).asBoolean()).isTrue();
    assertThat(deEscalate.metadata().get(KEY_PRICE_CHANGED).asBoolean()).isTrue();
    assertThat(reEscalate.metadata().get(KEY_PRICE_CHANGED).asBoolean()).isFalse();

    // All three ran repricing (each has a linked calculated_fee_detail).
    assertThat(escalate.metadata().get(KEY_PRICING_RECALCULATED).asBoolean()).isTrue();
    assertThat(deEscalate.metadata().get(KEY_PRICING_RECALCULATED).asBoolean()).isTrue();
    assertThat(reEscalate.metadata().get(KEY_PRICING_RECALCULATED).asBoolean()).isTrue();
  }

  @Test
  @DisplayName(
      "Multiple amendments: price_changed is resolved per amendment (changed, unchanged, changed)")
  void multipleAmendmentsPriceChangedMixResolvedPerAmendment() {
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

    ClaimHistoryEventRow first = findEvent(firstChanged);
    ClaimHistoryEventRow second = findEvent(secondUnchanged);
    ClaimHistoryEventRow third = findEvent(thirdChanged);

    assertThat(first.metadata().get(KEY_PRICE_CHANGED).asBoolean()).isTrue();
    assertThat(second.metadata().get(KEY_PRICE_CHANGED).asBoolean()).isFalse();
    assertThat(third.metadata().get(KEY_PRICE_CHANGED).asBoolean()).isTrue();

    // No escape transition on any of them, and all ran repricing.
    for (ClaimHistoryEventRow event : List.of(first, second, third)) {
      assertThat(event.metadata().get(KEY_PRICING_RECALCULATED).asBoolean()).isTrue();
      assertThat(event.metadata().get(KEY_ESCAPE_CASE_LOGGED).asBoolean()).isFalse();
    }
  }

  @Test
  @DisplayName(
      "Provider-requested change with repricing but no monetary change: P=true, C=false, E=false")
  void requestedChangeWithRepricingNoPriceChangeNoEscape() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    // A provider-requested (non-FSP) field change; repricing ran but produced the same total and no
    // escape. Validates all three indicators plus the Requested change_source passthrough.
    persistAmendment(
        amendmentId,
        diff(change("claim.caseReferenceNumber", "\"REF-1\"", "\"REF-2\"", SOURCE_REQUESTED)));
    linkCalculatedFeeDetail(amendmentId, false, false);

    ClaimHistoryEventRow event = findEvent(amendmentId);

    assertThat(event.metadata().get(KEY_PRICING_RECALCULATED).asBoolean()).isTrue();
    assertThat(event.metadata().get(KEY_PRICE_CHANGED).asBoolean()).isFalse();
    assertThat(event.metadata().get(KEY_ESCAPE_CASE_LOGGED).asBoolean()).isFalse();
    assertThat(event.metadata().get(KEY_CHANGES)).hasSize(1);
    assertThat(event.metadata().get(KEY_CHANGES).get(0).get(KEY_CHANGE_SOURCE).asText())
        .isEqualTo(SOURCE_REQUESTED);
  }

  @Test
  @DisplayName("Rich amendment with REQUESTED and FSP changes: all indicators true, changes kept")
  void richAmendmentRequestedAndFspChangesAllIndicatorsTrue() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    // A provider-requested field change plus FSP consequences: a total change and an escape
    // transition. Repricing ran, price changed, and the escape transition is logged.
    persistAmendment(
        amendmentId,
        diff(
            change("claim.netProfitCostsAmount", "\"100.00\"", "\"150.00\"", SOURCE_REQUESTED),
            change("fee.totalAmount", "\"100.00\"", "\"180.00\"", SOURCE_FSP),
            change("fee.escapeCaseFlag", "false", "true", SOURCE_FSP)));
    linkCalculatedFeeDetail(amendmentId, true, true);

    ClaimHistoryEventRow event = findEvent(amendmentId);

    assertThat(event.metadata().get(KEY_PRICING_RECALCULATED).asBoolean()).isTrue();
    assertThat(event.metadata().get(KEY_PRICE_CHANGED).asBoolean()).isTrue();
    assertThat(event.metadata().get(KEY_ESCAPE_CASE_LOGGED).asBoolean()).isTrue();
    assertThat(event.metadata().get(KEY_CHANGES)).hasSize(3);
  }

  @Test
  @DisplayName("Distinguishes REQUESTED changes from FSP consequences in the changes list")
  void distinguishesRequestedAndFspChanges() {
    UUID amendmentId = Uuid7.timeBasedUuid();
    persistAmendment(
        amendmentId,
        diff(
            change("client_surname", "\"Smyth\"", "\"Smith\"", SOURCE_REQUESTED),
            change("calculated_fee_detail.total_amount", "\"100.00\"", "\"125.00\"", SOURCE_FSP)),
        AMENDMENT_TIMESTAMP);

    ClaimHistoryEventRow event = findEvent(amendmentId);

    var changes = event.metadata().get(KEY_CHANGES);
    assertThat(changes).hasSize(2);
    assertThat(changes.get(0).get(KEY_CHANGE_SOURCE).asText()).isEqualTo(SOURCE_REQUESTED);
    assertThat(changes.get(1).get(KEY_CHANGE_SOURCE).asText()).isEqualTo(SOURCE_FSP);
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
        diff(change("client.client2Surname", "\"Bloggs\"", "null", SOURCE_REQUESTED)),
        AMENDMENT_TIMESTAMP);

    ClaimHistoryEventRow event = findEvent(amendmentId);

    var change = event.metadata().get(KEY_CHANGES).get(0);
    assertThat(change.get(KEY_FIELD_IDENTIFIER).asText()).isEqualTo("client.client2Surname");
    assertThat(change.get(KEY_CHANGE_SOURCE).asText()).isEqualTo(SOURCE_REQUESTED);
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
        diff(change("client_surname", "\"Smyth\"", "\"Smith\"", SOURCE_REQUESTED)),
        AMENDMENT_TIMESTAMP);

    ClaimHistoryEventRow event = findEvent(amendmentId);

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
        diff(change("client_surname", "\"Smyth\"", "\"Smith\"", SOURCE_REQUESTED)),
        AMENDMENT_TIMESTAMP);
    persistAmendment(
        laterAmendmentId,
        diff(change("fee_code", "\"OLD\"", "\"NEW\"", SOURCE_REQUESTED)),
        Instant.parse("2026-05-03T10:00:00Z"));

    List<UUID> amendments = amendmentSourceIds();

    // Newest amendment first, so the latest amendment is unambiguously derivable for the banner.
    assertThat(amendments).containsExactly(laterAmendmentId, earlierAmendmentId);
  }

  @Test
  @DisplayName("Orders same-timestamp amendments deterministically by source id descending")
  void ordersSameTimestampAmendmentsBySourceIdDescending() {
    UUID idA = Uuid7.timeBasedUuid();
    UUID idB = Uuid7.timeBasedUuid();
    persistAmendment(idA, diff(change("client_surname", "\"A\"", "\"B\"", SOURCE_REQUESTED)));
    persistAmendment(idB, diff(change("fee_code", "\"A\"", "\"B\"", SOURCE_REQUESTED)));

    List<UUID> amendmentOrder = amendmentSourceIds();

    UUID higher = idA.compareTo(idB) > 0 ? idA : idB;
    UUID lower = idA.compareTo(idB) > 0 ? idB : idA;
    assertThat(amendmentOrder).containsExactly(higher, lower);
  }

  @Test
  @DisplayName("Interleaves an amendment chronologically with submission and assessment events")
  void interleavesAmendmentWithSubmissionAndAssessment() {
    Instant submissionTimestamp = findHistory().getFirst().eventTimestamp();

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
        diff(change("client_surname", "\"Smyth\"", "\"Smith\"", SOURCE_REQUESTED)),
        submissionTimestamp.plusSeconds(60));

    List<ClaimHistoryEventRow> events = findHistory();

    // Newest first: AMENDMENT (after) -> SUBMISSION -> ASSESSMENT (before).
    assertThat(events).hasSize(3);
    assertThat(events)
        .extracting(ClaimHistoryEventRow::eventType)
        .containsExactly(AMENDMENT, SUBMISSION, ASSESSMENT);
    assertThat(events)
        .extracting(ClaimHistoryEventRow::sourceId)
        .containsExactly(laterAmendmentId, CLAIM_1_ID, earlierAssessmentId);
  }

  @Test
  @DisplayName("Returns no AMENDMENT event when the claim has no claim_amendment row")
  void returnsNoAmendmentEventWhenClaimHasNoAmendment() {
    // A failed/rejected attempt persists no claim_amendment row, so it never appears.
    List<ClaimHistoryEventRow> events = findHistory();

    assertThat(events).extracting(ClaimHistoryEventRow::eventType).doesNotContain(AMENDMENT);
  }

  @Test
  @DisplayName("Excludes New Matter Starts: a matter_start row never produces a timeline event")
  void excludesNewMatterStartsFromTimeline() {
    // New Matter Starts are submission-level and must stay out of the claim timeline. Seed a matter
    // start against the same submission as the claim and prove it contributes nothing.
    UUID matterStartId = Uuid7.timeBasedUuid();
    matterStartRepository.save(
        MatterStart.builder()
            .id(matterStartId)
            .submission(submissionRepository.getReferenceById(SUBMISSION_1_ID))
            .numberOfMatterStarts(1)
            .createdByUserId(USER_ID)
            .build());
    matterStartRepository.flush();

    List<ClaimHistoryEventRow> events = findHistory();

    // Only the submission event exists; the matter start is neither an event nor a source id.
    assertThat(events).extracting(ClaimHistoryEventRow::eventType).containsExactly(SUBMISSION);
    assertThat(events).extracting(ClaimHistoryEventRow::sourceId).doesNotContain(matterStartId);
  }

  // ----------------------------------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------------------------------

  private List<ClaimHistoryEventRow> findHistory() {
    return claimHistoryRepository.findHistory(CLAIM_1_ID, HISTORY_LIMIT);
  }

  private ClaimHistoryEventRow findEvent(UUID sourceId) {
    return findHistory().stream()
        .filter(event -> sourceId.equals(event.sourceId()))
        .findFirst()
        .orElseThrow();
  }

  private List<UUID> amendmentSourceIds() {
    return findHistory().stream()
        .filter(event -> AMENDMENT.equals(event.eventType()))
        .map(ClaimHistoryEventRow::sourceId)
        .toList();
  }

  private void persistAmendment(UUID id, String diffJson) {
    persistAmendment(id, diffJson, AMENDMENT_TIMESTAMP);
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
            .createdOn(createdOn)
            .build());
    claimAmendmentRepository.flush();
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
            .createdOn(Instant.now())
            .build();
    calculatedFeeDetailRepository.save(fee);
    calculatedFeeDetailRepository.flush();
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

  private void forceCreatedOn(UUID assessmentId, Instant createdOn) {
    jdbcClient
        .sql("UPDATE claims.assessment SET created_on = :ts WHERE id = :id")
        .param("ts", OffsetDateTime.ofInstant(createdOn, ZoneOffset.UTC))
        .param("id", assessmentId)
        .update();
  }

  // ---- JSON diff builders -----------------------------------------------------------------------

  private static String emptyDiff() {
    return diff();
  }

  private static String escapeDiff(boolean before, boolean after) {
    return diff(
        change("fee.escapeCaseFlag", String.valueOf(before), String.valueOf(after), SOURCE_FSP));
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
}
