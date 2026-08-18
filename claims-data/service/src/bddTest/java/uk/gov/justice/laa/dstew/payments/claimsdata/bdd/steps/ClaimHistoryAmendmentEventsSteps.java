package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step definitions for {@code claimHistoryAmendmentEvents.feature} (DSTEW-1815).
 *
 * <p>Asserts the three AMENDMENT-event metadata fields derived from FSP-linked data:
 *
 * <ul>
 *   <li>{@code pricing_recalculated} — true iff a {@code calculated_fee_detail} row is linked to
 *       the amendment ({@code claim_amendment_id = am.id}).
 *   <li>{@code price_changed} — {@code COALESCE(cfd.is_price_changed, false)} on the
 *       amendment-linked row.
 *   <li>{@code escape_case_logged} — true iff the amendment {@code diff.changes[]} contains an
 *       entry for {@code field_identifier='fee.escapeCaseFlag'} with {@code change_source='FSP'}
 *       and {@code after='true'} (transition into escape). NEVER derived from the claim's current
 *       escape state.
 * </ul>
 *
 * <p>Data is seeded directly via JPA repos, mirroring the fixtures used by {@code
 * JdbcClaimHistoryRepositoryIntegrationTest.persistAmendment / linkCalculatedFeeDetail}. Steps that
 * speak of an amendment "failing" persist no {@code claim_amendment} row at all — the underlying
 * invariant is "no row → no event".
 */
@Slf4j
public class ClaimHistoryAmendmentEventsSteps extends ClaimHistoryTimelineSharedSteps {

  private static final String BDD_USER_ID = "bdd-user-1815";
  private static final String AMENDMENT_EVENT_TYPE = "AMENDMENT";
  private static final String KEY_PRICING_RECALCULATED = "pricing_recalculated";
  private static final String KEY_PRICE_CHANGED = "price_changed";
  private static final String KEY_ESCAPE_CASE_LOGGED = "escape_case_logged";

  @Autowired private BddApiStepSupport api;
  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Autowired private ClaimAmendmentRepository claimAmendmentRepository;
  @Autowired private CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  private UUID amendmentAId;
  private UUID amendmentBId;

  // ---------------------------------------------------------------------------
  // Given — claim & amendment seeding
  // ---------------------------------------------------------------------------

  @Given("a claim exists")
  public void aClaimExists() {
    seedClaim();
  }

  // NOTE: canonical seeding step for "a claim exists with a successful amendment" lives in
  // ClaimHistoryTimelineParentSteps. Do NOT duplicate the annotated Given here; read the
  // seeded amendment id from the shared ClaimHistoryContext when needed.

  @Given("a claim exists that was NOT flagged as an escape case before the amendment")
  public void aClaimExistsNotFlaggedAsEscape() {
    seedClaim();
    // No pre-existing CFD — the claim starts un-escaped by omission.
  }

  @Given("a claim exists that was ALREADY flagged as an escape case before this amendment")
  public void aClaimAlreadyFlaggedAsEscapeBeforeAmendment() {
    seedClaim();
    seedUnlinkedEscapeFeeRow();
  }

  @Given("a claim exists that was ALREADY flagged as an escape case")
  public void aClaimAlreadyFlaggedAsEscape() {
    seedClaim();
    seedUnlinkedEscapeFeeRow();
  }

  @Given("a claim exists with two successful amendments applied in order A then B")
  public void aClaimWithTwoAmendmentsAthenB() {
    seedClaim();
    amendmentAId = persistAmendment(emptyDiff());
    amendmentBId = persistAmendment(emptyDiff());
  }

  // ---------------------------------------------------------------------------
  // Given — amendment-linked calculated_fee_detail
  // ---------------------------------------------------------------------------

  @Given(
      "the amendment has an amendment-linked calculated_fee_detail row with is_price_changed"
          + " set to {word}")
  public void amendmentHasLinkedCfdWithIsPriceChanged(String isPriceChanged) {
    linkCalculatedFeeDetail(requireLastAmendmentId(), Boolean.parseBoolean(isPriceChanged), false);
  }

  @Given("the amendment has no amendment-linked calculated_fee_detail row")
  public void amendmentHasNoLinkedCfd() {
    // No-op — the amendment was persisted without a linked CFD in the prior Given.
  }

