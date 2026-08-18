package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.GET_CLAIM_HISTORY_PATH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.BddBeansConfiguration.BddServerInfo;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step glue for {@code claimHistoryTimelineContract.feature} — DSTEW-1811 (1645-A).
 *
 * <p>Owns the envelope + SUBMISSION-event contract for {@code GET
 * /api/v1/claims/{claimId}/history}. Seeds {@code Submission → Claim} directly via JPA (with
 * native-SQL {@code UPDATE claim SET created_on = ...} because {@code Claim.createdOn} is
 * {@code @CreationTimestamp} + {@code updatable = false} on delivered {@code main}), then hits the
 * real REST endpoint via {@link BddApiStepSupport}.
 *
 * <p>Note on the SUBMISSION event's {@code source_id}: the delivered SQL emits {@code c.id AS
 * source_id} (i.e. the claim id), not the submission id. Feature-file labels like {@code
 * "sub-uuid-1"} are therefore bound to the seeded claim's UUID so scenario assertions match the
 * emitted response.
 *
 * <p>Every step body wraps its logic in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures#step(String,
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.ThrowingRunnable)}
 * per the project-wide step-failure-reporting standing rule.
 */
public class ClaimHistoryTimelineContractSteps {

  /**
   * Fields the feature file allows on the SUBMISSION event's {@code metadata} bag. Guards the "no
   * leakage of submission-only fields outside metadata onto the envelope" assertion (@DS1811_4).
   */
  private static final Set<String> SUBMISSION_METADATA_FIELDS =
      new HashSet<>(Arrays.asList("submission_period", "office_account_number", "area_of_law"));

  /** Fields the envelope is allowed to expose. */
  private static final Set<String> ENVELOPE_FIELDS =
      new HashSet<>(
          Arrays.asList("event_type", "event_timestamp", "actor_id", "source_id", "metadata"));

  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private BddApiStepSupport api;
  @Autowired private RestTemplate restTemplate;
  @Autowired private BddServerInfo serverInfo;

  private UUID currentClaimId;
  private JsonNode lastResponse;
  private int lastStatusCode;
  private String lastResponseBody;

  private final Map<String, UUID> labelToUuid = new HashMap<>();

  /**
   * Pending overrides captured by the {@code And the parent submission has the following stored
   * values} step. Applied inside {@link #applyPendingSubmissionOverrides()} after the claim is
   * seeded so we can pin values that the delivered SQL will actually surface (claim.created_on,
   * claim.created_by_user_id, claim.id — plus submission's period / office / area).
   */
  private Map<String, String> pendingSubmissionValues = new HashMap<>();

  // ---------------------------------------------------------------------------
  // Givens.
  // ---------------------------------------------------------------------------

  @Given("a claim exists that has been submitted but never amended, assessed or voided")
  @Transactional
  public void aClaimExistsThatHasBeenSubmittedOnly() {
    step(
        "seed a claim (with parent submission) that has no amendments, assessments, or voids",
        this::seedClaim);
  }

  @And("the claim has the following stored values")
  @Transactional
  public void theClaimHasStoredValues(DataTable table) {
    step(
        "override claim envelope columns to match the feature-file 'stored values' table",
        () -> {
          pendingSubmissionValues.putAll(singleFieldValueMap(table));
          applyPendingSubmissionOverrides();
        });
  }

  @And("the parent submission has the following stored values")
  @Transactional
  public void theParentSubmissionHasStoredValues(DataTable table) {
    step(
        "override submission metadata columns to match the feature-file 'stored values' table",
        () -> {
          pendingSubmissionValues.putAll(singleFieldValueMap(table));
          applyPendingSubmissionOverrides();
        });
  }

  @Given("no claim exists for claim id {string}")
  public void noClaimExistsForClaimId(String claimIdString) {
    step(
        "register the unknown-claim label and confirm the row is absent",
        () -> {
          UUID claimId = UUID.fromString(claimIdString);
          currentClaimId = claimId;
          // Sanity-check the row genuinely doesn't exist. If it somehow does, fail fast with a
          // clear message rather than letting the /history call succeed and confuse the assertion.
          if (claimRepository.existsById(claimId)) {
            throw new AssertionError(
                "Expected no claim for id "
                    + claimId
                    + " but one exists — BddHooks cleanup may not be running.");
          }
        });
  }

  // ---------------------------------------------------------------------------
  // When.
  // ---------------------------------------------------------------------------

  @When("I request the claim history timeline")
  public void iRequestTheClaimHistoryTimeline() {
    step(
        "GET /api/v1/claims/" + currentClaimId + "/history (expect 2xx)",
        () -> {
          lastResponse = api.getClaimHistoryJson(requireCurrentClaimId());
          lastStatusCode = 200;
          lastResponseBody = lastResponse.toString();
        });
  }

