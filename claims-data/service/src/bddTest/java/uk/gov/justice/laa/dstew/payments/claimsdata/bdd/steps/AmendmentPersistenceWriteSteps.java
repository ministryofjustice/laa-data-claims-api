package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.AmendableClaimFixture;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.BddMockServerSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;

/**
 * Step glue for {@code amendmentsPersistenceWrite.feature} (DSTEW-1907 — assemble &amp; write the
 * durable amendment business record).
 *
 * <p>Unlike the read-model claim-history steps, which hand-seed {@code claim_amendment} rows, these
 * steps drive <b>real</b> amendments over HTTP (PATCH the live endpoint via the DSTEW-2317
 * real-validation harness) and then assert the actually persisted rows plus the real claim-history
 * timeline. The write path is therefore exercised end to end.
 *
 * <p>Key persistence facts this glue relies on:
 *
 * <ul>
 *   <li>A successful amendment inserts exactly one {@code claim_amendment} row with a UUIDv7 id.
 *   <li>A pricing-impacting amendment (FSP runs) inserts one {@code calculated_fee_detail} row
 *       linked via {@code claim_amendment_id}; a non-pricing amendment inserts none.
 *   <li>{@code CalculatedFeeDetail.claimAmendment} is a LAZY association, so the {@code
 *       claim_amendment_id} FK is read as a raw scalar via {@link JdbcTemplate} rather than through
 *       the entity graph (which would trip lazy-init outside a session).
 *   <li>An AMENDMENT timeline event's {@code source_id} / {@code event_timestamp} are the persisted
 *       {@code claim_amendment.id} / {@code created_on} (see JdbcClaimHistoryRepository).
 * </ul>
 */
@Slf4j
public class AmendmentPersistenceWriteSteps {

  private static final String AMENDMENT_USER_ID = "0190b6a0-9b7e-7c8a-9e2d-230100000001";
  private static final String AMENDMENT_EVENT_TYPE = "AMENDMENT";

  @Autowired private AmendableClaimFixture fixture;
  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext scenarioContext;
  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private ClaimAmendmentRepository claimAmendmentRepository;
  @Autowired private BddMockServerSupport mock;
  @Autowired private JdbcTemplate jdbc;

  // Scenario-scoped bookkeeping. cucumber-spring gives a fresh step-class instance per scenario
  // (the class is not @ScenarioScope), so these reset between scenarios.
  private UUID claimId;
  private UUID submissionId;
  private final List<AppliedAmendment> applied = new ArrayList<>();
  private UUID originalCfdId;
  private Map<String, Object> originalCfdBaseline;
  private JsonNode history;

  /** One amendment the scenario drove, with the id it persisted and whether it was pricing. */
  private record AppliedAmendment(UUID id, boolean pricing) {}

  // ---------------------------------------------------------------------------
  // Given — seed a fresh amendable claim (the fixture seeds an original-submission
  // calculated_fee_detail row with claim_amendment_id null as its baseline).
  // ---------------------------------------------------------------------------

  @Given("a claim exists with an original-submission calculated_fee_detail row")
  public void aClaimExistsWithAnOriginalSubmissionCalculatedFeeDetailRow() {
    step(
        "seed a fresh amendable Legal Help claim with a baseline (claim_amendment_id null)"
            + " calculated_fee_detail row",
        () -> {
          AmendableClaimFixture.Seeded seeded = fixture.legalHelpValid().withVersion(0).seed();
          this.submissionId = seeded.submissionId();
          this.claimId = seeded.claimId();
          sharedPatchContext.setSubmissionId(submissionId);
          sharedPatchContext.setClaimId(claimId);

          List<Map<String, Object>> baseRows = cfdRows(claimId);
          assertThat(baseRows)
              .as(
                  "exactly one original-submission calculated_fee_detail row seeded for claim %s",
                  claimId)
              .hasSize(1);
          assertThat(baseRows.get(0).get("claim_amendment_id"))
              .as("the seeded original-submission row has claim_amendment_id = null")
              .isNull();
          this.originalCfdId = (UUID) baseRows.get(0).get("id");
          log.info("[DSTEW-1907] seeded claim {} with baseline CFD {}", claimId, originalCfdId);
        });
  }