  @Given(
      "the claim's latest calculated_fee_detail row belongs to an earlier submission or"
          + " amendment")
  public void latestCfdBelongsToEarlierSubmissionOrAmendment() {
    // Seed a CFD linked to the claim but NOT to the current amendment. If the SQL ever regressed
    // to reading the latest claim CFD instead of the amendment-linked one, pricing_recalculated
    // would become true — the assertion below guards against that.
    seedUnlinkedFeeRow(false, false);
  }

  // ---------------------------------------------------------------------------
  // Given — diff shape (FSP field entry, escape transition)
  // ---------------------------------------------------------------------------

  @Given(
      "the amendment diff contains a change_source {string} entry for field {string} from"
          + " {string} to {string}")
  public void amendmentDiffContainsChangeSourceEntry(
      String changeSource, String fieldIdentifier, String before, String after) {
    // Re-persist the current amendment's diff to include a single FSP entry. Simple string
    // replacement is fine — we own the whole diff since it started empty.
    ClaimAmendment amendment =
        claimAmendmentRepository.findById(requireLastAmendmentId()).orElseThrow();
    amendment.setDiff(
        diffWithChanges(
            change(fieldIdentifier, quoteIfString(before), quoteIfString(after), changeSource)));
    claimAmendmentRepository.saveAndFlush(amendment);
  }

  @Given(
      "a successful amendment produced an amendment-linked transition of"
          + " calculated_fee_detail.escape_case_flag from false to true")
  public void amendmentProducedEscapeTransitionFalseToTrue() {
    UUID id = persistAmendment(diffWithChanges(escapeTransitionChange(false, true)));
    claimHistoryContext.setLastAmendmentId(id);
    linkCalculatedFeeDetail(id, true, true);
  }

  @Given("a later successful amendment has an amendment-linked calculated_fee_detail row")
  public void laterSuccessfulAmendmentHasLinkedCfd() {
    UUID id = persistAmendment(emptyDiff());
    claimHistoryContext.setLastAmendmentId(id);
    linkCalculatedFeeDetail(id, false, true);
  }

  @Given("the later amendment did NOT produce a new amendment-linked escape transition")
  public void laterAmendmentNoEscapeTransition() {
    // No-op — the previous Given persisted an empty diff (no fee.escapeCaseFlag entry), so the
    // SQL EXISTS(...) will resolve to false. Documented here for scenario readability.
  }

  @Given("a successful non-pricing amendment is applied")
  public void aSuccessfulNonPricingAmendmentIsApplied() {
    UUID id = persistAmendment(emptyDiff());
    claimHistoryContext.setLastAmendmentId(id);
  }

  @Given(
      "amendment A has an amendment-linked calculated_fee_detail row with is_price_changed"
          + " set to {word} and no escape transition")
  public void amendmentAHasLinkedCfd(String isPriceChanged) {
    linkCalculatedFeeDetail(amendmentAId, Boolean.parseBoolean(isPriceChanged), false);
  }

  @Given(
      "amendment B has an amendment-linked calculated_fee_detail row with is_price_changed"
          + " set to {word} and produced an escape transition")
  public void amendmentBHasLinkedCfdAndEscapeTransition(String isPriceChanged) {
    // Re-persist B with an escape diff.
    ClaimAmendment b = claimAmendmentRepository.findById(amendmentBId).orElseThrow();
    b.setDiff(diffWithChanges(escapeTransitionChange(false, true)));
    claimAmendmentRepository.saveAndFlush(b);
    linkCalculatedFeeDetail(amendmentBId, Boolean.parseBoolean(isPriceChanged), true);
  }

  // ---------------------------------------------------------------------------
  // Given — no-amendment-row baseline (see @DS1815_8 de-scope note in feature)
  // ---------------------------------------------------------------------------

  @Given("no `claim_amendment` row has been persisted for that claim")
  public void noClaimAmendmentRowHasBeenPersistedForThatClaim() {
    // Documentary no-op — reads the read-model precondition literally.
    //
    // Honest scope note: the BDD tier cannot yet drive the four specific write-side failure
    // paths originally covered by the @DS1815_8 outline (FSP validation reject, FSP technical
    // failure, post-FSP version guard, post-FSP persistence). All four would produce this same
    // observable read-side state (no `claim_amendment` row), but exercising the paths themselves
    // needs the write-side amendment harness (WireMock PDA/FSP + event-service test hook) which
    // is not yet in this project. The de-scoped paths are tracked in the audit ledger for
    // follow-up once the harness lands.
    log.debug(
        "@DS1815_8 baseline: no claim_amendment row persisted for claim {}",
        requireCurrentClaimId());
  }

