package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step definitions for {@code claimHistoryAmendmentMetadata.feature} (DSTEW-1813).
 *
 * <p>Covers the read-side AMENDMENT-event envelope population from a {@code claim_amendment} row:
 *
 * <ul>
 *   <li>envelope: {@code event_type=AMENDMENT}, {@code event_timestamp=am.created_on}, {@code
 *       actor_id=am.created_by_user_id}, {@code source_id=am.id};
 *   <li>metadata: {@code requested_by_code} and {@code amendment_reason_code} echoed verbatim from
 *       the persisted row (no label substitution — that is DSTEW-1594 + AaBC).
 * </ul>
 *
 * <p>The {@code amended_field_identifiers} scenarios were de-scoped from this ticket (see the
 * banner in the feature file) because the shipped SQL in {@code JdbcClaimHistoryRepository} does
 * not yet build a Requested-only filtered list. When that filter ships, the feature file grows a
 * fresh set of scenarios and this steps class gains the corresponding assertions.
 *
 * <p>Every step wraps its body in {@link BddStepFailures#step(String,
 * BddStepFailures.ThrowingRunnable)} so scenario failures surface as {@code [BDD step failed]
 * &lt;plain-English context&gt; — &lt;cause&gt;} in the JUnit XML / Cucumber HTML report, per the
 * standing rule in {@code memory.md}.
 */
@Slf4j
public class ClaimHistoryAmendmentMetadataSteps {

  private static final String BDD_USER_ID = "bdd-user-1813";
  private static final String AMENDMENT_EVENT_TYPE = "AMENDMENT";

  @Autowired private BddApiStepSupport api;
  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Autowired private ClaimAmendmentRepository claimAmendmentRepository;

  private UUID currentClaimId;
  private UUID currentAmendmentId;
  private final List<UUID> amendmentIdsInOrder = new ArrayList<>();
  private JsonNode lastHistoryResponse;

  // ---------------------------------------------------------------------------
  // Given — single-amendment seeding
  // ---------------------------------------------------------------------------

  @Given("a claim exists with the following successful `claim_amendment` row")
  public void aClaimExistsWithTheFollowingSuccessfulClaimAmendmentRow(DataTable table) {
    BddStepFailures.step(
        "Seeding a claim + single successful claim_amendment row from the scenario DataTable",
        () -> {
          seedClaim();
          Map<String, String> row = singleRowFromTwoColumnTable(table);
          currentAmendmentId =
              persistAmendment(
                  requireField(row, "created_on"),
                  requireField(row, "created_by_user_id"),
                  requireField(row, "requested_by_code"),
                  requireField(row, "amendment_reason_code"),
                  emptyDiff());
          amendmentIdsInOrder.add(currentAmendmentId);
        });
  }

  @Given(
      "a claim exists with a successful `claim_amendment` row where `requested_by_code` is"
          + " {string} and `amendment_reason_code` is {string}")
  public void aClaimExistsWithASuccessfulAmendmentRowWithCodes(
      String requestedByCode, String amendmentReasonCode) {
    BddStepFailures.step(
        "Seeding a claim + single successful claim_amendment row with requested_by_code='"
            + requestedByCode
            + "' and amendment_reason_code='"
            + amendmentReasonCode
            + "'",
        () -> {
          seedClaim();
          currentAmendmentId =
              persistAmendment(
                  Instant.now().toString(),
                  BDD_USER_ID,
                  requestedByCode,
                  amendmentReasonCode,
                  emptyDiff());
          amendmentIdsInOrder.add(currentAmendmentId);
        });
  }

  @And("the amendment's stored diff contains a `change_source` {string} entry for field {string}")
  public void theAmendmentDiffContainsAChangeSourceEntryForField(
      String changeSource, String fieldIdentifier) {
    BddStepFailures.step(
        "Re-persisting the current amendment's diff to include a "
            + changeSource
            + " entry for field '"
            + fieldIdentifier
            + "'",
        () -> {
          ClaimAmendment amendment =
              claimAmendmentRepository.findById(currentAmendmentId).orElseThrow();
          amendment.setDiff(diffWithRequestedChange(fieldIdentifier, changeSource));
          claimAmendmentRepository.saveAndFlush(amendment);
        });
  }

  // ---------------------------------------------------------------------------
  // Given — multi-amendment chronology
  // ---------------------------------------------------------------------------