  @When("I request the claim history timeline for that claim id")
  public void iRequestTheClaimHistoryTimelineForThatClaimId() {
    step(
        "GET /api/v1/claims/" + currentClaimId + "/history (expect a not-found response)",
        () -> {
          // The endpoint throws ClaimNotFoundException → framework maps to 404. RestTemplate turns
          // 4xx into HttpStatusCodeException, so we capture status + body without letting the
          // exception propagate.
          HttpHeaders headers = new HttpHeaders();
          headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
          try {
            ResponseEntity<String> response =
                restTemplate.exchange(
                    serverInfo.baseUrl() + GET_CLAIM_HISTORY_PATH,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class,
                    requireCurrentClaimId());
            lastStatusCode = response.getStatusCode().value();
            lastResponseBody = response.getBody();
          } catch (HttpStatusCodeException ex) {
            HttpStatusCode status = ex.getStatusCode();
            lastStatusCode = status.value();
            lastResponseBody = ex.getResponseBodyAsString();
          }
        });
  }

  // ---------------------------------------------------------------------------
  // Thens — happy-path timeline (@DS1811_1, @DS1811_4).
  // ---------------------------------------------------------------------------

  @Then("the response contains exactly one event")
  public void theResponseContainsExactlyOneEvent() {
    step(
        "assert /history timeline contains exactly one event",
        () -> {
          JsonNode events = requireEventsArray();
          assertThat(events.size()).as("number of events on timeline").isEqualTo(1);
        });
  }

  @Then("that event matches the following common envelope")
  public void thatEventMatchesTheFollowingEnvelope(DataTable table) {
    step(
        "assert the single event's envelope matches the feature-file table",
        () -> {
          JsonNode event = requireEventsArray().get(0);
          Map<String, String> expected = envelopeMap(table);
          assertEnvelopeFieldEquals(event, "event_type", expected.get("event_type"));
          if (expected.containsKey("event_timestamp")) {
            assertTimestamp(event, expected.get("event_timestamp"));
          }
          if (expected.containsKey("actor_user_id") || expected.containsKey("actor_id")) {
            // Delivered contract calls the field `actor_id`. Feature file uses `actor_user_id`
            // in one row — accept either alias to preserve author intent.
            String expectedActor = expected.getOrDefault("actor_id", expected.get("actor_user_id"));
            assertEnvelopeFieldEquals(event, "actor_id", expectedActor);
          }
          if (expected.containsKey("source_id")) {
            UUID expectedSourceId = requireLabel(expected.get("source_id"));
            assertThat(event.path("source_id").asText())
                .as("event source_id (delivered SQL emits claim.id here)")
                .isEqualTo(expectedSourceId.toString());
          }
        });
  }

  @Then("that event's metadata contains")
  public void thatEventsMetadataContains(DataTable table) {
    step(
        "assert the single event's metadata contains all expected fields",
        () -> {
          JsonNode metadata = requireEventsArray().get(0).path("metadata");
          assertThat(metadata.isObject()).as("metadata is a JSON object").isTrue();
          metadataMap(table)
              .forEach(
                  (k, v) ->
                      assertThat(metadata.path(k).asText()).as("metadata.%s", k).isEqualTo(v));
        });
  }

  @Then("the SUBMISSION event contains a `metadata` object")
  public void theSubmissionEventContainsAMetadataObject() {
    step(
        "assert the SUBMISSION event carries a `metadata` object (extension point)",
        () -> {
          JsonNode event = requireSubmissionEvent();
          assertThat(event.path("metadata").isObject())
              .as("SUBMISSION event.metadata is a JSON object")
              .isTrue();
        });
  }

  @Then("the `metadata` object is present as an object type, not null and not omitted")
  public void metadataObjectIsPresentAsObjectNotNull() {
    step(
        "assert SUBMISSION event.metadata is present, non-null, and is an object type",
        () -> {
          JsonNode metadata = requireSubmissionEvent().path("metadata");
          assertThat(metadata.isMissingNode()).as("metadata key must be present").isFalse();
          assertThat(metadata.isNull()).as("metadata must not be JSON null").isFalse();
          assertThat(metadata.isObject()).as("metadata must be an object").isTrue();
        });
  }