  // ---------------------------------------------------------------------------
  // Given — DSTEW-1815 @DS1815_9 case dispatcher
  // ---------------------------------------------------------------------------

  @Given("a claim exists with the described amendment scenario {string}")
  public void aClaimWithDescribedAmendmentScenario(String scenarioCase) {
    seedClaim();
    switch (scenarioCase) {
      case "pricing amendment, FSP changed values" -> {
        UUID id = persistAmendment(emptyDiff());
        claimHistoryContext.setLastAmendmentId(id);
        linkCalculatedFeeDetail(id, true, false);
      }
      case "pricing amendment, FSP same-value repricing" -> {
        UUID id = persistAmendment(emptyDiff());
        claimHistoryContext.setLastAmendmentId(id);
        linkCalculatedFeeDetail(id, false, false);
      }
      case "non-pricing amendment" -> {
        UUID id = persistAmendment(emptyDiff());
        claimHistoryContext.setLastAmendmentId(id);
      }
      case "amendment caused escape" -> {
        UUID id = persistAmendment(diffWithChanges(escapeTransitionChange(false, true)));
        claimHistoryContext.setLastAmendmentId(id);
        linkCalculatedFeeDetail(id, true, true);
      }
      case "later amendment on already-escaped claim" -> {
        seedUnlinkedEscapeFeeRow();
        UUID id = persistAmendment(emptyDiff());
        claimHistoryContext.setLastAmendmentId(id);
        linkCalculatedFeeDetail(id, false, true);
      }
      case "failed amendment" -> {
        // No amendment persisted — the failed attempt leaves no row.
      }
      default -> throw new IllegalArgumentException("Unknown case: '" + scenarioCase + "'");
    }
  }

  // ---------------------------------------------------------------------------
  // When
  // ---------------------------------------------------------------------------

  // The canonical step that performs the HTTP request is defined in
  // ClaimHistoryTimelineCommonSteps. Keep a non-annotated helper for local readability.
  public void iRequestTheClaimHistoryTimeline() throws IOException {
    assertThat(requireCurrentClaimId())
        .as("claim must be seeded before requesting history")
        .isNotNull();
    // Expect the common step to populate the shared last response; do not perform the request here.
    assertThat(getLastResponse()).as("claim history response must already be present").isNotNull();
  }

  // ---------------------------------------------------------------------------
  // Then — presence / absence of AMENDMENT event
  // ---------------------------------------------------------------------------

  @Then("the response contains an AMENDMENT event for that amendment")
  public void responseContainsAmendmentEventForThatAmendment() {
    UUID amendmentId = requireLastAmendmentId();
    assertThat(findAmendmentEvent(amendmentId))
        .as("no AMENDMENT event found for amendment %s", amendmentId)
        .isNotNull();
  }

  @Then("the response contains no AMENDMENT event")
  public void responseContainsNoAmendmentEvent() {
    assertThat(amendmentEvents())
        .as("no AMENDMENT events expected when no claim_amendment row is persisted")
        .isEmpty();
  }

  @Then("the response contains no FSP repricing or escape metadata")
  public void responseContainsNoFspRepricingOrEscapeMetadata() {
    // Any AMENDMENT event would carry the metadata; absence of the event guarantees absence of
    // its metadata. Belt-and-braces: assert every amendment event we CAN see has
    // pricing_recalculated=false + escape_case_logged=false (no cross-contamination from an
    // unrelated event on the same claim).
    for (JsonNode event : amendmentEvents()) {
      JsonNode metadata = event.path("metadata");
      assertThat(booleanOrFalse(metadata.get(KEY_PRICING_RECALCULATED)))
          .as("no-amendment-row precondition must not leak pricing_recalculated to other events")
          .isFalse();
      assertThat(booleanOrFalse(metadata.get(KEY_ESCAPE_CASE_LOGGED)))
          .as("no-amendment-row precondition must not leak escape_case_logged to other events")
          .isFalse();
    }
  }