  @Given(
      "I capture the original-submission calculated_fee_detail row as the pre-amendment baseline")
  public void iCaptureTheOriginalSubmissionRowAsBaseline() {
    step(
        "snapshot every stored column of original-submission calculated_fee_detail row "
            + originalCfdId,
        () -> {
          this.originalCfdBaseline =
              jdbc.queryForMap(
                  "SELECT * FROM claims.calculated_fee_detail WHERE id = ?", originalCfdId);
          assertThat(originalCfdBaseline).as("captured baseline row").isNotEmpty();
        });
  }

  // ---------------------------------------------------------------------------
  // When — drive the real amendments in order
  // ---------------------------------------------------------------------------

  @When("the following successful amendments are applied in order")
  public void theFollowingSuccessfulAmendmentsAreAppliedInOrder(DataTable table) {
    List<Map<String, String>> rows = table.asMaps();
    step(
        "drive " + rows.size() + " real amendments in order against claim " + claimId,
        () -> {
          int index = 0;
          for (Map<String, String> row : rows) {
            boolean pricing = Boolean.parseBoolean(row.get("impacts_pricing"));
            applyOneAmendment(index, pricing);
            index++;
          }
        });
  }

  private void applyOneAmendment(int index, boolean pricing) throws Exception {
    // Re-read the current version straight from the DB (bypassing the JPA first-level cache) so
    // each amendment submits the version the previous one left behind.
    long version =
        jdbc.queryForObject("SELECT version FROM claims.claim WHERE id = ?", Long.class, claimId);

    // Arm the happy external-service stubs for every amendment. A pricing amendment drives a real
    // FSP calculateFee; validateClaim runs over real HTTP either way, so arming both PDA + FSP is
    // the safe, idempotent baseline (unused expectations are harmless).
    mock.stubProviderSchedulesOk();
    mock.stubAmendmentFspOk();

    String patch = pricing ? pricingPatch(version, index) : nonPricingPatch(version, index);
    api.patchClaimAmendment(submissionId, claimId, patch);

    Integer status = scenarioContext.getLastStatusCode();
    assertThat(status)
        .as(
            "amendment #%d (pricing=%s) should be accepted (2xx); body=%s",
            index + 1, pricing, scenarioContext.getLastResponseBody())
        .isNotNull()
        .satisfies(s -> assertThat(s / 100).isEqualTo(2));

    // The newest claim_amendment row (UUIDv7-id desc) is the one this PATCH just committed.
    List<ClaimAmendment> amendments = claimAmendmentRepository.findByClaimIdOrderByIdDesc(claimId);
    assertThat(amendments)
        .as("at least %d claim_amendment rows after amendment #%d", index + 1, index + 1)
        .hasSizeGreaterThanOrEqualTo(index + 1);
    UUID newId = amendments.get(0).getId();
    applied.add(new AppliedAmendment(newId, pricing));
    log.info(
        "[DSTEW-1907] amendment #{} (pricing={}) committed → claim_amendment {}",
        index + 1,
        pricing,
        newId);
  }

  // ---------------------------------------------------------------------------
  // When — request the real claim-history timeline
  // ---------------------------------------------------------------------------

  @When("I request the claim history timeline for the amended claim")
  public void iRequestTheClaimHistoryTimelineForTheAmendedClaim() {
    step(
        "GET /claims/" + claimId + "/history after driving the amendments",
        () -> this.history = api.getClaimHistory(claimId));
  }

  // ---------------------------------------------------------------------------
  // Then — timeline assertions
  // ---------------------------------------------------------------------------

