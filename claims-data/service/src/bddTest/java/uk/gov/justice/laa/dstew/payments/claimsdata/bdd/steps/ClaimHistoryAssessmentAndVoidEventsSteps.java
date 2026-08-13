package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step glue for {@code claimHistoryAssessmentAndVoidEvents.feature} — DSTEW-1812 (1645-B).
 *
 * <p>Seeds {@code claims.claim} → {@code claims.claim_summary_fee} → {@code claims.assessment}
 * directly via JPA (falling back to native SQL where the entity's {@code @NotNull} annotations are
 * stricter than the underlying DB columns — e.g. legacy null {@code assessment_type} rows), then
 * hits the real REST endpoint {@code GET /api/v1/claims/{claimId}/history} via {@link
 * BddApiStepSupport}. Assertions run against the raw {@link JsonNode} so present-and-null vs
 * missing-key distinctions are preserved.
 *
 * <p>Feature-file scenarios use human-readable source-id labels (e.g. {@code "assess-uuid-1"});
 * they map to real UUIDs through a scenario-scoped label→UUID table so multi-row scenarios keep
 * their identifiers stable across Given / Then blocks.
 *
 * <p>Every step body wraps its logic in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures#step(String,
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.ThrowingRunnable)}
 * per the project-wide step-failure-reporting standing rule.
 */
public class ClaimHistoryAssessmentAndVoidEventsSteps {

  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Autowired private AssessmentRepository assessmentRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private BddApiStepSupport api;

  /** Current claim under test. Set by any {@code Given a claim exists...} step. */
  private UUID currentClaimId;

  /** Cache the summary-fee id created alongside the current claim (FK for assessment inserts). */
  private UUID currentClaimSummaryFeeId;

  /** Live label→UUID map so labels like {@code "assess-uuid-1"} stay stable within the scenario. */
  private final Map<String, UUID> labelToUuid = new HashMap<>();

  /** Cached raw response for the last {@code When I request the claim history timeline}. */
  private JsonNode lastResponse;

  /**
   * If a scenario used {@code Given a claim exists ...} with an explicit assessment_outcome
   * follow-up (e.g. {@code And that row's assessment_outcome is "PAID_IN_FULL"}), we remember the
   * assessment id created just before so we can update it. Used by DS1812_2 and DS1812_4.
   */
  private UUID lastSeededAssessmentId;

  // ---------------------------------------------------------------------------
  // Givens — seed the claim + assessment rows.
  // ---------------------------------------------------------------------------

  @Given("a claim exists with the following claims.assessment row")
  @Transactional
  public void aClaimExistsWithTheFollowingAssessmentRow(DataTable table) {
    step(
        "seed a claim + a single claims.assessment row from the feature-file data table",
        () -> {
          Map<String, String> row = singleFieldValueMap(table);
          Claim claim = seedClaim();
          UUID assessmentId = registerLabel(row.get("id"));
          Instant createdOn = Instant.parse(row.get("created_on"));
          String createdByUserId = row.get("created_by_user_id");
          String typeText = row.get("assessment_type");
          String outcomeText = row.get("assessment_outcome");
          String reason = row.get("assessment_reason");
          insertAssessment(
              assessmentId,
              claim,
              parseType(typeText),
              parseOutcome(outcomeText),
              reason,
              createdByUserId,
              createdOn);
          lastSeededAssessmentId = assessmentId;
        });
  }

  @Given("a claim exists with a claims.assessment row where assessment_type is {string}")
  @Transactional
  public void aClaimExistsWithAssessmentTypeString(String assessmentType) {
    step(
        "seed a claim + assessment with assessment_type=" + assessmentType,
        () -> {
          Claim claim = seedClaim();
          UUID assessmentId = Uuid7.timeBasedUuid();
          insertAssessment(
              claim,
              parseType(assessmentType),
              null,
              "Seeded by BDD",
              "bdd-user",
              Instant.parse("2026-05-01T00:00:00Z"),
              assessmentId);
          lastSeededAssessmentId = assessmentId;
        });
  }