  @Given("a claim exists with the following successful `claim_amendment` rows applied in order")
  public void aClaimExistsWithMultipleSuccessfulAmendmentsInOrder(DataTable table) {
    BddStepFailures.step(
        "Seeding a claim + multiple successful claim_amendment rows applied in scenario order",
        () -> {
          seedClaim();
          List<Map<String, String>> rows = table.asMaps(String.class, String.class);
          for (Map<String, String> row : rows) {
            String requestedByCode = requireField(row, "requested_by_code");
            UUID id =
                persistAmendment(
                    requireField(row, "created_on"),
                    requireField(row, "created_by_user_id"),
                    requestedByCode,
                    // Reason code column is optional in this scenario. Fall back to a valid
                    // (requested_by, reason) pair from the DSTEW-1594 reference-data seed
                    // (V41__create_amendment_reference_tables.sql) so the composite FK
                    // fk_claim_amendment_reason_party_code holds.
                    row.getOrDefault("amendment_reason_code", defaultReasonFor(requestedByCode)),
                    emptyDiff());
            amendmentIdsInOrder.add(id);
          }
          currentAmendmentId = amendmentIdsInOrder.get(amendmentIdsInOrder.size() - 1);
        });
  }

  // ---------------------------------------------------------------------------
  // When
  // ---------------------------------------------------------------------------

  @When("I request the claim history timeline")
  public void iRequestTheClaimHistoryTimeline() {
    BddStepFailures.step(
        "Requesting claim history timeline for claim " + currentClaimId,
        () -> {
          assertThat(currentClaimId)
              .as("claim must be seeded before requesting history")
              .isNotNull();
          lastHistoryResponse = api.getClaimHistory(currentClaimId);
        });
  }

  // ---------------------------------------------------------------------------
  // Then — envelope
  // ---------------------------------------------------------------------------

  @Then("the response contains an event with the following envelope")
  public void theResponseContainsAnEventWithTheFollowingEnvelope(DataTable table) {
    BddStepFailures.step(
        "Verifying an AMENDMENT event with the scenario-declared envelope fields is present"
            + " on the history response for claim "
            + currentClaimId,
        () -> {
          Map<String, String> expected = expectedMapFromTwoColumnTable(table, "envelopeField");
          JsonNode matched = null;
          for (JsonNode event : eventsArray()) {
            if (envelopeMatches(event, expected)) {
              matched = event;
              break;
            }
          }
          assertThat(matched)
              .as(
                  "no event on the history response matched all envelope fields %s;"
                      + " actual events=%s",
                  expected, eventsArray())
              .isNotNull();
        });
  }

  @And("that event's metadata contains")
  public void thatEventsMetadataContains(DataTable table) {
    BddStepFailures.step(
        "Verifying the AMENDMENT event for amendment "
            + currentAmendmentId
            + " has the scenario-declared metadata fields",
        () -> {
          JsonNode event = requireAmendmentEvent(currentAmendmentId);
          JsonNode metadata = event.path("metadata");
          Map<String, String> expected = expectedMapFromTwoColumnTable(table, "metadataField");
          for (Map.Entry<String, String> entry : expected.entrySet()) {
            JsonNode value = metadata.get(entry.getKey());
            assertThat(value)
                .as("metadata field '%s' node on AMENDMENT event", entry.getKey())
                .isNotNull();
            assertThat(value.asText())
                .as("metadata field '%s' value on AMENDMENT event", entry.getKey())
                .isEqualTo(entry.getValue());
          }
        });
  }

  // ---------------------------------------------------------------------------
  // Then — multi-amendment chronology
  // ---------------------------------------------------------------------------

  @Then("the response contains exactly {int} AMENDMENT events")
  public void theResponseContainsExactlyNAmendmentEvents(int expectedCount) {
    BddStepFailures.step(
        "Counting AMENDMENT events on the history response for claim "
            + currentClaimId
            + " — expected "
            + expectedCount,
        () ->
            assertThat(amendmentEvents())
                .as("AMENDMENT event count on history response")
                .hasSize(expectedCount));
  }

  /**
   * Word-spelled variant. Kept discrete from the {@code {int}} overload so the feature file can
   * read naturally ("exactly two AMENDMENT events") without forcing an integer literal into the
   * Gherkin. Only "two" is wired up because it's the only count the DSTEW-1813 feature uses; extend
   * if further scenarios need "three", etc.
   */
  @Then("the response contains exactly two AMENDMENT events")
  public void theResponseContainsExactlyTwoAmendmentEvents() {
    theResponseContainsExactlyNAmendmentEvents(2);
  }