  @Then("the timeline contains {int} AMENDMENT events")
  public void theTimelineContainsNAmendmentEvents(int expected) {
    step(
        "assert the history timeline contains " + expected + " AMENDMENT events",
        () ->
            assertThat(amendmentEvents())
                .as("AMENDMENT events in the timeline for claim %s", claimId)
                .hasSize(expected));
  }

  @Then(
      "the earlier AMENDMENT event source_id sorts before the later one when compared as a UUIDv7")
  public void earlierAmendmentSourceIdSortsBefore() {
    step(
        "assert the earlier AMENDMENT event's source_id precedes the later one as a UUIDv7",
        () -> {
          List<JsonNode> events = amendmentEvents();
          assertThat(events).as("need two AMENDMENT events to compare ordering").hasSize(2);

          JsonNode a = events.get(0);
          JsonNode b = events.get(1);
          JsonNode earlier =
              Instant.parse(a.path("event_timestamp").asText())
                      .isBefore(Instant.parse(b.path("event_timestamp").asText()))
                  ? a
                  : b;
          JsonNode later = (earlier == a) ? b : a;

          UUID earlierId = UUID.fromString(earlier.path("source_id").asText());
          UUID laterId = UUID.fromString(later.path("source_id").asText());

          assertThat(earlierId.version()).as("earlier source_id is a UUIDv7").isEqualTo(7);
          assertThat(laterId.version()).as("later source_id is a UUIDv7").isEqualTo(7);
          assertThat(uuidV7Timestamp(earlierId))
              .as("earlier source_id's UUIDv7 timestamp precedes the later one's")
              .isLessThan(uuidV7Timestamp(laterId));
          assertThat(earlierId.toString().compareTo(laterId.toString()))
              .as("UUIDv7 ids are lexicographically time-ordered, so the earlier id sorts first")
              .isLessThan(0);
        });
  }

  @Then(
      "ordering the AMENDMENT events by source_id yields the same order as ordering by event_timestamp")
  public void orderingBySourceIdMatchesOrderingByTimestamp() {
    step(
        "assert sort-by-source_id and sort-by-event_timestamp produce the same AMENDMENT sequence",
        () -> {
          List<JsonNode> events = amendmentEvents();

          List<String> bySourceId =
              events.stream()
                  .sorted(
                      Comparator.comparingLong(
                          e -> uuidV7Timestamp(UUID.fromString(e.path("source_id").asText()))))
                  .map(e -> e.path("source_id").asText())
                  .toList();

          List<String> byTimestamp =
              events.stream()
                  .sorted(
                      Comparator.comparing(e -> Instant.parse(e.path("event_timestamp").asText())))
                  .map(e -> e.path("source_id").asText())
                  .toList();

          assertThat(bySourceId)
              .as("ordering AMENDMENT events by source_id matches ordering by event_timestamp")
              .isEqualTo(byTimestamp);
        });
  }

  // ---------------------------------------------------------------------------
  // Then — persisted claim_amendment assertions
  // ---------------------------------------------------------------------------