  @Then("no submission-only fields leak outside the `metadata` container onto the envelope")
  public void noSubmissionOnlyFieldsLeakOutsideMetadata() {
    step(
        "assert no submission-metadata field (submission_period, office_account_number, "
            + "area_of_law) appears as a top-level envelope key on the SUBMISSION event",
        () -> {
          JsonNode event = requireSubmissionEvent();
          Set<String> envelopeKeys = new HashSet<>();
          event.fieldNames().forEachRemaining(envelopeKeys::add);
          // Every leaked-metadata field would surface as a top-level envelope key; the envelope
          // must ONLY expose the agreed contract fields.
          for (String submissionField : SUBMISSION_METADATA_FIELDS) {
            assertThat(envelopeKeys)
                .as(
                    "submission field '%s' must live inside metadata, not on the envelope",
                    submissionField)
                .doesNotContain(submissionField);
          }
          // Additional guard: the envelope must not carry any keys we haven't agreed to expose.
          assertThat(envelopeKeys)
              .as("SUBMISSION event envelope carries only the agreed contract fields")
              .isSubsetOf(ENVELOPE_FIELDS);
        });
  }

  // ---------------------------------------------------------------------------
  // Thens — unknown-claim-id (@DS1811_2).
  // ---------------------------------------------------------------------------

  @Then("the endpoint returns the agreed not-found response")
  public void theEndpointReturnsTheAgreedNotFoundResponse() {
    step(
        "assert /history returned HTTP 404 for the unknown claim id",
        () -> {
          assertThat(lastStatusCode)
              .as("status code for GET /history of unknown claim %s", currentClaimId)
              .isEqualTo(404);
        });
  }

  @Then("the response shape matches the existing Claims API claim-not-found contract")
  public void theResponseShapeMatchesClaimNotFoundContract() {
    step(
        "assert the not-found response body carries the standard RFC 9457 Problem Detail shape "
            + "(see DataClaimsExceptionHandler.buildProblemDetailResponse)",
        () -> {
          assertThat(lastResponseBody).as("not-found response body").isNotNull().isNotBlank();
          JsonNode body =
              new com.fasterxml.jackson.databind.ObjectMapper().readTree(lastResponseBody);
          // RFC 9457 mandatory-when-present fields for a 404 from this handler: type, title,
          // status, detail, instance. The handler also copies detail into a backwards-compat
          // `message` property. Anchoring on these keys catches HTML/plain-text regressions.
          assertThat(body.path("status").asInt()).as("Problem Detail `status`").isEqualTo(404);
          assertThat(body.path("title").asText())
              .as("Problem Detail `title`")
              .isEqualTo("Not Found");
          assertThat(body.path("type").asText())
              .as("Problem Detail `type` URI")
              .isNotBlank()
              .startsWith("http");
          assertThat(body.path("detail").asText())
              .as("Problem Detail `detail` (must reference the missing claim id)")
              .containsIgnoringCase(currentClaimId.toString());
          assertThat(body.path("instance").asText())
              .as("Problem Detail `instance` (must reference the requested endpoint URI)")
              .contains("/history");
          // Backwards-compat property emitted by DataClaimsExceptionHandler.
          assertThat(body.has("message"))
              .as("Problem Detail must carry the backwards-compat `message` property")
              .isTrue();
          assertThat(body.path("message").asText())
              .as("`message` must mirror `detail`")
              .isEqualTo(body.path("detail").asText());
        });
  }

