package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step definitions for {@code claimHistoryAmendmentChangesDetail.feature} (DSTEW-1814).
 *
 * <p>Verifies the {@code AMENDMENT} event's {@code metadata.changes[]} array is rendered verbatim
 * from the persisted {@code claim_amendment.diff} JSONB, honouring the OpenAPI {@code
 * claim_history_change_entry} contract: {@code field_identifier}, {@code change_source}
 * (upper-case), {@code before}, {@code after} — with JSON {@code null} preserved as a
 * present-and-null value (cleared field) rather than an omitted key.
 *
 * <p>Data is seeded directly through JPA repos (Submission → Claim → ClaimAmendment with a raw
 * JSONB diff string) so scenarios exercise the read-side {@code GET
 * /api/v1/claims/{claimId}/history} SQL and the controller mapping in isolation from the write
 * pipeline (amendment PoC, PDA / FSP, OCC guard). The diff shape mirrors the fixtures used by
 * {@code JdbcClaimHistoryRepositoryIntegrationTest}.
 */
@Slf4j
public class ClaimHistoryAmendmentChangesDetailSteps extends ClaimHistoryTimelineSharedSteps {

  private static final String BDD_USER_ID = "bdd-user-1814";
  private static final String AMENDMENT_EVENT_TYPE = "AMENDMENT";

  @Autowired private BddApiStepSupport api;
  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimAmendmentRepository claimAmendmentRepository;

  // Scenario-scoped state is stored in ClaimHistoryTimelineSharedSteps / ClaimHistoryContext.

  // ---------------------------------------------------------------------------
  // Given — seed a claim + a successful claim_amendment carrying the diff under test
  // ---------------------------------------------------------------------------

  // Cucumber Expressions treat unescaped `(...)` as an optional-text group, so parentheses that
  // are literal in the feature file must be escaped as `\(` / `\)` in the annotation string.

  @Given(
      "a claim exists with a successful `claim_amendment` row whose stored diff"
          + " \\(schema_version=1) contains")
  public void aClaimWithAmendmentDiffContainingSchemaV1(DataTable table) {
    seedClaim();
    persistAmendment(buildDiffJson(table));
  }

  @Given("a claim exists with a successful `claim_amendment` row whose stored diff contains")
  public void aClaimWithAmendmentDiffContaining(DataTable table) {
    seedClaim();
    persistAmendment(buildDiffJson(table));
  }

  @Given(
      "a claim exists with a successful `claim_amendment` row whose stored diff contains ONLY the"
          + " following change entries")
  public void aClaimWithDiffContainingOnly(DataTable table) {
    seedClaim();
    persistAmendment(buildDiffJson(table));
  }

  @Given(
      "the amendment payload also echoed the following fields unchanged \\(no change recorded in"
          + " the stored diff)")
  public void amendmentPayloadEchoedUnchangedFields(DataTable table) {
    // Documentary Given — the diff already omits these fields (per DSTEW-1659 design: only
    // changed fields appear in `changes[]`). No DB mutation needed; the omission is asserted by
    // the "does NOT contain" steps below. Recording the intent here keeps the scenario readable.
    List<String> unchangedFields =
        table.asMaps(String.class, String.class).stream()
            .map(row -> row.get("field_identifier"))
            .toList();
    log.debug(
        "Scenario documents payload-echoed unchanged fields (must remain absent from"
            + " changes[]): {}",
        unchangedFields);
  }

  // ---------------------------------------------------------------------------
  // When
  // ---------------------------------------------------------------------------

  // ---------------------------------------------------------------------------
  // Then — array size / entry presence
  // ---------------------------------------------------------------------------

  @Then("the AMENDMENT event metadata `changes` array contains exactly one entry")
  public void amendmentChangesArrayHasExactlyOneEntry() {
    assertChangesArraySize(1);
  }

  @Then("the AMENDMENT event metadata `changes` array contains exactly two entries")
  public void amendmentChangesArrayHasExactlyTwoEntries() {
    assertChangesArraySize(2);
  }

