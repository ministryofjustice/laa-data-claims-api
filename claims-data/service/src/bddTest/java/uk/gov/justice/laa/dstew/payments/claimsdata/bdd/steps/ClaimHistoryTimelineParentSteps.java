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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.MatterStartRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step glue for {@code claimHistoryTimelineParent.feature} — DSTEW-1645 (parent).
 *
 * <p>Exercises the cross-cutting parent-level guarantees on {@code GET
 * /api/v1/claims/{claimId}/history} that no single child ticket (1811 / 1812 / 1813 / 1814 / 1815)
 * can prove on its own:
 *
 * <ul>
 *   <li>mixed SUBMISSION + AMENDMENT + ASSESSMENT + VOID timeline in the documented {@code
 *       event_timestamp DESC, source_id DESC} order;
 *   <li>failed amendment attempts never surface as events (they leave no {@code claim_amendment}
 *       row);
 *   <li>New Matter Starts rows are not part of the claim timeline (no branch in {@code
 *       HISTORY_SQL});
 *   <li>raw {@code request_payload} / full {@code before_state} never leak on the main response;
 *   <li>submission-only / minimal history returns exactly one SUBMISSION event.
 * </ul>
 *
 * <p>Two source scenarios are de-scoped from BDD (see feature-file banner + reporting ledger):
 * {@code @DS1645_6} (actor fallback — unreachable because every source {@code created_by_user_id}
 * column is {@code TEXT NOT NULL}) and {@code @DS1645_7} (write-to-read smoke — needs the
 * write-side amendment harness that {@code bddTest} does not have today).
 *
 * <p>Every step body wraps its logic in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures#step(String,
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.ThrowingRunnable)}
 * per the project-wide step-failure-reporting standing rule.
 */
public class ClaimHistoryTimelineParentSteps {

  private static final Set<String> AGREED_EVENT_TYPES =
      new HashSet<>(Arrays.asList("SUBMISSION", "AMENDMENT", "ASSESSMENT", "VOID"));

  private static final Set<String> ENVELOPE_FIELDS =
      new HashSet<>(
          Arrays.asList("event_type", "event_timestamp", "actor_id", "source_id", "metadata"));

  /** Sentinel strings written into request_payload / before_state so a leak is easy to detect. */
  private static final String PAYLOAD_LEAK_MARKER = "PAYLOAD_LEAK_MARKER_PARENT_1645";

  private static final String BEFORE_STATE_LEAK_MARKER = "BEFORE_STATE_LEAK_MARKER_PARENT_1645";

  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Autowired private ClaimAmendmentRepository claimAmendmentRepository;
  @Autowired private AssessmentRepository assessmentRepository;
  @Autowired private MatterStartRepository matterStartRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private BddApiStepSupport api;

  private UUID currentClaimId;
  private UUID currentClaimSummaryFeeId;
  private UUID currentSubmissionId;
  private JsonNode lastResponse;

  /** Ordered timestamps of AMENDMENT events we seeded — used by @DS1645_1 for order assertions. */
  private final List<Instant> seededAmendmentTimestamps = new ArrayList<>();

  /** Ordered timestamps of ASSESSMENT-family events we seeded. */
  private final List<Instant> seededAssessmentTimestamps = new ArrayList<>();

  // ---------------------------------------------------------------------------
  // Background — no-op locally.
  // ---------------------------------------------------------------------------

  @Given("the amendments feature flag is enabled")
  public void theAmendmentsFeatureFlagIsEnabled() {
    step(
        "no-op — the delivered `/history` endpoint is read-side and does not gate on the "
            + "amendments feature flag",
        () -> {
          // The write-side amendment endpoint gates on the flag; the read endpoint we exercise
          // here has no such gate. Documented so the reader understands the assumption.
        });
  }

  // ---------------------------------------------------------------------------
  // Givens.
  // ---------------------------------------------------------------------------