  @Then("exactly {int} claim_amendment rows exist for this claim")
  public void exactlyNClaimAmendmentRowsExist(int expected) {
    step(
        "assert exactly " + expected + " claim_amendment rows exist for claim " + claimId,
        () ->
            assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(claimId))
                .as("claim_amendment rows for claim %s", claimId)
                .hasSize(expected));
  }

  @Then("each claim_amendment row has a UUIDv7 id")
  public void eachClaimAmendmentRowHasAUuidV7Id() {
    step(
        "assert every claim_amendment id is a version-7 UUID",
        () ->
            claimAmendmentRepository
                .findByClaimIdOrderByIdDesc(claimId)
                .forEach(
                    row ->
                        assertThat(row.getId().version())
                            .as("claim_amendment %s is a UUIDv7", row.getId())
                            .isEqualTo(7)));
  }

  // ---------------------------------------------------------------------------
  // Then — amendment-linked calculated_fee_detail assertions
  // ---------------------------------------------------------------------------

  @Then("exactly {int} amendment-linked calculated_fee_detail rows exist for this claim")
  public void exactlyNAmendmentLinkedCfdRowsExist(int expected) {
    step(
        "assert exactly " + expected + " calculated_fee_detail rows carry a claim_amendment_id",
        () ->
            assertThat(linkedCfdRows())
                .as("amendment-linked calculated_fee_detail rows for claim %s", claimId)
                .hasSize(expected));
  }

  @Then(
      "each amendment-linked calculated_fee_detail row links to a successful pricing amendment id")
  public void eachLinkedCfdLinksToAPricingAmendmentId() {
    step(
        "assert every linked calculated_fee_detail.claim_amendment_id is a pricing amendment id",
        () -> {
          List<UUID> pricingIds =
              applied.stream().filter(AppliedAmendment::pricing).map(AppliedAmendment::id).toList();
          List<UUID> linkedIds =
              linkedCfdRows().stream().map(r -> (UUID) r.get("claim_amendment_id")).toList();

          assertThat(linkedIds)
              .as("each amendment-linked CFD row points at a pricing amendment id %s", pricingIds)
              .allSatisfy(id -> assertThat(pricingIds).contains(id));
          assertThat(linkedIds)
              .as("every pricing amendment produced exactly one linked CFD row")
              .containsExactlyInAnyOrderElementsOf(pricingIds);
        });
  }

  @Then("the non-pricing amendment has no amendment-linked calculated_fee_detail row")
  public void theNonPricingAmendmentHasNoLinkedCfdRow() {
    step(
        "assert the non-pricing amendment id appears on no calculated_fee_detail row",
        () -> {
          List<UUID> nonPricingIds =
              applied.stream().filter(a -> !a.pricing()).map(AppliedAmendment::id).toList();
          List<UUID> linkedIds =
              linkedCfdRows().stream().map(r -> (UUID) r.get("claim_amendment_id")).toList();
          assertThat(linkedIds)
              .as("no calculated_fee_detail row links to a non-pricing amendment %s", nonPricingIds)
              .doesNotContainAnyElementsOf(nonPricingIds);
        });
  }

  @Then("at most one calculated_fee_detail row exists per claim_amendment_id")
  public void atMostOneCfdPerClaimAmendmentId() {
    step(
        "assert the claim_amendment_id FK is at-most-1:1 across calculated_fee_detail rows",
        () -> {
          List<UUID> linkedIds =
              linkedCfdRows().stream().map(r -> (UUID) r.get("claim_amendment_id")).toList();
          assertThat(linkedIds)
              .as("calculated_fee_detail.claim_amendment_id values are unique (UNIQUE constraint)")
              .doesNotHaveDuplicates();
        });
  }

  // ---------------------------------------------------------------------------
  // Then — original-submission row preservation (DS1907_2)
  // ---------------------------------------------------------------------------

  @Then(
      "the original-submission calculated_fee_detail row is still present with claim_amendment_id"
          + " still null")
  public void originalRowStillPresentWithNullLink() {
    step(
        "assert the original-submission calculated_fee_detail row survives with a null link",
        () -> {
          Map<String, Object> current =
              jdbc.queryForMap(
                  "SELECT * FROM claims.calculated_fee_detail WHERE id = ?", originalCfdId);
          assertThat(current.get("claim_amendment_id"))
              .as("original-submission row %s keeps claim_amendment_id = null", originalCfdId)
              .isNull();
        });
  }

  @Then(
      "every stored value on the original-submission row matches the pre-amendment baseline exactly")
  public void originalRowMatchesBaseline() {
    step(
        "assert the original-submission row is byte-for-byte unchanged versus the baseline",
        () -> {
          assertThat(originalCfdBaseline).as("pre-amendment baseline was captured").isNotEmpty();
          Map<String, Object> current =
              jdbc.queryForMap(
                  "SELECT * FROM claims.calculated_fee_detail WHERE id = ?", originalCfdId);
          assertThat(current)
              .as("original-submission row is unchanged by the amendment")
              .isEqualTo(originalCfdBaseline);
        });
  }

  @Then(
      "a new amendment-linked calculated_fee_detail row exists with claim_amendment_id set to the"
          + " successful amendment id")
  public void newLinkedRowExistsForTheAmendment() {
    step(
        "assert a new calculated_fee_detail row is linked to the successful amendment",
        () -> {
          assertThat(applied).as("one amendment was applied").hasSize(1);
          UUID amendmentId = applied.get(0).id();
          List<Map<String, Object>> linked = linkedCfdRows();
          assertThat(linked)
              .as("exactly one amendment-linked calculated_fee_detail row exists")
              .hasSize(1);
          assertThat((UUID) linked.get(0).get("claim_amendment_id"))
              .as("the new row links to the successful amendment id")
              .isEqualTo(amendmentId);
          assertThat((UUID) linked.get(0).get("id"))
              .as("the new row is a distinct row from the original-submission row")
              .isNotEqualTo(originalCfdId);
        });
  }

  @Then("the original and amendment-linked rows coexist as two independent rows")
  public void originalAndLinkedRowsCoexist() {
    step(
        "assert the original (null link) and amendment-linked rows coexist independently",
        () -> {
          List<Map<String, Object>> all = cfdRows(claimId);
          assertThat(all).as("the claim now has two calculated_fee_detail rows").hasSize(2);
          long nullLinks = all.stream().filter(r -> r.get("claim_amendment_id") == null).count();
          long amendmentLinks =
              all.stream().filter(r -> r.get("claim_amendment_id") != null).count();
          assertThat(nullLinks).as("one original-submission row (null link)").isEqualTo(1);
          assertThat(amendmentLinks).as("one amendment-linked row").isEqualTo(1);
        });
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private List<JsonNode> amendmentEvents() {
    List<JsonNode> out = new ArrayList<>();
    if (history != null) {
      for (JsonNode event : history.path("events")) {
        if (AMENDMENT_EVENT_TYPE.equals(event.path("event_type").asText())) {
          out.add(event);
        }
      }
    }
    return out;
  }

  private List<Map<String, Object>> cfdRows(UUID claim) {
    return jdbc.queryForList(
        "SELECT id, claim_amendment_id FROM claims.calculated_fee_detail WHERE claim_id = ?",
        claim);
  }

  private List<Map<String, Object>> linkedCfdRows() {
    return cfdRows(claimId).stream().filter(r -> r.get("claim_amendment_id") != null).toList();
  }

  /** Extracts the 48-bit unix_ts_ms embedded in the most-significant bits of a UUIDv7. */
  private static long uuidV7Timestamp(UUID uuid) {
    return uuid.getMostSignificantBits() >>> 16;
  }

  private static String nonPricingPatch(long version, int index) {
    // Changing client_forename is non-pricing: FSP is not invoked, so no linked CFD row is added.
    return "{\"version\":"
        + version
        + ",\"amendment_requested_by\":\"PROVIDER\""
        + ",\"amendment_reason_code\":\"PROVIDER_ERROR\""
        + ",\"amendment_user_id\":\""
        + AMENDMENT_USER_ID
        + "\""
        + ",\"client_forename\":\"Canary-"
        + index
        + "\"}";
  }

  private static String pricingPatch(long version, int index) {
    // case_start_date is a pricing-impacting FSP request-body field. A distinct date per amendment
    // guarantees a real change versus the current value (so the amendment is not a no-op) and
    // drives one FeeSchemePlatformRestClient.calculateFee → one amendment-linked CFD row.
    String caseStartDate = String.format("%02d/08/2025", 4 + index);
    return "{\"version\":"
        + version
        + ",\"amendment_requested_by\":\"PROVIDER\""
        + ",\"amendment_reason_code\":\"PROVIDER_ERROR\""
        + ",\"amendment_user_id\":\""
        + AMENDMENT_USER_ID
        + "\""
        + ",\"case_start_date\":\""
        + caseStartDate
        + "\"}";
  }
}