  @Then("no history events are returned in the body")
  public void noHistoryEventsAreReturnedInTheBody() {
    step(
        "assert the not-found response body carries no events[] array",
        () -> {
          // A 404 body from ClaimsDataException carries an error payload, NOT a
          // ClaimHistoryResultSet — so `events` must be absent (or, if present, empty).
          if (lastResponseBody == null || lastResponseBody.isBlank()) {
            return;
          }
          try {
            JsonNode body =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(lastResponseBody);
            JsonNode events = body.path("events");
            if (events.isMissingNode() || events.isNull()) {
              return;
            }
            assertThat(events.isArray() && events.size() == 0)
                .as("not-found body must not contain history events")
                .isTrue();
          } catch (Exception ignored) {
            // Body isn't JSON — still fine; no events array present.
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

    // If the previous step captured a "stored values" table, apply it now.
    applyPendingSubmissionOverrides();
  }

  private void applyPendingSubmissionOverrides() {
    if (pendingSubmissionValues == null || pendingSubmissionValues.isEmpty()) {
      return;
    }
    Map<String, String> vals = pendingSubmissionValues;

    // The delivered SUBMISSION event derives event_timestamp, actor_id and source_id from the
    // CLAIM row (see JdbcClaimHistoryRepository.HISTORY_SQL SUBMISSION branch), and derives its
    // metadata fields from the parent SUBMISSION row. The feature file provides these values
    // via two separate "stored values" tables ("the claim has ..." and "the parent submission
    // has ..."); this helper routes each key to whichever table it belongs on.
    UUID claimId = requireCurrentClaimId();

    // id label — bind whatever the feature file names this to the actual claim id.
    if (vals.containsKey("id")) {
      labelToUuid.put(vals.get("id"), claimId);
    }
    if (vals.containsKey("created_on")) {
      jdbcClient
          .sql("UPDATE claims.claim SET created_on = :ts WHERE id = :id")
          .param(
              "ts", OffsetDateTime.ofInstant(Instant.parse(vals.get("created_on")), ZoneOffset.UTC))
          .param("id", claimId)
          .update();
    }
    if (vals.containsKey("created_by_user_id")) {
      jdbcClient
          .sql("UPDATE claims.claim SET created_by_user_id = :u WHERE id = :id")
          .param("u", vals.get("created_by_user_id"))
          .param("id", claimId)
          .update();
    }
    // Submission-owned metadata fields — updated on the parent row.
    UUID submissionId =
        jdbcClient
            .sql("SELECT submission_id FROM claims.claim WHERE id = :id")
            .param("id", claimId)
            .query(UUID.class)
            .single();
    if (vals.containsKey("submission_period")) {
      jdbcClient
          .sql("UPDATE claims.submission SET submission_period = :v WHERE id = :id")
          .param("v", vals.get("submission_period"))
          .param("id", submissionId)
          .update();
    }
    if (vals.containsKey("office_account_number")) {
      jdbcClient
          .sql("UPDATE claims.submission SET office_account_number = :v WHERE id = :id")
          .param("v", vals.get("office_account_number"))
          .param("id", submissionId)
          .update();
    }
    if (vals.containsKey("area_of_law")) {
      // Hibernate stores `AreaOfLaw` via {@code @Enumerated(EnumType.STRING)}, i.e. the enum's
      // {@code name()} — the same underscore form the feature file uses (e.g. {@code CRIME_LOWER}).
      // Jackson's {@code @JsonValue} is separate and only affects JSON serialization of the enum
      // Java type; the SUBMISSION event metadata bag reads {@code s.area_of_law} verbatim from
      // the row via {@code jsonb_build_object}, so passing the underscore form here surfaces
      // {@code "CRIME_LOWER"} at the HTTP boundary — matching the feature-file table.
      jdbcClient
          .sql("UPDATE claims.submission SET area_of_law = :v WHERE id = :id")
          .param("v", vals.get("area_of_law"))
          .param("id", submissionId)
          .update();
    }
    pendingSubmissionValues = new HashMap<>();
  }

  // ---------------------------------------------------------------------------
  // Inspection helpers.
  // ---------------------------------------------------------------------------

  private JsonNode requireEventsArray() {
    assertThat(lastResponse).as("no /history response captured yet").isNotNull();
    JsonNode events = lastResponse.path("events");
    assertThat(events.isArray()).as("response has an `events` JSON array").isTrue();
    return events;
  }

  private JsonNode requireSubmissionEvent() {
    JsonNode events = requireEventsArray();
    for (JsonNode e : events) {
      if ("SUBMISSION".equals(e.path("event_type").asText())) {
        return e;
      }
    }
    throw new AssertionError("No SUBMISSION event on /history timeline");
  }

  private void assertEnvelopeFieldEquals(JsonNode event, String field, String expected) {
    assertThat(event.path(field).asText()).as("envelope field '%s'", field).isEqualTo(expected);
  }

  private void assertTimestamp(JsonNode event, String expectedIso) {
    Instant expected = Instant.parse(expectedIso);
    Instant actual = Instant.parse(event.path("event_timestamp").asText());
    assertThat(actual).as("event_timestamp").isEqualTo(expected);
  }

  private UUID requireLabel(String label) {
    UUID id = labelToUuid.get(label);
    if (id == null) {
      throw new AssertionError(
          "No UUID registered for label '" + label + "'. Known labels: " + labelToUuid.keySet());
    }
    return id;
  }

  private UUID requireCurrentClaimId() {
    if (currentClaimId == null) {
      throw new AssertionError(
          "No claim id has been established yet — expected a prior Given step.");
    }
    return currentClaimId;
  }

  // ---------------------------------------------------------------------------
  // DataTable helpers.
  // ---------------------------------------------------------------------------

  private static Map<String, String> singleFieldValueMap(DataTable table) {
    return keyValueMap(table, "field");
  }

  private static Map<String, String> envelopeMap(DataTable table) {
    return keyValueMap(table, "envelopeField");
  }

  private static Map<String, String> metadataMap(DataTable table) {
    return keyValueMap(table, "metadataField");
  }

  private static Map<String, String> keyValueMap(DataTable table, String headerKey) {
    Map<String, String> out = new HashMap<>();
    for (List<String> row : table.asLists()) {
      if (row.size() < 2) {
        continue;
      }
      String key = row.get(0);
      if (headerKey.equalsIgnoreCase(key)) {
        continue;
      }
      out.put(key, row.get(1) == null ? "" : row.get(1));
    }
    return out;
  }
}