  @Given("a claim exists with the following lifecycle events in order")
  @Transactional
  public void aClaimExistsWithTheFollowingLifecycleEvents(DataTable table) {
    step(
        "seed a claim with SUBMISSION + AMENDMENT/ASSESSMENT/VOID rows per the feature-file table",
        () -> {
          seedClaim();
          List<Map<String, String>> rows = table.asMaps();
          // First row (SUBMISSION) pins the CLAIM created_on because the SQL sources
          // event_timestamp from c.created_on for SUBMISSION events.
          for (Map<String, String> row : rows) {
            String eventType = row.get("event");
            Instant when = Instant.parse(row.get("occurredAt"));
            switch (eventType) {
              case "SUBMISSION":
                pinClaimCreatedOn(when);
                break;
              case "AMENDMENT":
                persistAmendment(when, /* payloadHasMarker */ false, /* beforeHasMarker */ false);
                seededAmendmentTimestamps.add(when);
                break;
              case "ASSESSMENT":
                persistAssessment(AssessmentType.ESCAPE_CASE_ASSESSMENT, when);
                seededAssessmentTimestamps.add(when);
                break;
              case "VOID":
                persistAssessment(AssessmentType.VOID, when);
                seededAssessmentTimestamps.add(when);
                break;
              default:
                throw new IllegalStateException("Unknown event type in table: " + eventType);
            }
          }
        });
  }

  @Given("a claim exists with the following amendment attempts")
  @Transactional
  public void aClaimExistsWithAmendmentAttempts(DataTable table) {
    step(
        "seed a claim + only the successful amendment row (failed attempts leave no persisted row)",
        () -> {
          seedClaim();
          List<Map<String, String>> rows = table.asMaps();
          for (Map<String, String> row : rows) {
            String outcome = row.get("outcome");
            // Only the 'committed successfully' attempt creates a claim_amendment row on `main`.
            // Failed attempts (eligibility, PDA, FSP, final-save-guard) short-circuit before the
            // write, so seeding just the successful one exactly mirrors production.
            if (outcome != null && outcome.toLowerCase().contains("committed")) {
              Instant when =
                  Instant.parse("2026-05-01T10:00:00Z")
                      .plusSeconds(60L * Long.parseLong(row.get("attempt")));
              persistAmendment(when, false, false);
              seededAmendmentTimestamps.add(when);
            }
          }
        });
  }

  @Given("a claim exists with a linked New Matter Starts submission-level history entry")
  @Transactional
  public void aClaimExistsWithNewMatterStartsEntry() {
    step(
        "seed a claim + a MatterStart row on the parent submission",
        () -> {
          seedClaim();
          MatterStart matterStart =
              MatterStart.builder()
                  .id(Uuid7.timeBasedUuid())
                  .submission(submissionRepository.findById(currentSubmissionId).orElseThrow())
                  .scheduleReference("SCH-NMS")
                  .categoryCode("CAT01")
                  .createdByUserId("bdd-seed-user")
                  .updatedByUserId("bdd-seed-user")
                  .build();
          matterStartRepository.saveAndFlush(matterStart);
        });
  }

  @And("the claim has a successful AMENDMENT event")
  @Transactional
  public void theClaimHasASuccessfulAmendmentEvent() {
    step(
        "seed one successful AMENDMENT so the timeline is non-trivial when we assert NMS absence",
        () -> {
          Instant when = Instant.parse("2026-05-15T10:00:00Z");
          persistAmendment(when, false, false);
          seededAmendmentTimestamps.add(when);
        });
  }

  @Given("a claim exists with a successful amendment")
  @Transactional
  public void aClaimExistsWithASuccessfulAmendment() {
    step(
        "seed a claim + one successful AMENDMENT row",
        () -> {
          seedClaim();
          Instant when = Instant.parse("2026-05-01T10:00:00Z");
          persistAmendment(when, false, false);
          seededAmendmentTimestamps.add(when);
        });
  }

  @And("the amendment's `claim_amendment.request_payload` contains sensitive claim field values")
  @Transactional
  public void requestPayloadContainsSensitiveValues() {
    step(
        "overwrite the seeded amendment's request_payload with a sensitive-marker JSON so leaks "
            + "are trivially detectable",
        () ->
            jdbcClient
                .sql("UPDATE claims.claim_amendment SET request_payload = CAST(:v AS jsonb)")
                .param(
                    "v",
                    "{\"sensitive_marker\":\""
                        + PAYLOAD_LEAK_MARKER
                        + "\",\"client_ni_number\":\"AB123456C\"}")
                .update());
  }

  @And(
      "the amendment's `claim_amendment.before_state` contains a full snapshot of pre-amendment "
          + "values")
  @Transactional
  public void beforeStateContainsFullSnapshot() {
    step(
        "overwrite the seeded amendment's before_state with a full-snapshot marker JSON",
        () ->
            jdbcClient
                .sql("UPDATE claims.claim_amendment SET before_state = CAST(:v AS jsonb)")
                .param(
                    "v",
                    "{\"snapshot_marker\":\""
                        + BEFORE_STATE_LEAK_MARKER
                        + "\",\"claim_status\":\"VALID\",\"line_number\":1}")
                .update());
  }