  @Then("the AMENDMENT event metadata `changes` array contains an entry for {string}")
  public void amendmentChangesArrayContainsEntryFor(String fieldIdentifier) {
    assertThat(findEntryByField(fieldIdentifier))
        .as("changes[] entry for field '%s'", fieldIdentifier)
        .isNotNull();
  }

  @Then("the `changes` array does NOT contain an entry for {string}")
  public void changesArrayDoesNotContainEntryFor(String fieldIdentifier) {
    assertThat(findEntryByField(fieldIdentifier))
        .as("changes[] must NOT contain entry for '%s'", fieldIdentifier)
        .isNull();
  }

  // ---------------------------------------------------------------------------
  // Then — entry values
  // ---------------------------------------------------------------------------

  @Then("that entry matches")
  public void thatEntryMatches(DataTable table) {
    // Single-row expectation table — assert against the sole entry.
    JsonNode changes = requireChangesArray();
    assertThat(changes).hasSize(1);
    assertEntryMatchesExpectation(changes.get(0), table.asMaps(String.class, String.class).get(0));
  }

  @Then("the `changes` array contains an entry with the following values")
  public void changesArrayContainsEntryWithValues(DataTable table) {
    Map<String, String> expected = table.asMaps(String.class, String.class).get(0);
    String field = expected.get("field_identifier");
    JsonNode entry = findEntryByField(field);
    assertThat(entry).as("no changes[] entry for field '%s'", field).isNotNull();
    assertEntryMatchesExpectation(entry, expected);
  }

  // ---------------------------------------------------------------------------
  // Then — explicit-null semantics (DS1814_3)
  // ---------------------------------------------------------------------------

  @Then("that entry's `after` field is present in the JSON response")
  public void thatEntryAfterFieldIsPresent() {
    JsonNode entry = requireSingleEntry();
    assertThat(entry.has("after"))
        .as("`after` key must be present on the changes[] entry (explicit null vs missing)")
        .isTrue();
  }

  @Then("that entry's `after` value is explicit JSON null")
  public void thatEntryAfterValueIsExplicitJsonNull() {
    JsonNode entry = requireSingleEntry();
    JsonNode after = entry.get("after");
    assertThat(after).as("`after` node").isNotNull();
    assertThat(after.isNull())
        .as(
            "`after` must be JSON null, actual node type: %s / value: %s",
            after.getNodeType(), after)
        .isTrue();
  }