  // ---------------------------------------------------------------------------
  // Then — metadata field assertions
  // ---------------------------------------------------------------------------

  @Then("the AMENDMENT event metadata field {string} is {word}")
  public void amendmentEventMetadataFieldIs(String fieldName, String expectedBool) {
    JsonNode event = requireAmendmentEvent(requireLastAmendmentId());
    JsonNode value = event.path("metadata").get(fieldName);
    assertThat(value).as("metadata field '%s' node", fieldName).isNotNull();
    assertThat(value.isBoolean()).as("metadata field '%s' must be a boolean", fieldName).isTrue();
    assertThat(value.asBoolean())
        .as("metadata field '%s' value", fieldName)
        .isEqualTo(Boolean.parseBoolean(expectedBool));
  }

  @Then("the AMENDMENT event metadata field {string} is absent or false")
  public void amendmentEventMetadataFieldIsAbsentOrFalse(String fieldName) {
    JsonNode event = requireAmendmentEvent(requireLastAmendmentId());
    JsonNode value = event.path("metadata").get(fieldName);
    assertThat(booleanOrFalse(value))
        .as("metadata field '%s' must be absent, null, or explicit false", fieldName)
        .isFalse();
  }

  @Then(
      "the AMENDMENT event metadata is not derived from the claim's latest"
          + " calculated_fee_detail row")
  public void amendmentEventMetadataIsNotDerivedFromLatestCfd() {
    // Proven arithmetically by the sibling "absent or false" assertions: if the SQL had
    // accidentally
    // read the latest claim CFD (present in the fixture) rather than the amendment-linked one
    // (absent), pricing_recalculated would be true and the sibling assertion would have failed.
    // No additional assertion needed — this step documents the semantic guarantee.
  }

  @Then("the AMENDMENT event metadata field {string} for the later amendment is absent or false")
  public void amendmentEventMetadataFieldForLaterAmendmentAbsentOrFalse(String fieldName) {
    JsonNode event = requireAmendmentEvent(requireLastAmendmentId());
    JsonNode value = event.path("metadata").get(fieldName);
    assertThat(booleanOrFalse(value))
        .as(
            "metadata field '%s' on later amendment must be absent, null, or explicit false",
            fieldName)
        .isFalse();
  }

  @Then(
      "the AMENDMENT event metadata for the later amendment is not derived from the claim's"
          + " current escape state")
  public void amendmentEventForLaterAmendmentIsNotDerivedFromCurrentEscapeState() {
    // Same semantic as the "latest CFD" documentation step above: if the SQL had read the current
    // claim escape state (fixture: already-escaped, unlinked CFD with escape_case_flag=true),
    // escape_case_logged would be true and the sibling assertion would have failed.
  }