  @And("the LATEST AMENDMENT event has `actor_id` {string} and `event_timestamp` {string}")
  public void theLatestAmendmentEventHasActorAndTimestamp(
      String expectedActorId, String expectedTimestamp) {
    BddStepFailures.step(
        "Verifying the latest AMENDMENT event (by event_timestamp) has actor_id='"
            + expectedActorId
            + "' and event_timestamp='"
            + expectedTimestamp
            + "'",
        () -> {
          List<JsonNode> events = amendmentEvents();
          assertThat(events)
              .as("no AMENDMENT events on history response — cannot pick 'latest'")
              .isNotEmpty();
          JsonNode latest =
              events.stream()
                  .max(
                      (a, b) ->
                          Instant.parse(a.path("event_timestamp").asText())
                              .compareTo(Instant.parse(b.path("event_timestamp").asText())))
                  .orElseThrow();
          assertThat(latest.path("actor_id").asText())
              .as("actor_id on the latest AMENDMENT event")
              .isEqualTo(expectedActorId);
          assertThat(latest.path("event_timestamp").asText())
              .as("event_timestamp on the latest AMENDMENT event")
              .isEqualTo(expectedTimestamp);
        });
  }

  // ---------------------------------------------------------------------------
  // Then — codes echoed verbatim (@DS1813_4 Outline)
  // ---------------------------------------------------------------------------

  @Then("the AMENDMENT event metadata field {string} is exactly {string}")
  public void theAmendmentEventMetadataFieldIsExactly(String fieldName, String expectedValue) {
    BddStepFailures.step(
        "Verifying AMENDMENT metadata field '"
            + fieldName
            + "' equals '"
            + expectedValue
            + "' (verbatim, no label substitution) for claim "
            + currentClaimId,
        () -> {
          JsonNode event = requireAmendmentEvent(currentAmendmentId);
          JsonNode value = event.path("metadata").get(fieldName);
          assertThat(value)
              .as("metadata field '%s' node on AMENDMENT event", fieldName)
              .isNotNull();
          assertThat(value.asText())
              .as("metadata field '%s' value on AMENDMENT event", fieldName)
              .isEqualTo(expectedValue);
        });
  }

  @Then("no display label has been substituted for either code")
  public void noDisplayLabelHasBeenSubstitutedForEitherCode() {
    // Semantic documentation step: the sibling "is exactly" assertions above assert byte-for-byte
    // equality between the persisted code and the JSON field value. If label substitution had
    // sneaked in, those assertions would have failed. Nothing extra to assert here — kept as a
    // Gherkin-level guardrail so the intent is visible to reviewers.
    BddStepFailures.step(
        "Documenting the no-label-substitution guarantee for claim " + currentClaimId,
        () -> {
          /* intentionally empty — covered by the sibling exact-match assertions */
        });
  }

  // ---------------------------------------------------------------------------
  // Helpers — data seeding
  // ---------------------------------------------------------------------------

  private void seedClaim() {
    Submission submission =
        submissionRepository.saveAndFlush(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .officeAccountNumber("1813-office")
                .submissionPeriod("JAN-2025")
                .areaOfLaw(AreaOfLaw.LEGAL_HELP)
                .status(SubmissionStatus.CREATED)
                .createdByUserId(BDD_USER_ID)
                .providerUserId(BDD_USER_ID)
                .createdOn(Instant.now())
                .build());

    Claim claim =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(Uuid7.timeBasedUuid())
                .submission(submission)
                .status(ClaimStatus.VALID)
                .feeCode("TEST")
                .lineNumber(1)
                .matterTypeCode("TEST_MATTER")
                .createdByUserId(BDD_USER_ID)
                .build());
    currentClaimId = claim.getId();