  @Given("a claim exists that has been submitted but never amended, assessed or voided")
  @Transactional
  public void aClaimExistsThatHasBeenSubmittedOnly() {
    step("seed a claim with no amendment / assessment / void rows", this::seedClaim);
  }

  // ---------------------------------------------------------------------------
  // When.
  // ---------------------------------------------------------------------------

  @When("I request the claim history timeline")
  public void iRequestTheClaimHistoryTimeline() {
    step(
        "GET /api/v1/claims/" + currentClaimId + "/history",
        () -> lastResponse = api.getClaimHistory(requireCurrentClaimId()));
  }

  // ---------------------------------------------------------------------------
  // Thens — @DS1645_1 mixed timeline.
  // ---------------------------------------------------------------------------

  @Then("the response contains events of types SUBMISSION, AMENDMENT, ASSESSMENT, VOID")
  public void responseContainsEventsOfAllFourTypes() {
    step(
        "assert timeline contains at least one event of each of the 4 agreed types",
        () -> {
          Set<String> types = eventTypesPresent();
          assertThat(types)
              .as("event types present on timeline")
              .contains("SUBMISSION", "AMENDMENT", "ASSESSMENT", "VOID");
        });
  }

  @Then("the events are returned in the documented deterministic order")
  public void eventsReturnedInDeterministicOrder() {
    step(
        "assert timeline events are ordered by event_timestamp DESC, source_id DESC",
        () -> {
          List<JsonNode> events = eventList();
          for (int i = 1; i < events.size(); i++) {
            JsonNode previous = events.get(i - 1);
            JsonNode current = events.get(i);
            Instant prevTs = Instant.parse(previous.path("event_timestamp").asText());
            Instant currTs = Instant.parse(current.path("event_timestamp").asText());
            // Primary sort: event_timestamp DESC. On a tie the SQL sorts by source_id DESC.
            if (prevTs.equals(currTs)) {
              assertThat(current.path("source_id").asText())
                  .as("tie-break source_id DESC at index %d", i)
                  .isLessThanOrEqualTo(previous.path("source_id").asText());
            } else {
              assertThat(prevTs).as("event_timestamp DESC at index %d", i).isAfter(currTs);
            }
          }
        });
  }

  @Then("no event type outside the agreed set \\(SUBMISSION, AMENDMENT, ASSESSMENT, VOID) appears")
  public void noEventTypeOutsideAgreedSetAppears() {
    step(
        "assert the timeline emits only agreed event types (no unknown / synthesised type slips "
            + "through)",
        () ->
            assertThat(eventTypesPresent())
                .as("emitted event types must be a subset of the agreed set")
                .isSubsetOf(AGREED_EVENT_TYPES));
  }

  // ---------------------------------------------------------------------------
  // Thens — @DS1645_2 failed amendment attempts.
  // ---------------------------------------------------------------------------

  @Then("the response contains exactly one AMENDMENT event")
  public void responseContainsExactlyOneAmendmentEvent() {
    step(
        "assert timeline has exactly one AMENDMENT event",
        () -> {
          long amendmentCount =
              eventList().stream()
                  .filter(e -> "AMENDMENT".equals(e.path("event_type").asText()))
                  .count();
          assertThat(amendmentCount).as("AMENDMENT event count").isEqualTo(1L);
        });
  }

  @Then("no AMENDMENT event exists for any of the four failed attempts")
  public void noAmendmentEventExistsForFailedAttempts() {
    step(
        "assert AMENDMENT count matches only the seeded (committed) attempt count — failed "
            + "attempts add zero events",
        () -> {
          long amendmentCount =
              eventList().stream()
                  .filter(e -> "AMENDMENT".equals(e.path("event_type").asText()))
                  .count();
          assertThat(amendmentCount)
              .as("AMENDMENT events must equal the number of persisted (successful) attempts")
              .isEqualTo((long) seededAmendmentTimestamps.size());
        });
  }

  // ---------------------------------------------------------------------------
  // Thens — @DS1645_3 New Matter Starts exclusion.
  // ---------------------------------------------------------------------------

  @Then("the response contains no event of type {string}")
  public void responseContainsNoEventOfType(String eventType) {
    step(
        "assert timeline emits no event with event_type=" + eventType,
        () -> {
          boolean any =
              eventList().stream().anyMatch(e -> eventType.equals(e.path("event_type").asText()));
          assertThat(any).as("no %s event should be present", eventType).isFalse();
        });
  }