  @Then("that entry's `after` field is NOT omitted from the JSON response")
  public void thatEntryAfterFieldIsNotOmitted() {
    // Belt-and-braces guard against Jackson NON_NULL creeping in on the primary ObjectMapper —
    // if the key ever disappears, the explicit-null vs cleared semantic collapses.
    assertThat(requireSingleEntry().has("after"))
        .as("`after` key must never be omitted — cleared fields are represented as JSON null")
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Helpers — data seeding
  // ---------------------------------------------------------------------------

  private void seedClaim() {
    Submission submission =
        submissionRepository.saveAndFlush(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .officeAccountNumber("1814-office")
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

    setCurrentClaimId(claim.getId());
  }

  private void persistAmendment(String diffJson) {
    Claim claimRef = claimRepository.getReferenceById(currentClaimId);
    claimAmendmentRepository.saveAndFlush(
        ClaimAmendment.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claimRef)
            .requestedByCode("PROVIDER")
            .amendmentReasonCode("PROVIDER_ERROR")
            .beforeState("{}")
            .requestPayload("{}")
            .diff(diffJson)
            .createdByUserId(BDD_USER_ID)
            .createdOn(Instant.now())
            .build());
  }

  /**
   * Builds a {@code {"schema_version":1,"changes":[...]}} JSONB string from a scenario data table.
   *
   * <p>Every emitted {@code changes[]} entry ALWAYS carries all four keys ({@code
   * field_identifier}, {@code change_source}, {@code before}, {@code after}) — this matches the
   * delivered wire contract, where {@code DiffEntry.before} / {@code DiffEntry.after} are
   * non-optional and a cleared field is represented by an explicit JSON {@code null}.
   *
   * <p>Accepted column shapes for the {@code before} / {@code after} sides (see {@link
   * #resolveSide}):
   *
   * <ul>
   *   <li>{@code before_value} / {@code after_value} — bare literal (default form).
   *   <li>{@code before} / {@code after} — bare literal (alternate spelling used by some
   *       scenarios).
   *   <li>{@code before_present} / {@code after_present} paired with {@code before_value} / {@code
   *       after_value} — legacy tri-state form retained ONLY for de-scoped {@code @DS1814_4}. On
   *       the shipped contract {@code present=false} collapses to JSON {@code null}, identical to
   *       an explicit cleared value.
   * </ul>
   *
   * <p>If the column is omitted entirely the emitted key is JSON {@code null} (i.e. treated the
   * same as an explicit cleared value — the contract has no "omit key" case).
   *
   * <p>{@code change_source} is REQUIRED on every row; a missing / blank value fails fast at seed
   * time (see {@link #normaliseChangeSource}).
   */
  private String buildDiffJson(DataTable table) {
    List<Map<String, String>> rows = table.asMaps(String.class, String.class);
    StringBuilder changes = new StringBuilder();
    for (int i = 0; i < rows.size(); i++) {
      Map<String, String> row = rows.get(i);
      if (i > 0) {
        changes.append(',');
      }
      changes.append('{');
      changes.append("\"field_identifier\":").append(jsonString(row.get("field_identifier")));
      changes
          .append(",\"change_source\":\"")
          .append(normaliseChangeSource(row.get("change_source")))
          .append('\"');
      changes.append(",\"before\":").append(resolveSide(row, "before"));
      changes.append(",\"after\":").append(resolveSide(row, "after"));
      changes.append('}');
    }
    return "{\"schema_version\":1,\"changes\":[" + changes + "]}";
  }

  private String resolveSide(Map<String, String> row, String side) {
    String presentCol = side + "_present";
    String valueCol = side + "_value";
    String plainCol = side; // some tables use a bare "before" / "after" column

    // Tri-state form kept for the de-scoped @DS1814_4 scenario (see feature-file banner).
    // The delivered API can't distinguish present=false from JSON null on the wire, so
    // both settings collapse to JSON null here. Kept only so the scenario can be restored
    // verbatim if the underlying `DiffEntry` contract is later extended with a `present` flag.
    if (row.containsKey(presentCol)) {
      String present = row.get(presentCol);
      if ("false".equalsIgnoreCase(present)) {
        return "null";
      }
      return renderJsonLiteral(row.get(valueCol));
    }
    if (row.containsKey(valueCol)) {
      return renderJsonLiteral(row.get(valueCol));
    }
    if (row.containsKey(plainCol)) {
      return renderJsonLiteral(row.get(plainCol));
    }
    // Column omitted entirely — surface a JSON null. The delivered API always emits both
    // `before` and `after` keys on every changes[] entry (see buildDiffJson Javadoc), so
    // "column omitted" means "explicit cleared value" here.
    return "null";
  }

  /**
   * Renders a scenario cell as a JSON literal. Bare digits / decimals go through as JSON numbers so
   * a scenario writer can express {@code 100.00} without quoting; everything else is a JSON string.
   * The literal token {@code null} (case-insensitive) becomes an explicit JSON {@code null}.
   */
  private String renderJsonLiteral(String raw) {
    if (raw == null || "null".equalsIgnoreCase(raw.trim())) {
      return "null";
    }
    String trimmed = raw.trim();
    if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
      return trimmed;
    }
    return jsonString(raw);
  }

