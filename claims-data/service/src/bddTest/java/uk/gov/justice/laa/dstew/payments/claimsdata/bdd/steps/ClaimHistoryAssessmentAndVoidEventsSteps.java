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
import java.time.Month;
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
import java.util.stream.Stream;

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

  /** Cached raw response for the assessment/void claim-history scenario. */
  private JsonNode lastResponse;

  private static final String BDD_USER = "bdd-user";
  private static final String EVENT_TYPE = "event_type";
  private static final String SOURCE_ID = "source_id";
  private static final String BDD_SEED_USER = "bdd-seed-user";
  private static final String EVENT = "event";
  private static final Instant FIXED_INSTANT = Instant.parse("2026-05-01T00:00:00Z");
  private static final String CREATED_ON = "created_on";
  private static final String META_DATA = "metadata";
  private static final String EVENT_TIMESTAMP = "event_timestamp";

  /**
   * If a scenario used {@code Given a claim exists ...} with an explicit assessment_outcome
   * follow-up (e.g. {@code And that row's assessment_outcome is "PAID_IN_FULL"}), we remember the
   * assessment id created just before so we can update it. Used by DS1812_2's outcome follow-up.
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
          Instant createdOn = Instant.parse(row.get(CREATED_ON));
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
              BDD_USER,
              FIXED_INSTANT,
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
              /* createdByUserId */ BDD_USER,
              /* createdOn */ FIXED_INSTANT);
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
                BDD_USER,
                FIXED_INSTANT);
          } else {
            insertAssessment(
                claim,
                type,
                outcome,
                reason,
                BDD_USER,
                FIXED_INSTANT,
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
          Map<String, String> submissionRow = rows.getFirst();
          if (!"SUBMISSION".equals(submissionRow.get(EVENT))) {
            throw new IllegalStateException(
                "First row of lifecycle table must be SUBMISSION; got " + submissionRow);
          }
          Claim claim = seedClaim();
          UUID claimId = claim.getId();
          registerLabel(submissionRow.get(SOURCE_ID), claimId);
          // The SUBMISSION event's source_id comes from c.id, and event_timestamp from
          // c.created_on. Pin created_on via native SQL (Claim.createdOn is @CreationTimestamp +
          // updatable=false so we must use raw SQL to override).
          Instant submissionCreatedOn = Instant.parse(submissionRow.get(CREATED_ON));
          jdbcClient
              .sql("UPDATE claims.claim SET created_on = :ts WHERE id = :id")
              .param("ts", OffsetDateTime.ofInstant(submissionCreatedOn, ZoneOffset.UTC))
              .param("id", claimId)
              .update();

          for (int i = 1; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String eventLabel = row.get(EVENT); // ASSESSMENT | VOID
            UUID assessmentId = registerLabel(row.get(SOURCE_ID));
            Instant createdOn = Instant.parse(row.get(CREATED_ON));
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
            insertAssessment(claim, type, outcome, reason, BDD_USER, createdOn, assessmentId);
          }
        });
  }

  @Given("a claim exists that has been submitted but has no claims.assessment rows")
  @Transactional
  public void aClaimExistsWithNoAssessmentRows() {
    step(
        "seed a claim with a SUBMISSION but zero assessment rows",
            this::seedClaim
        );
  }

  // ---------------------------------------------------------------------------
  // When — hit the real endpoint.
  // ---------------------------------------------------------------------------

  @When("I request the claim history timeline for the assessment and void feature")
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
          UUID expectedSourceId = requireLabel(expected.get(SOURCE_ID));
          JsonNode event = requireEventBySourceId(expectedSourceId);
          assertEnvelopeFieldEquals(event, EVENT_TYPE, expected.get(EVENT_TYPE));
          if (expected.containsKey(EVENT_TIMESTAMP)) {
            assertTimestamp(event, expected.get(EVENT_TIMESTAMP));
          }
          String actorExpected = expected.get("actor_id");
          if (actorExpected != null) {
            assertEnvelopeFieldEquals(event, "actor_id", actorExpected);
          }
        });
  }

  @Then("the response contains an event with event_type {string}")
  public void theResponseContainsAnEventWithEventType(String eventType) {
    step(
        "assert timeline contains an event with event_type=" + eventType,
        () ->
          assertThat(findFirstEventByType(eventType))
              .as("event with event_type=%s", eventType)
              .isNotNull()
        );
  }

  @Then("the response contains an event with event_type {string} and source_id {string}")
  public void theResponseContainsAnEventWithEventTypeAndSourceId(
      String eventType, String sourceIdLabel) {
    step(
        "assert timeline contains " + eventType + " event with source_id label " + sourceIdLabel,
        () -> {
          UUID sourceId = requireLabel(sourceIdLabel);
          JsonNode event = requireEventBySourceId(sourceId);
          assertEnvelopeFieldEquals(event, EVENT_TYPE, eventType);
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
          assertEnvelopeFieldEquals(event, EVENT_TYPE, eventType);
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
                          eventType.equals(e.path(EVENT_TYPE).asText())
                              && sourceId.toString().equals(e.path(SOURCE_ID).asText()));
          assertThat(found)
              .as("no %s event with source_id %s should be present", eventType, sourceId)
              .isFalse();
        });
  }

  @Then("the ASSESSMENT event metadata contains")
  public void theAssessmentEventMetadataContains(DataTable table) {
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

  @Then("the ASSESSMENT event metadata field {string} is {string}")
  public void theAssessmentEventMetadataFieldIs(String field, String expected) {
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
        () ->
          // Same assertion as absence — a fabricated placeholder would surface as any non-null
          // value under the field key.
          assertMetadataFieldAbsent(requireEventBySourceId(requireSeededAssessmentId()), field)
    );
  }

  @Then("the corresponding ASSESSMENT event's metadata does NOT contain the field {string}")
  public void theCorrespondingAssessmentEventMetadataDoesNotContainField(String field) {
    step(
        "assert corresponding ASSESSMENT event metadata does NOT carry field '" + field + "'",
        () -> {
          UUID sourceId = requireSeededAssessmentId();
          JsonNode event = requireEventBySourceId(sourceId);
          assertEnvelopeFieldEquals(event, EVENT_TYPE, "ASSESSMENT");
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
            assertEnvelopeFieldEquals(event, EVENT_TYPE, row.get(EVENT));
            assertThat(event.path(SOURCE_ID).asText())
                .as("event #%d (%s) source_id", i, row.get(EVENT))
                .isEqualTo(requireLabel(row.get(SOURCE_ID)).toString());
          }
        });
  }

  @Then("the assessment and void timeline contains no event of type {string}")
  public void theAssessmentVoidTimelineContainsNoEventOfType(String eventType) {
    step(
        "assert timeline contains NO event of type '" + eventType + "'",
        () -> {
          boolean any = events().anyMatch(e -> eventType.equals(e.path(EVENT_TYPE).asText()));
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
                        String type = e.path(EVENT_TYPE).asText();
                        if (!"ASSESSMENT".equals(type) && !"VOID".equals(type)) {
                          return false;
                        }
                        JsonNode sourceId = e.path(SOURCE_ID);
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
    submission.setCreatedByUserId(BDD_SEED_USER);
    submission.setProviderUserId(BDD_SEED_USER);
    submission.setCreatedOn(Instant.now());
    submissionRepository.saveAndFlush(submission);

    Claim claim = new Claim();
    claim.setId(Uuid7.timeBasedUuid());
    claim.setSubmission(submission);
    claim.setStatus(ClaimStatus.VALID);
    claim.setLineNumber(1);
    claim.setFeeCode("TEST");
    claim.setMatterTypeCode("MAT01");
    claim.setCaseStartDate(LocalDate.of(2025, Month.JANUARY, 1));
    claim.setCreatedByUserId(BDD_SEED_USER);
    claim.setUpdatedByUserId(BDD_SEED_USER);
    claimRepository.saveAndFlush(claim);
    currentClaimId = claim.getId();

    ClaimSummaryFee summaryFee = new ClaimSummaryFee();
    summaryFee.setId(Uuid7.timeBasedUuid());
    summaryFee.setClaim(claim);
    summaryFee.setCreatedByUserId(BDD_SEED_USER);
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
   * assessment_reason} (both are {@code @NotNull} on the entity but nullable in the DB — used by
   * DS1812_4's Scenario Outline to seed a null nullable column).
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

  private Stream<JsonNode> events() {
    assertThat(lastResponse).as("no /history response captured yet").isNotNull();
    JsonNode events = lastResponse.path("events");
    if (!events.isArray()) {
      return Stream.empty();
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
            .filter(e -> sourceId.toString().equals(e.path(SOURCE_ID).asText()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError("No event on /history timeline for source_id " + sourceId));
    lastInspectedEvent = event;
    return event;
  }

  private JsonNode findFirstEventByType(String eventType) {
    return events()
        .filter(e -> eventType.equals(e.path(EVENT_TYPE).asText()))
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
            field, event.path(SOURCE_ID).asText())
        .isEqualTo(expected);
  }

  private void assertTimestamp(JsonNode event, String expectedIso) {
    Instant expected = Instant.parse(expectedIso);
    Instant actual = Instant.parse(event.path(EVENT_TIMESTAMP).asText());
    assertThat(actual)
        .as("event_timestamp on event with source_id %s", event.path(SOURCE_ID).asText())
        .isEqualTo(expected);
  }

  private void assertMetadataContains(JsonNode event, Map<String, String> expected) {
    JsonNode metadata = event.path(META_DATA);
    assertThat(metadata.isObject())
        .as(
            "metadata container present on event with source_id %s",
            event.path(SOURCE_ID).asText())
        .isTrue();
    expected.forEach(
        (k, v) ->
            assertThat(metadata.path(k).asText())
                .as("metadata.%s on event with source_id %s", k, event.path(SOURCE_ID).asText())
                .isEqualTo(v));
  }

  private void assertMetadataFieldEquals(JsonNode event, String field, String expected) {
    JsonNode value = event.path(META_DATA).path(field);
    assertThat(value.isMissingNode() || value.isNull())
        .as("metadata.%s must be present with value '%s'", field, expected)
        .isFalse();
    assertThat(value.asText())
        .as("metadata.%s on event with source_id %s", field, event.path(SOURCE_ID).asText())
        .isEqualTo(expected);
  }

  private void assertMetadataFieldAbsent(JsonNode event, String field) {
    JsonNode metadata = event.path(META_DATA);
    JsonNode value = metadata.path(field);
    // "Absent" per the feature-file contract means the key is MISSING ENTIRELY. An explicit
    // JSON null (e.g. `"assessment_outcome": null`) is a leak, not an absence, and must fail
    // this assertion — the mapper is expected to omit the key altogether when the source
    // column is null.
    assertThat(value.isMissingNode())
        .as(
            "metadata.%s must be ABSENT (key omitted) on event with source_id %s — explicit "
                + "JSON null is a leaked placeholder and is not permitted by the contract",
            field, event.path(SOURCE_ID).asText())
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

  /**
   * Generic helper that reads a two-column DataTable into a map.
   *
   * @param table the DataTable expected to contain two columns per row
   * @param headerName a header label to ignore (case-insensitive) from the first column, or
   *     {@code null} to accept any value
   * @param nullToEmpty convert null values in the second column to empty string when true
   */
  private static Map<String, String> twoColumnMap(
      DataTable table, String headerName, boolean nullToEmpty) {
    Map<String, String> out = new HashMap<>();
    for (List<String> row : table.asLists()) {
      if (row.size() < 2) {
        continue;
      }
      String key = row.get(0);
      if (headerName != null && headerName.equalsIgnoreCase(key)) {
        continue; // header row
      }
      String value = row.get(1);
      if (value == null && nullToEmpty) {
        value = "";
      }
      out.put(key, value);
    }
    return out;
  }

  /** Reads a two-column {@code field | value} DataTable into a map. */
  private static Map<String, String> singleFieldValueMap(DataTable table) {
    return twoColumnMap(table, "field", true);
  }

  /** Reads a two-column {@code envelopeField | value} DataTable into a map. */
  private static Map<String, String> envelopeMap(DataTable table) {
    return twoColumnMap(table, "envelopeField", false);
  }

  /** Reads a two-column {@code metadataField | value} DataTable into a map. */
  private static Map<String, String> metadataMap(DataTable table) {
    return twoColumnMap(table, "metadataField", false);
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
}