  @Given("a claim exists with a claims.assessment row where assessment_type is null")
  @Transactional
  public void aClaimExistsWithAssessmentTypeNull() {
    step(
        "seed a claim + a legacy assessment row with assessment_type = NULL "
            + "(native SQL to bypass the entity's @NotNull)",
        () -> {
          Claim claim = seedClaim();
          UUID assessmentId = Uuid7.timeBasedUuid();
          insertAssessmentRaw(
              assessmentId,
              claim.getId(),
              /* assessmentType */ null,
              /* assessmentOutcome */ null,
              /* assessmentReason */ "legacy row (assessment_type null)",
              /* createdByUserId */ "bdd-user",
              /* createdOn */ Instant.parse("2026-05-01T00:00:00Z"));
          lastSeededAssessmentId = assessmentId;
        });
  }

  @Given("a claim exists with a claims.assessment row where {string} is null")
  @Transactional
  public void aClaimExistsWithColumnNull(String nullableColumn) {
    step(
        "seed a claim + assessment row where " + nullableColumn + " is NULL",
        () -> {
          Claim claim = seedClaim();
          UUID assessmentId = Uuid7.timeBasedUuid();
          AssessmentType type = AssessmentType.ESCAPE_CASE_ASSESSMENT;
          AssessmentOutcome outcome =
              "assessment_outcome".equals(nullableColumn) ? null : AssessmentOutcome.PAID_IN_FULL;
          String reason = "assessment_reason".equals(nullableColumn) ? null : "populated reason";
          if (reason == null || outcome == null) {
            // reason is @NotNull on the entity, outcome column can be null but drop through raw for
            // symmetry (the null-column case is authored by BDD anyway).
            insertAssessmentRaw(
                assessmentId,
                claim.getId(),
                type.getValue(),
                outcome == null ? null : outcome.getValue(),
                reason,
                "bdd-user",
                Instant.parse("2026-05-01T00:00:00Z"));
          } else {
            insertAssessment(
                claim,
                type,
                outcome,
                reason,
                "bdd-user",
                Instant.parse("2026-05-01T00:00:00Z"),
                assessmentId);
          }
          lastSeededAssessmentId = assessmentId;
        });
  }

  @Given("the other assessment columns are populated")
  public void theOtherAssessmentColumnsArePopulated() {
    step(
        "no-op — sibling columns were populated when the row was seeded",
        () -> {
          // Documentation-only step. Prior seed step already set the other columns to real values.
        });
  }

  @And("that row's assessment_outcome is {string}")
  @Transactional
  public void thatRowsAssessmentOutcomeIs(String outcomeText) {
    step(
        "override assessment_outcome to '" + outcomeText + "' on the most recently seeded row",
        () -> {
          UUID id = requireSeededAssessmentId();
          jdbcClient
              .sql("UPDATE claims.assessment SET assessment_outcome = :o WHERE id = :id")
              .param("o", outcomeText)
              .param("id", id)
              .update();
        });
  }

  @Given("a claim exists with the following lifecycle events in stored `created_on` order")
  @Transactional
  public void aClaimExistsWithLifecycleEvents(DataTable table) {
    step(
        "seed claim + submission created_on + assessment rows for the interleave scenario",
        () -> {
          List<Map<String, String>> rows = table.asMaps();
          // Row 1 is the SUBMISSION — we need to pin claim.created_on and remember the claim UUID
          // under the SUBMISSION source_id label so later assertions can look it up.
          Map<String, String> submissionRow = rows.get(0);
          if (!"SUBMISSION".equals(submissionRow.get("event"))) {
            throw new IllegalStateException(
                "First row of lifecycle table must be SUBMISSION; got " + submissionRow);
          }
          Claim claim = seedClaim();
          UUID claimId = claim.getId();
          registerLabel(submissionRow.get("source_id"), claimId);
          // The SUBMISSION event's source_id comes from c.id, and event_timestamp from
          // c.created_on. Pin created_on via native SQL (Claim.createdOn is @CreationTimestamp +
          // updatable=false so we must use raw SQL to override).
          Instant submissionCreatedOn = Instant.parse(submissionRow.get("created_on"));
          jdbcClient
              .sql("UPDATE claims.claim SET created_on = :ts WHERE id = :id")
              .param("ts", OffsetDateTime.ofInstant(submissionCreatedOn, ZoneOffset.UTC))
              .param("id", claimId)
              .update();

          for (int i = 1; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String eventLabel = row.get("event"); // ASSESSMENT | VOID
            UUID assessmentId = registerLabel(row.get("source_id"));
            Instant createdOn = Instant.parse(row.get("created_on"));
            String metaType = row.get("metadata_type");
            AssessmentType type = parseType(metaType);
            if ("VOID".equals(eventLabel) && type != AssessmentType.VOID) {
              throw new IllegalStateException(
                  "Row labelled VOID must carry metadata_type=VOID; got " + row);
            }
            AssessmentOutcome outcome =
                type == AssessmentType.VOID ? null : AssessmentOutcome.PAID_IN_FULL;
            String reason =
                type == AssessmentType.VOID ? "Voided in interleave scenario" : "populated reason";
            insertAssessment(claim, type, outcome, reason, "bdd-user", createdOn, assessmentId);
          }
        });
  }