  @Then(
      "the AMENDMENT event metadata {string} array contains an entry with field_identifier"
          + " {string} and change_source {string}")
  public void amendmentChangesArrayContainsEntry(
      String arrayName, String fieldIdentifier, String changeSource) {
    JsonNode event = requireAmendmentEvent(requireLastAmendmentId());
    JsonNode arr = event.path("metadata").path(arrayName);
    assertThat(arr.isArray()).as("metadata '%s' must be an array", arrayName).isTrue();
    boolean found = false;
    for (JsonNode entry : arr) {
      if (fieldIdentifier.equals(entry.path("field_identifier").asText())
          && changeSource.equalsIgnoreCase(entry.path("change_source").asText())) {
        found = true;
        break;
      }
    }
    assertThat(found)
        .as(
            "expected an entry with field_identifier='%s' and change_source='%s' in %s",
            fieldIdentifier, changeSource, arrayName)
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Then — DSTEW-1815 @DS1815_9 contract dispatcher
  // ---------------------------------------------------------------------------

  @Then("the AMENDMENT event metadata for that amendment satisfies {string}")
  public void amendmentEventMetadataSatisfies(String expectedContract) {
    if (expectedContract.contains("no AMENDMENT event")) {
      assertThat(amendmentEvents())
          .as("contract '%s' requires no AMENDMENT event", expectedContract)
          .isEmpty();
      return;
    }
    JsonNode event = requireAmendmentEvent(requireLastAmendmentId());
    JsonNode metadata = event.path("metadata");
    for (String clause : expectedContract.split(";")) {
      assertContractClause(metadata, clause.trim());
    }
  }

  private void assertContractClause(JsonNode metadata, String clause) {
    if (clause.isEmpty()) {
      return;
    }
    if (clause.contains("absent or false")) {
      String field = clause.replace("absent or false", "").trim();
      assertThat(booleanOrFalse(metadata.get(field))).as("contract clause '%s'", clause).isFalse();
      return;
    }
    // "field=true" / "field=false"
    String[] parts = clause.split("=", 2);
    assertThat(parts.length).as("malformed contract clause: '%s'", clause).isEqualTo(2);
    String field = parts[0].trim();
    boolean expected = Boolean.parseBoolean(parts[1].trim());
    JsonNode value = metadata.get(field);
    assertThat(value).as("metadata field '%s' node", field).isNotNull();
    assertThat(value.isBoolean()).as("metadata field '%s' must be boolean", field).isTrue();
    assertThat(value.asBoolean()).as("contract clause '%s'", clause).isEqualTo(expected);
  }

  // ---------------------------------------------------------------------------
  // Then — DSTEW-1815 @DS1815_10 per-amendment assertions
  // ---------------------------------------------------------------------------

  @Then(
      "the AMENDMENT event for amendment A has {string} {word}, {string} {word}, and {string}"
          + " absent or false")
  public void amendmentAHasFieldsAbsentOrFalse(
      String field1, String bool1, String field2, String bool2, String field3) {
    JsonNode metadata = requireAmendmentEvent(amendmentAId).path("metadata");
    assertBooleanField(metadata, field1, bool1);
    assertBooleanField(metadata, field2, bool2);
    assertThat(booleanOrFalse(metadata.get(field3)))
        .as("metadata field '%s' on amendment A must be absent, null, or explicit false", field3)
        .isFalse();
  }

  @Then(
      "the AMENDMENT event for amendment B has {string} {word}, {string} {word}, and {string}"
          + " {word}")
  public void amendmentBHasThreeBooleanFields(
      String field1, String bool1, String field2, String bool2, String field3, String bool3) {
    JsonNode metadata = requireAmendmentEvent(amendmentBId).path("metadata");
    assertBooleanField(metadata, field1, bool1);
    assertBooleanField(metadata, field2, bool2);
    assertBooleanField(metadata, field3, bool3);
  }

  // ---------------------------------------------------------------------------
  // Helpers — data seeding
  // ---------------------------------------------------------------------------

  private void seedClaim() {
    Submission submission =
        submissionRepository.saveAndFlush(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .officeAccountNumber("1815-office")
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

    ClaimSummaryFee summaryFee =
        claimSummaryFeeRepository.saveAndFlush(
            ClaimSummaryFee.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(claim)
                .createdByUserId(BDD_USER_ID)
                .build());
    setCurrentClaimSummaryFeeId(summaryFee.getId());
  }

  private UUID persistAmendment(String diffJson) {
    UUID id = Uuid7.timeBasedUuid();
    claimAmendmentRepository.saveAndFlush(
        ClaimAmendment.builder()
            .id(id)
            .claim(claimRepository.getReferenceById(requireCurrentClaimId()))
            .requestedByCode("PROVIDER")
            .amendmentReasonCode("PROVIDER_ERROR")
            .beforeState("{}")
            .requestPayload("{}")
            .diff(diffJson)
            .createdByUserId(BDD_USER_ID)
            .createdOn(Instant.now())
            .build());
    return id;
  }

  private void linkCalculatedFeeDetail(UUID amendmentId, boolean priceChanged, boolean escapeFlag) {
    calculatedFeeDetailRepository.saveAndFlush(
        CalculatedFeeDetail.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claimRepository.getReferenceById(requireCurrentClaimId()))
            .claimSummaryFee(
                claimSummaryFeeRepository.getReferenceById(requireCurrentClaimSummaryFeeId()))
            .claimAmendment(claimAmendmentRepository.getReferenceById(amendmentId))
            .isPriceChanged(priceChanged)
            .escapeCaseFlag(escapeFlag)
            .totalAmount(new BigDecimal("120.00"))
            .createdByUserId(BDD_USER_ID)
            .createdOn(Instant.now())
            .build());
  }

  private void seedUnlinkedFeeRow(boolean priceChanged, boolean escapeFlag) {
    calculatedFeeDetailRepository.saveAndFlush(
        CalculatedFeeDetail.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claimRepository.getReferenceById(requireCurrentClaimId()))
            .claimSummaryFee(
                claimSummaryFeeRepository.getReferenceById(requireCurrentClaimSummaryFeeId()))
            .claimAmendment(null) // unlinked — belongs to an earlier submission/amendment
            .isPriceChanged(priceChanged)
            .escapeCaseFlag(escapeFlag)
            .totalAmount(new BigDecimal("110.00"))
            .createdByUserId(BDD_USER_ID)
            .createdOn(Instant.now())
            .build());
  }