  private String jsonString(String value) {
    if (value == null) {
      return "null";
    }
    // Minimal escaping — scenario inputs are ASCII identifiers / short business values.
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private String normaliseChangeSource(String raw) {
    // Feature file uses "Requested" / "FSP" for readability; persisted enum requires upper-case.
    // change_source is required — every changes[] entry MUST carry one on the delivered contract.
    // Fail fast if the scenario table omits it, so a malformed fixture surfaces at seed time
    // rather than as a mysterious "change_source":"NULL" string downstream.
    if (raw == null || raw.trim().isEmpty()) {
      throw new IllegalArgumentException(
          "buildDiffJson: `change_source` is required on every changes[] row but was missing / "
              + "blank. Add a `change_source` column (values: Requested | FSP) to the scenario "
              + "data table.");
    }
    return raw.trim().toUpperCase(Locale.ROOT);
  }

  // ---------------------------------------------------------------------------
  // Helpers — response inspection
  // ---------------------------------------------------------------------------

  private JsonNode findAmendmentEvent(JsonNode history) {
    JsonNode events = history.path("events");
    assertThat(events.isArray()).as("history response `events` must be an array").isTrue();
    for (JsonNode event : events) {
      if (AMENDMENT_EVENT_TYPE.equals(event.path("event_type").asText())) {
        return event;
      }
    }
    throw new AssertionError(
        "No AMENDMENT event found on the history response for claim " + currentClaimId);
  }

  private JsonNode requireChangesArray() {
    JsonNode amendmentEvent = findAmendmentEvent(getLastResponse());
    JsonNode changes = amendmentEvent.path("metadata").path("changes");
    assertThat(changes.isArray()).as("AMENDMENT event metadata.changes must be an array").isTrue();
    return changes;
  }

  private void assertChangesArraySize(int expected) {
    assertThat(requireChangesArray().size())
        .as("AMENDMENT event metadata.changes[] entry count")
        .isEqualTo(expected);
  }

  private JsonNode findEntryByField(String fieldIdentifier) {
    for (JsonNode entry : requireChangesArray()) {
      if (fieldIdentifier.equals(entry.path("field_identifier").asText())) {
        return entry;
      }
    }
    return null;
  }

  private JsonNode requireSingleEntry() {
    JsonNode changes = requireChangesArray();
    assertThat(changes)
        .as("scenarios calling `that entry` must have exactly one entry in changes[]")
        .hasSize(1);
    return changes.get(0);
  }

  private void assertEntryMatchesExpectation(JsonNode entry, Map<String, String> expected) {
    assertThat(entry.path("field_identifier").asText())
        .as("field_identifier on changes[] entry")
        .isEqualTo(expected.get("field_identifier"));
    assertThat(entry.path("change_source").asText())
        .as("change_source on changes[] entry")
        .isEqualTo(expected.get("change_source"));
    assertSideMatches(entry, "before", expected.get("before"));
    assertSideMatches(entry, "after", expected.get("after"));
  }

  private void assertSideMatches(JsonNode entry, String side, String expectedRaw) {
    if (expectedRaw == null) {
      return; // column omitted → no expectation
    }
    JsonNode node = entry.get(side);
    if ("null".equalsIgnoreCase(expectedRaw.trim())) {
      assertThat(node).as("`%s` node", side).isNotNull();
      assertThat(node.isNull()).as("`%s` must be JSON null", side).isTrue();
      return;
    }
    assertThat(node).as("`%s` node", side).isNotNull();

    // Numeric values in the diff round-trip as JSON numbers, so a scenario cell "100.00" would
    // arrive as "100.0" via node.asText(). Compare as BigDecimal when both sides parse cleanly
    // so decimal-scale differences don't cause false failures — string equality otherwise.
    String expected = expectedRaw.trim();
    if (node.isNumber() && expected.matches("-?\\d+(\\.\\d+)?")) {
      assertThat(new java.math.BigDecimal(node.asText()))
          .as("`%s` numeric value on changes[] entry", side)
          .isEqualByComparingTo(new java.math.BigDecimal(expected));
      return;
    }
    assertThat(node.asText()).as("`%s` value on changes[] entry", side).isEqualTo(expected);
  }
}