  @Given("a claim exists that has been submitted but has no claims.assessment rows")
  @Transactional
  public void aClaimExistsWithNoAssessmentRows() {
    step(
        "seed a claim with a SUBMISSION but zero assessment rows",
        () -> {
          seedClaim();
        });
  }

  // ---------------------------------------------------------------------------
  // When — hit the real endpoint.
  // ---------------------------------------------------------------------------

  @When("I request the claim history timeline")
  public void iRequestTheClaimHistoryTimeline() {
    step(
        "GET /api/v1/claims/" + currentClaimId + "/history",
        () -> lastResponse = api.getClaimHistory(requireCurrentClaimId()));
  }

  // ---------------------------------------------------------------------------
  // Thens — assertions.
  // ---------------------------------------------------------------------------

  @Then("the response contains an event with the following envelope")
  public void theResponseContainsAnEventWithTheFollowingEnvelope(DataTable table) {
    step(
        "assert the timeline contains an event whose envelope matches the feature-file table",
        () -> {
          Map<String, String> expected = envelopeMap(table);
          UUID expectedSourceId = requireLabel(expected.get("source_id"));
          JsonNode event = requireEventBySourceId(expectedSourceId);
          assertEnvelopeFieldEquals(event, "event_type", expected.get("event_type"));
          if (expected.containsKey("event_timestamp")) {
            assertTimestamp(event, expected.get("event_timestamp"));
          }
          String actorExpected =
              firstNonNull(expected.get("actor_id"), expected.get("actor_user_id"));
          if (actorExpected != null) {
            // The delivered contract calls this field `actor_id`; the feature file happens to spell
            // it `actor_user_id` in one row — accept either alias so the intent is preserved.
            assertEnvelopeFieldEquals(event, "actor_id", actorExpected);
          }
        });
  }

  @Then("the response contains an event with event_type {string}")
  public void theResponseContainsAnEventWithEventType(String eventType) {
    step(
        "assert timeline contains an event with event_type=" + eventType,
        () -> {
          assertThat(findFirstEventByType(eventType))
              .as("event with event_type=%s", eventType)
              .isNotNull();
        });
  }

  @Then("the response contains an event with event_type {string} and source_id {string}")
  public void theResponseContainsAnEventWithEventTypeAndSourceId(
      String eventType, String sourceIdLabel) {
    step(
        "assert timeline contains " + eventType + " event with source_id label " + sourceIdLabel,
        () -> {
          UUID sourceId = requireLabel(sourceIdLabel);
          JsonNode event = requireEventBySourceId(sourceId);
          assertEnvelopeFieldEquals(event, "event_type", eventType);
        });
  }

  @Then("the response contains an event with event_type {string} and source_id matching that row")
  public void theResponseContainsAnEventWithEventTypeMatchingLastAssessment(String eventType) {
    step(
        "assert timeline contains "
            + eventType
            + " event with source_id = last-seeded assessment id",
        () -> {
          UUID sourceId = requireSeededAssessmentId();
          JsonNode event = requireEventBySourceId(sourceId);
          assertEnvelopeFieldEquals(event, "event_type", eventType);
        });
  }

  @Then("the response does NOT contain an event of type {string} with source_id {string}")
  public void theResponseDoesNotContainEventOfTypeWithSourceId(
      String eventType, String sourceIdLabel) {
    step(
        "assert timeline does NOT contain "
            + eventType
            + " event with source_id label "
            + sourceIdLabel,
        () -> {
          UUID sourceId = requireLabel(sourceIdLabel);
          boolean found =
              events()
                  .anyMatch(
                      e ->
                          eventType.equals(e.path("event_type").asText())
                              && sourceId.toString().equals(e.path("source_id").asText()));
          assertThat(found)
              .as("no %s event with source_id %s should be present", eventType, sourceId)
              .isFalse();
        });
  }