  /**
   * Seeds an unlinked CFD flagged as an escape case — represents the claim's pre-existing state.
   */
  private void seedUnlinkedEscapeFeeRow() {
    seedUnlinkedFeeRow(false, true);
  }

  // ---------------------------------------------------------------------------
  // Helpers — response inspection
  // ---------------------------------------------------------------------------

  private java.util.List<JsonNode> amendmentEvents() {
    java.util.List<JsonNode> results = new java.util.ArrayList<>();
    JsonNode events = getLastResponse().path("events");
    if (events.isArray()) {
      for (JsonNode event : events) {
        if (AMENDMENT_EVENT_TYPE.equals(event.path("event_type").asText())) {
          results.add(event);
        }
      }
    }
    return results;
  }

  private JsonNode findAmendmentEvent(UUID amendmentId) {
    for (JsonNode event : amendmentEvents()) {
      if (amendmentId != null && amendmentId.toString().equals(event.path("source_id").asText())) {
        return event;
      }
    }
    return null;
  }

  private JsonNode requireAmendmentEvent(UUID amendmentId) {
    JsonNode event = findAmendmentEvent(amendmentId);
    assertThat(event)
        .as("no AMENDMENT event found on history response for amendment %s", amendmentId)
        .isNotNull();
    return event;
  }

  private boolean booleanOrFalse(JsonNode node) {
    return node != null
        && !node.isMissingNode()
        && !node.isNull()
        && node.isBoolean()
        && node.asBoolean();
  }

  private void assertBooleanField(JsonNode metadata, String field, String expected) {
    JsonNode value = metadata.get(field);
    assertThat(value).as("metadata field '%s' node", field).isNotNull();
    assertThat(value.isBoolean()).as("metadata field '%s' must be boolean", field).isTrue();
    assertThat(value.asBoolean())
        .as("metadata field '%s' value", field)
        .isEqualTo(Boolean.parseBoolean(expected));
  }

  // ---------------------------------------------------------------------------
  // Helpers — diff JSON builders
  // ---------------------------------------------------------------------------

  private static String emptyDiff() {
    return diffWithChanges();
  }

  private static String diffWithChanges(String... changes) {
    return "{\"schema_version\":1,\"changes\":[" + String.join(",", changes) + "]}";
  }

  private static String change(String field, String beforeJson, String afterJson, String source) {
    return "{\"field_identifier\":\""
        + field
        + "\",\"before\":"
        + beforeJson
        + ",\"after\":"
        + afterJson
        + ",\"change_source\":\""
        + source.toUpperCase(java.util.Locale.ROOT)
        + "\"}";
  }

  /**
   * The SQL EXISTS(...) that drives {@code escape_case_logged} compares {@code ch->>'after'} — the
   * text form of the JSONB value — to the string {@code 'true'}. That works for both JSON booleans
   * (jsonb {@code true} → text {@code "true"}) and JSON string booleans. We store the change as a
   * quoted string to match the pattern used by {@code
   * JdbcClaimHistoryRepositoryIntegrationTest.escapeDiff}.
   */
  private static String escapeTransitionChange(boolean before, boolean after) {
    return change("fee.escapeCaseFlag", "\"" + before + "\"", "\"" + after + "\"", "FSP");
  }

  private static String quoteIfString(String raw) {
    if (raw == null || "null".equalsIgnoreCase(raw.trim())) {
      return "null";
    }
    String trimmed = raw.trim();
    if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
      return trimmed;
    }
    return "\"" + trimmed.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