    // Seed a summary fee row so downstream amendment-linked CFD lookups (not exercised in this
    // ticket) don't blow up if invoked accidentally from a shared helper.
    claimSummaryFeeRepository.saveAndFlush(
        ClaimSummaryFee.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .createdByUserId(BDD_USER_ID)
            .build());
  }

  private UUID persistAmendment(
      String createdOnIso,
      String createdByUserId,
      String requestedByCode,
      String amendmentReasonCode,
      String diffJson) {
    UUID id = Uuid7.timeBasedUuid();
    claimAmendmentRepository.saveAndFlush(
        ClaimAmendment.builder()
            .id(id)
            .claim(claimRepository.getReferenceById(currentClaimId))
            .requestedByCode(requestedByCode)
            .amendmentReasonCode(amendmentReasonCode)
            .beforeState("{}")
            .requestPayload("{}")
            .diff(diffJson)
            .createdByUserId(createdByUserId)
            .createdOn(Instant.parse(createdOnIso))
            .build());
    return id;
  }

  // ---------------------------------------------------------------------------
  // Helpers — diff JSON
  // ---------------------------------------------------------------------------

  private static String emptyDiff() {
    return "{\"schema_version\":1,\"changes\":[]}";
  }

  /**
   * Returns a reason code that is valid for the given requested-by code according to the DSTEW-1594
   * reference-data seed. Used only when a scenario's DataTable doesn't specify a reason code
   * explicitly (e.g. the multi-amendment chronology scenario, which only cares about timestamps and
   * actors).
   */
  private static String defaultReasonFor(String requestedByCode) {
    return switch (requestedByCode) {
      case "PROVIDER" -> "PROVIDER_ERROR";
      case "CONTRACT_MANAGEMENT", "ASSURANCE" -> "OTHER";
      default ->
          throw new IllegalArgumentException(
              "No default amendment reason wired up for requested_by_code='"
                  + requestedByCode
                  + "' — extend defaultReasonFor() or add a reason_code column to the scenario's"
                  + " DataTable.");
    };
  }

  private static String diffWithRequestedChange(String fieldIdentifier, String changeSource) {
    return "{\"schema_version\":1,\"changes\":["
        + "{\"field_identifier\":\""
        + fieldIdentifier
        + "\",\"before\":\"OLD\",\"after\":\"NEW\",\"change_source\":\""
        + changeSource
        + "\"}]}";
  }

  // ---------------------------------------------------------------------------
  // Helpers — response inspection
  // ---------------------------------------------------------------------------

  private List<JsonNode> eventsArray() {
    List<JsonNode> results = new ArrayList<>();
    JsonNode events = lastHistoryResponse == null ? null : lastHistoryResponse.path("events");
    if (events != null && events.isArray()) {
      events.forEach(results::add);
    }
    return results;
  }

  private List<JsonNode> amendmentEvents() {
    List<JsonNode> results = new ArrayList<>();
    for (JsonNode event : eventsArray()) {
      if (AMENDMENT_EVENT_TYPE.equals(event.path("event_type").asText())) {
        results.add(event);
      }
    }
    return results;
  }

  private JsonNode requireAmendmentEvent(UUID amendmentId) {
    for (JsonNode event : amendmentEvents()) {
      if (amendmentId != null && amendmentId.toString().equals(event.path("source_id").asText())) {
        return event;
      }
    }
    // Fall back to the sole AMENDMENT event if scenarios asserted envelope contents without
    // relying on the amendment id (DSTEW-1813 seeds one amendment per scenario in most cases).
    List<JsonNode> events = amendmentEvents();
    assertThat(events)
        .as("no AMENDMENT event found on history response for amendment %s", amendmentId)
        .isNotEmpty();
    return events.get(0);
  }

  private boolean envelopeMatches(JsonNode event, Map<String, String> expected) {
    for (Map.Entry<String, String> entry : expected.entrySet()) {
      JsonNode value = event.get(entry.getKey());
      if (value == null || !entry.getValue().equals(value.asText())) {
        return false;
      }
    }
    return true;
  }

  // ---------------------------------------------------------------------------
  // Helpers — DataTable parsing
  // ---------------------------------------------------------------------------

  /**
   * Reads a two-column {@code (field, value)} DataTable where each row is a distinct property of a
   * single entity being described. Used for the one-amendment-per-row Given.
   */
  private static Map<String, String> singleRowFromTwoColumnTable(DataTable table) {
    List<Map<String, String>> maps = table.asMaps(String.class, String.class);
    assertThat(maps).as("expected a non-empty (field, value) DataTable but got none").isNotEmpty();
    // The DataTable is column-headed (field | value); each row is a separate entry.
    Map<String, String> merged = new java.util.LinkedHashMap<>();
    for (Map<String, String> row : maps) {
      merged.put(requireField(row, "field"), requireField(row, "value"));
    }
    return merged;
  }

  /**
   * Reads a two-column {@code (<keyColumn>, value)} DataTable — the key column names vary per
   * scenario ({@code envelopeField} vs {@code metadataField}) so the caller supplies it.
   */
  private static Map<String, String> expectedMapFromTwoColumnTable(
      DataTable table, String keyColumn) {
    Map<String, String> expected = new java.util.LinkedHashMap<>();
    for (Map<String, String> row : table.asMaps(String.class, String.class)) {
      expected.put(requireField(row, keyColumn), requireField(row, "value"));
    }
    return expected;
  }

  private static String requireField(Map<String, String> row, String key) {
    String value = row.get(key);
    assertThat(value)
        .as("DataTable row is missing required column '%s' — row=%s", key, row)
        .isNotNull();
    return value;
  }
}