  @Then("that event's metadata contains")
  public void thatEventsMetadataContains(DataTable table) {
    step(
        "assert the LAST-inspected event's metadata contains all expected fields",
        () -> {
          // Reuse the last event we located via requireEventBySourceId; for DS1812_1 this is the
          // envelope's event.
          JsonNode event = requireLastInspectedEvent();
          assertMetadataContains(event, metadataMap(table));
        });
  }

  @Then("the VOID event metadata contains")
  public void theVoidEventMetadataContains(DataTable table) {
    step(
        "assert the VOID event's metadata contains all expected fields",
        () -> {
          JsonNode event = requireFirstEventByType("VOID");
          assertMetadataContains(event, metadataMap(table));
        });
  }

  @Then("that event's metadata field {string} is {string}")
  public void thatEventsMetadataFieldIs(String field, String expected) {
    step(
        "assert metadata." + field + " = '" + expected + "' on the last-seeded assessment's event",
        () -> {
          UUID sourceId = requireSeededAssessmentId();
          JsonNode event = requireEventBySourceId(sourceId);
          assertMetadataFieldEquals(event, field, expected);
        });
  }

  @Then("the VOID event metadata does NOT contain the field {string}")
  public void theVoidEventMetadataDoesNotContainField(String field) {
    step(
        "assert VOID event metadata does NOT carry field '" + field + "'",
        () -> assertMetadataFieldAbsent(requireFirstEventByType("VOID"), field));
  }

  @Then("that event's metadata does NOT contain the field {string}")
  public void thatEventsMetadataDoesNotContainField(String field) {
    step(
        "assert last-seeded event's metadata does NOT carry field '" + field + "'",
        () ->
            assertMetadataFieldAbsent(requireEventBySourceId(requireSeededAssessmentId()), field));
  }

  @Then("that event's metadata does NOT contain a fabricated placeholder value for {string}")
  public void thatEventsMetadataDoesNotContainFabricatedPlaceholder(String field) {
    step(
        "assert metadata." + field + " has not been substituted with a placeholder value",
        () -> {
          // Same assertion as absence — a fabricated placeholder would surface as any non-null
          // value under the field key.
          assertMetadataFieldAbsent(requireEventBySourceId(requireSeededAssessmentId()), field);
        });
  }

  @Then("the corresponding ASSESSMENT event's metadata does NOT contain the field {string}")
  public void theCorrespondingAssessmentEventMetadataDoesNotContainField(String field) {
    step(
        "assert corresponding ASSESSMENT event metadata does NOT carry field '" + field + "'",
        () -> {
          UUID sourceId = requireSeededAssessmentId();
          JsonNode event = requireEventBySourceId(sourceId);
          assertEnvelopeFieldEquals(event, "event_type", "ASSESSMENT");
          assertMetadataFieldAbsent(event, field);
        });
  }

  @Then("no placeholder value has been substituted for {string}")
  public void noPlaceholderValueHasBeenSubstitutedFor(String field) {
    step(
        "assert metadata." + field + " has not been silently defaulted to a placeholder",
        () ->
            assertMetadataFieldAbsent(requireEventBySourceId(requireSeededAssessmentId()), field));
  }

  @Then("the timeline contains events in the documented default order")
  public void theTimelineContainsEventsInTheDocumentedDefaultOrder(DataTable table) {
    step(
        "assert timeline default ordering matches the feature-file table",
        () -> {
          List<Map<String, String>> expected = table.asMaps();
          List<JsonNode> emitted = events().toList();
          // Default order per the timeline contract is event_timestamp DESC, source_id DESC. The
          // feature file's authoring order lists SUBMISSION → ASSESSMENT → VOID from OLDEST to
          // NEWEST, so we compare to the DESC-emitted response by reversing the expected list.
          assertThat(emitted).as("timeline events emitted").hasSize(expected.size());
          for (int i = 0; i < expected.size(); i++) {
            Map<String, String> row = expected.get(expected.size() - 1 - i);
            JsonNode event = emitted.get(i);
            assertEnvelopeFieldEquals(event, "event_type", row.get("event"));
            assertThat(event.path("source_id").asText())
                .as("event #%d (%s) source_id", i, row.get("event"))
                .isEqualTo(requireLabel(row.get("source_id")).toString());
          }
        });
  }