  @Then("the response contains no submission-level New Matter Starts metadata")
  public void responseContainsNoNewMatterStartsMetadata() {
    step(
        "assert no event's metadata bag carries NMS-shaped fields (schedule_reference, "
            + "matter_start_id, matter_type / category_code)",
        () -> {
          Set<String> bannedKeys =
              new HashSet<>(
                  Arrays.asList(
                      "matter_start_id", "new_matter_starts", "matter_type", "schedule_reference"));
          for (JsonNode event : eventList()) {
            JsonNode metadata = event.path("metadata");
            if (!metadata.isObject()) {
              continue;
            }
            metadata
                .fieldNames()
                .forEachRemaining(
                    key ->
                        assertThat(bannedKeys)
                            .as(
                                "event %s metadata key '%s' must not be an NMS leak",
                                event.path("event_type").asText(), key)
                            .doesNotContain(key));
          }
        });
  }

  // ---------------------------------------------------------------------------
  // Thens — @DS1645_4 payload leak.
  // ---------------------------------------------------------------------------

  @Then("the AMENDMENT event does not contain the raw amendment request payload")
  public void amendmentEventDoesNotContainRawRequestPayload() {
    step(
        "assert AMENDMENT event's serialised JSON does not contain the request_payload leak marker",
        () -> {
          JsonNode event = requireFirstAmendmentEvent();
          assertThat(event.toString())
              .as("AMENDMENT event JSON body")
              .doesNotContain(PAYLOAD_LEAK_MARKER);
        });
  }

  @Then("the AMENDMENT event does not contain the full before-state snapshot")
  public void amendmentEventDoesNotContainFullBeforeStateSnapshot() {
    step(
        "assert AMENDMENT event's serialised JSON does not contain the before_state leak marker",
        () -> {
          JsonNode event = requireFirstAmendmentEvent();
          assertThat(event.toString())
              .as("AMENDMENT event JSON body")
              .doesNotContain(BEFORE_STATE_LEAK_MARKER);
        });
  }

  @Then("only per-field before\\/after values from the versioned diff are exposed")
  public void onlyPerFieldBeforeAfterFromVersionedDiffAreExposed() {
    step(
        "assert AMENDMENT event carries only its `metadata.changes[]` — no envelope key called "
            + "'request_payload' or 'before_state', and no top-level payload leak on the event",
        () -> {
          JsonNode event = requireFirstAmendmentEvent();
          Set<String> envelopeKeys = new HashSet<>();
          event.fieldNames().forEachRemaining(envelopeKeys::add);
          assertThat(envelopeKeys)
              .as("AMENDMENT event envelope carries only agreed contract fields")
              .isSubsetOf(ENVELOPE_FIELDS);
          JsonNode metadata = event.path("metadata");
          assertThat(metadata.has("request_payload"))
              .as("metadata must not carry 'request_payload'")
              .isFalse();
          assertThat(metadata.has("before_state"))
              .as("metadata must not carry 'before_state'")
              .isFalse();
        });
  }

  // ---------------------------------------------------------------------------
  // Thens — @DS1645_5 submission-only history.
  // ---------------------------------------------------------------------------

  @Then("the response contains exactly one event of type SUBMISSION")
  public void responseContainsExactlyOneSubmissionEvent() {
    step(
        "assert exactly one SUBMISSION event on the minimal-history timeline",
        () -> {
          List<JsonNode> events = eventList();
          assertThat(events).as("total events").hasSize(1);
          assertThat(events.get(0).path("event_type").asText())
              .as("only event's type")
              .isEqualTo("SUBMISSION");
        });
  }

  @Then("the response contains no AMENDMENT, ASSESSMENT or VOID events")
  public void responseContainsNoOtherEvents() {
    step(
        "assert no AMENDMENT / ASSESSMENT / VOID events on minimal history",
        () -> {
          Set<String> types = eventTypesPresent();
          assertThat(types)
              .as("event types on minimal-history timeline")
              .doesNotContain("AMENDMENT", "ASSESSMENT", "VOID");
        });
  }

  @Then("the response shape matches the documented contract")
  public void responseShapeMatchesDocumentedContract() {
    step(
        "assert the response envelope keys are exactly the agreed contract set",
        () -> {
          Set<String> topLevelKeys = new HashSet<>();
          lastResponse.fieldNames().forEachRemaining(topLevelKeys::add);
          // The delivered ClaimHistoryResultSet emits {claimId, events}. Both keys must be present.
          assertThat(topLevelKeys).as("response envelope keys").contains("claim_id", "events");
          // Every event must carry only agreed envelope fields.
          for (JsonNode event : eventList()) {
            Set<String> envelopeKeys = new HashSet<>();
            event.fieldNames().forEachRemaining(envelopeKeys::add);
            assertThat(envelopeKeys).as("event envelope keys").isSubsetOf(ENVELOPE_FIELDS);
          }
        });
  }