  @Then("the response contains no event of type {string}")
  public void theResponseContainsNoEventOfType(String eventType) {
    step(
        "assert timeline contains NO event of type '" + eventType + "'",
        () -> {
          boolean any = events().anyMatch(e -> eventType.equals(e.path("event_type").asText()));
          assertThat(any).as("no %s event should be present", eventType).isFalse();
        });
  }

  @Then("no error is returned")
  public void noErrorIsReturned() {
    step(
        "assert previous /history call returned a 2xx",
        () -> {
          assertThat(lastResponse).as("previous /history response body parsed as JSON").isNotNull();
          assertThat(lastResponse.path("events").isArray())
              .as("response has an `events` array (no error envelope)")
              .isTrue();
        });
  }

  @Then("no empty ASSESSMENT or VOID stub event is returned")
  public void noEmptyStubEventIsReturned() {
    step(
        "assert no synthesised stub ASSESSMENT / VOID event with a null source_id sneaks through",
        () -> {
          boolean stub =
              events()
                  .anyMatch(
                      e -> {
                        String type = e.path("event_type").asText();
                        if (!"ASSESSMENT".equals(type) && !"VOID".equals(type)) {
                          return false;
                        }
                        JsonNode sourceId = e.path("source_id");
                        return sourceId.isMissingNode() || sourceId.isNull();
                      });
          assertThat(stub).as("no synthetic stub ASSESSMENT/VOID event").isFalse();
        });
  }

  // ---------------------------------------------------------------------------
  // Internals — seeding / inspection helpers.
  // ---------------------------------------------------------------------------

  private Claim seedClaim() {
    Submission submission = new Submission();
    submission.setId(Uuid7.timeBasedUuid());
    submission.setOfficeAccountNumber("0X001");
    submission.setSubmissionPeriod("JAN-2025");
    submission.setAreaOfLaw(AreaOfLaw.LEGAL_HELP);
    submission.setStatus(SubmissionStatus.CREATED);
    submission.setCreatedByUserId("bdd-seed-user");
    submission.setProviderUserId("bdd-seed-user");
    submission.setCreatedOn(Instant.now());
    submissionRepository.saveAndFlush(submission);

    Claim claim = new Claim();
    claim.setId(Uuid7.timeBasedUuid());
    claim.setSubmission(submission);
    claim.setStatus(ClaimStatus.VALID);
    claim.setLineNumber(1);
    claim.setFeeCode("TEST");
    claim.setMatterTypeCode("MAT01");
    claim.setCaseStartDate(LocalDate.of(2025, 1, 1));
    claim.setCreatedByUserId("bdd-seed-user");
    claim.setUpdatedByUserId("bdd-seed-user");
    claimRepository.saveAndFlush(claim);
    currentClaimId = claim.getId();

    ClaimSummaryFee summaryFee = new ClaimSummaryFee();
    summaryFee.setId(Uuid7.timeBasedUuid());
    summaryFee.setClaim(claim);
    summaryFee.setCreatedByUserId("bdd-seed-user");
    claimSummaryFeeRepository.saveAndFlush(summaryFee);
    currentClaimSummaryFeeId = summaryFee.getId();

    return claim;
  }

  private void insertAssessment(
      Claim claim,
      AssessmentType type,
      AssessmentOutcome outcome,
      String reason,
      String createdByUserId,
      Instant createdOn,
      UUID id) {
    insertAssessment(id, claim, type, outcome, reason, createdByUserId, createdOn);
  }

  private void insertAssessment(
      UUID assessmentId,
      Claim claim,
      AssessmentType type,
      AssessmentOutcome outcome,
      String reason,
      String createdByUserId,
      Instant createdOn) {
    ClaimSummaryFee summaryFee =
        claimSummaryFeeRepository
            .findById(currentClaimSummaryFeeId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "seedClaim() must run before insertAssessment(); no summary fee found"));
    Assessment assessment =
        Assessment.builder()
            .id(assessmentId)
            .claim(claim)
            .claimSummaryFee(summaryFee)
            .assessmentType(type)
            .assessmentOutcome(outcome)
            .assessmentReason(reason)
            .assessedTotalVat(BigDecimal.ZERO)
            .assessedTotalInclVat(BigDecimal.ZERO)
            .allowedTotalVat(BigDecimal.ZERO)
            .allowedTotalInclVat(BigDecimal.ZERO)
            .createdByUserId(createdByUserId)
            .updatedByUserId(createdByUserId)
            .build();
    assessmentRepository.saveAndFlush(assessment);
    // Pin created_on via native SQL because @CreationTimestamp overrides on persist.
    jdbcClient
        .sql("UPDATE claims.assessment SET created_on = :ts WHERE id = :id")
        .param("ts", OffsetDateTime.ofInstant(createdOn, ZoneOffset.UTC))
        .param("id", assessmentId)
        .update();
  }

  /**
   * Native SQL insert used when the target row must carry a null {@code assessment_type} or {@code
   * assessment_reason} (both are {@code @NotNull} on the entity but nullable in the DB — DS1812_4
   * and DS1812_5).
   */
  private void insertAssessmentRaw(
      UUID assessmentId,
      UUID claimId,
      String assessmentType,
      String assessmentOutcome,
      String assessmentReason,
      String createdByUserId,
      Instant createdOn) {
    UUID summaryFeeId = currentClaimSummaryFeeId;
    if (summaryFeeId == null) {
      throw new IllegalStateException(
          "seedClaim() must run before insertAssessmentRaw(); no summary fee cached");
    }
    jdbcClient
        .sql(
            """
            INSERT INTO claims.assessment (
              id, claim_id, claim_summary_fee_id,
              assessment_type, assessment_outcome, assessment_reason,
              assessed_total_vat, assessed_total_incl_vat,
              allowed_total_vat, allowed_total_incl_vat,
              created_by_user_id, created_on,
              updated_by_user_id, updated_on
            ) VALUES (
              :id, :claimId, :summaryFeeId,
              :assessmentType, :assessmentOutcome, :assessmentReason,
              0, 0, 0, 0,
              :createdByUserId, :createdOn,
              :createdByUserId, :createdOn
            )
            """)
        .param("id", assessmentId)
        .param("claimId", claimId)
        .param("summaryFeeId", summaryFeeId)
        .param("assessmentType", assessmentType)
        .param("assessmentOutcome", assessmentOutcome)
        .param("assessmentReason", assessmentReason)
        .param("createdByUserId", createdByUserId)
        .param("createdOn", OffsetDateTime.ofInstant(createdOn, ZoneOffset.UTC))
        .update();
  }

  // ---------------------------------------------------------------------------
  // Response-inspection helpers.
  // ---------------------------------------------------------------------------

  private java.util.stream.Stream<JsonNode> events() {
    assertThat(lastResponse).as("no /history response captured yet").isNotNull();
    JsonNode events = lastResponse.path("events");
    if (!events.isArray()) {
      return java.util.stream.Stream.empty();
    }
    List<JsonNode> list = new java.util.ArrayList<>();
    events.forEach(list::add);
    return list.stream();
  }

  /** Last event inspected via {@link #requireEventBySourceId(UUID)} — used by follow-up Thens. */
  private JsonNode lastInspectedEvent;

  private JsonNode requireEventBySourceId(UUID sourceId) {
    JsonNode event =
        events()
            .filter(e -> sourceId.toString().equals(e.path("source_id").asText()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError("No event on /history timeline for source_id " + sourceId));
    lastInspectedEvent = event;
    return event;
  }

  private JsonNode findFirstEventByType(String eventType) {
    return events()
        .filter(e -> eventType.equals(e.path("event_type").asText()))
        .findFirst()
        .orElse(null);
  }

  private JsonNode requireFirstEventByType(String eventType) {
    JsonNode event = findFirstEventByType(eventType);
    assertThat(event).as("first %s event on /history timeline", eventType).isNotNull();
    lastInspectedEvent = event;
    return event;
  }

  private JsonNode requireLastInspectedEvent() {
    assertThat(lastInspectedEvent)
        .as("no event has been located yet — expected a prior envelope/event assertion")
        .isNotNull();
    return lastInspectedEvent;
  }

  private void assertEnvelopeFieldEquals(JsonNode event, String field, String expected) {
    assertThat(event.path(field).asText())
        .as(
            "envelope field '%s' on event with source_id %s",
            field, event.path("source_id").asText())
        .isEqualTo(expected);
  }

  private void assertTimestamp(JsonNode event, String expectedIso) {
    Instant expected = Instant.parse(expectedIso);
    Instant actual = Instant.parse(event.path("event_timestamp").asText());
    assertThat(actual)
        .as("event_timestamp on event with source_id %s", event.path("source_id").asText())
        .isEqualTo(expected);
  }

  private void assertMetadataContains(JsonNode event, Map<String, String> expected) {
    JsonNode metadata = event.path("metadata");
    assertThat(metadata.isObject())
        .as(
            "metadata container present on event with source_id %s",
            event.path("source_id").asText())
        .isTrue();
    expected.forEach(
        (k, v) ->
            assertThat(metadata.path(k).asText())
                .as("metadata.%s on event with source_id %s", k, event.path("source_id").asText())
                .isEqualTo(v));
  }

  private void assertMetadataFieldEquals(JsonNode event, String field, String expected) {
    JsonNode value = event.path("metadata").path(field);
    assertThat(value.isMissingNode() || value.isNull())
        .as("metadata.%s must be present with value '%s'", field, expected)
        .isFalse();
    assertThat(value.asText())
        .as("metadata.%s on event with source_id %s", field, event.path("source_id").asText())
        .isEqualTo(expected);
  }

  private void assertMetadataFieldAbsent(JsonNode event, String field) {
    JsonNode metadata = event.path("metadata");
    JsonNode value = metadata.path(field);
    // "Absent" per the feature-file contract = either the key is missing entirely OR the value is
    // explicit JSON null (the spec bans both a fabricated placeholder and a leaked-through null).
    assertThat(value.isMissingNode() || value.isNull())
        .as(
            "metadata.%s must NOT be surfaced (key present with a real value would be a "
                + "fabricated placeholder) on event with source_id %s",
            field, event.path("source_id").asText())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Label / DataTable helpers.
  // ---------------------------------------------------------------------------

  private UUID registerLabel(String label) {
    return labelToUuid.computeIfAbsent(label, k -> Uuid7.timeBasedUuid());
  }

  private void registerLabel(String label, UUID uuid) {
    labelToUuid.put(label, uuid);
  }

  private UUID requireLabel(String label) {
    UUID id = labelToUuid.get(label);
    if (id == null) {
      throw new AssertionError(
          "No UUID registered for source_id label '"
              + label
              + "'. Known labels: "
              + labelToUuid.keySet());
    }
    return id;
  }

  private UUID requireSeededAssessmentId() {
    if (lastSeededAssessmentId == null) {
      throw new AssertionError("No assessment has been seeded yet — expected a prior Given step.");
    }
    return lastSeededAssessmentId;
  }

  private UUID requireCurrentClaimId() {
    if (currentClaimId == null) {
      throw new AssertionError("No claim has been seeded yet — expected a prior Given step.");
    }
    return currentClaimId;
  }

  /** Reads a two-column {@code field | value} DataTable into a map. */
  private static Map<String, String> singleFieldValueMap(DataTable table) {
    Map<String, String> out = new HashMap<>();
    for (List<String> row : table.asLists()) {
      if (row.size() < 2) {
        continue;
      }
      String key = row.get(0);
      if ("field".equalsIgnoreCase(key)) {
        continue; // header row
      }
      out.put(key, row.get(1) == null ? "" : row.get(1));
    }
    return out;
  }

  /** Reads a two-column {@code envelopeField | value} DataTable into a map. */
  private static Map<String, String> envelopeMap(DataTable table) {
    Map<String, String> out = new HashMap<>();
    for (List<String> row : table.asLists()) {
      if (row.size() < 2) {
        continue;
      }
      String key = row.get(0);
      if ("envelopeField".equalsIgnoreCase(key)) {
        continue;
      }
      out.put(key, row.get(1));
    }
    return out;
  }

  /** Reads a two-column {@code metadataField | value} DataTable into a map. */
  private static Map<String, String> metadataMap(DataTable table) {
    Map<String, String> out = new HashMap<>();
    for (List<String> row : table.asLists()) {
      if (row.size() < 2) {
        continue;
      }
      String key = row.get(0);
      if ("metadataField".equalsIgnoreCase(key)) {
        continue;
      }
      out.put(key, row.get(1));
    }
    return out;
  }

  private static AssessmentType parseType(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    return AssessmentType.valueOf(text);
  }

  private static AssessmentOutcome parseOutcome(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    return AssessmentOutcome.valueOf(text);
  }

  private static String firstNonNull(String a, String b) {
    return a != null ? a : b;
  }
}