  // ---------------------------------------------------------------------------
  // Seeding helpers.
  // ---------------------------------------------------------------------------

  private void seedClaim() {
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
    currentSubmissionId = submission.getId();

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
  }

  private void pinClaimCreatedOn(Instant when) {
    jdbcClient
        .sql("UPDATE claims.claim SET created_on = :ts WHERE id = :id")
        .param("ts", OffsetDateTime.ofInstant(when, ZoneOffset.UTC))
        .param("id", currentClaimId)
        .update();
  }

  private void persistAmendment(Instant when, boolean payloadHasMarker, boolean beforeHasMarker) {
    Claim claim = claimRepository.findById(currentClaimId).orElseThrow();
    ClaimAmendment amendment =
        ClaimAmendment.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .requestedByCode("PROVIDER")
            .amendmentReasonCode("PROVIDER_ERROR")
            .beforeState(beforeHasMarker ? "{\"leak\":\"" + BEFORE_STATE_LEAK_MARKER + "\"}" : "{}")
            .requestPayload(payloadHasMarker ? "{\"leak\":\"" + PAYLOAD_LEAK_MARKER + "\"}" : "{}")
            .diff("{\"schema_version\":1,\"changes\":[]}")
            .createdByUserId("bdd-seed-user")
            .createdOn(when)
            .build();
    claimAmendmentRepository.saveAndFlush(amendment);
    // Pin created_on — CreationTimestamp behaviour on ClaimAmendment sets this on persist
    // (V39 also has DEFAULT now() at DB level), so we overwrite defensively.
    jdbcClient
        .sql("UPDATE claims.claim_amendment SET created_on = :ts WHERE id = :id")
        .param("ts", OffsetDateTime.ofInstant(when, ZoneOffset.UTC))
        .param("id", amendment.getId())
        .update();
  }

  private void persistAssessment(AssessmentType type, Instant when) {
    Claim claim = claimRepository.findById(currentClaimId).orElseThrow();
    ClaimSummaryFee summaryFee =
        claimSummaryFeeRepository.findById(currentClaimSummaryFeeId).orElseThrow();
    Assessment assessment =
        Assessment.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .claimSummaryFee(summaryFee)
            .assessmentType(type)
            .assessmentOutcome(type == AssessmentType.VOID ? null : AssessmentOutcome.PAID_IN_FULL)
            .assessmentReason(type == AssessmentType.VOID ? "Voided in parent scenario" : "Seed")
            .assessedTotalVat(BigDecimal.ZERO)
            .assessedTotalInclVat(BigDecimal.ZERO)
            .allowedTotalVat(BigDecimal.ZERO)
            .allowedTotalInclVat(BigDecimal.ZERO)
            .createdByUserId("bdd-seed-user")
            .updatedByUserId("bdd-seed-user")
            .build();
    assessmentRepository.saveAndFlush(assessment);
    jdbcClient
        .sql("UPDATE claims.assessment SET created_on = :ts WHERE id = :id")
        .param("ts", OffsetDateTime.ofInstant(when, ZoneOffset.UTC))
        .param("id", assessment.getId())
        .update();
  }

  // ---------------------------------------------------------------------------
  // Inspection helpers.
  // ---------------------------------------------------------------------------

  private List<JsonNode> eventList() {
    assertThat(lastResponse).as("no /history response captured yet").isNotNull();
    JsonNode events = lastResponse.path("events");
    if (!events.isArray()) {
      return List.of();
    }
    List<JsonNode> out = new ArrayList<>();
    events.forEach(out::add);
    return out;
  }

  private Set<String> eventTypesPresent() {
    Set<String> types = new HashSet<>();
    for (JsonNode event : eventList()) {
      types.add(event.path("event_type").asText());
    }
    return types;
  }

  private JsonNode requireFirstAmendmentEvent() {
    return eventList().stream()
        .filter(e -> "AMENDMENT".equals(e.path("event_type").asText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No AMENDMENT event on /history timeline"));
  }

  private UUID requireCurrentClaimId() {
    if (currentClaimId == null) {
      throw new AssertionError(
          "No claim id has been established yet — expected a prior Given step.");
    }
    return currentClaimId;
  }
}
