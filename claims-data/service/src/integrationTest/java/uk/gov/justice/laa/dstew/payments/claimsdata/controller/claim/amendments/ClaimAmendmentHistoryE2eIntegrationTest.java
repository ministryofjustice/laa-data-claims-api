package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.model.ClearType;
import org.mockserver.model.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.helper.MockServerIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * End-to-end coverage for the claim-history AMENDMENT event (DSTEW-1813 / DSTEW-1814).
 *
 * <p>This is the top of the test pyramid: it drives a <b>real</b> amendment through the public HTTP
 * PATCH endpoint (with the PDA and FSP external calls stubbed via MockServer), then reads the
 * public {@code GET /history} endpoint and asserts the AMENDMENT event that the full pipeline
 * produced. Unlike {@code JdbcClaimHistoryRepositoryIntegrationTest} - which writes a fabricated
 * {@code diff} JSON directly - nothing here is hand-crafted: the {@code changes} shape is produced
 * by the genuine {@code AmendmentChangeDetector} / {@code AmendmentDiffAssembler} pipeline,
 * persisted, and read back through the history SQL. It therefore also guards the end-to-end {@code
 * change_source} casing ("REQUESTED" / "FSP").
 *
 * <p>It is deliberately a separate class from {@code ClaimAmendmentRepricingIntegrationTest}: that
 * suite owns the repricing/commit behaviour; this suite owns the history read-back assertions.
 *
 * <p><b>Explicit-null (field cleared) is intentionally NOT exercised here.</b> A cleared field
 * (before=value, after=explicit null) cannot currently be driven through the PATCH endpoint: {@code
 * ClaimPatch} models fields as plain {@code @Nullable String} (so a JSON explicit null and an
 * omitted field both deserialize to {@code null}), and {@code ClaimMapper.map(String)} collapses
 * {@code null -> JsonNullable.undefined()} - i.e. "no change". Empirically, PATCHing {@code
 * client2Surname: null} returns 204, leaves the field unchanged and records an empty diff. The
 * null-in-history presence semantics (DSTEW-1814) are therefore proven where they are expressible:
 * {@code JdbcClaimHistoryRepositoryIntegrationTest.retainsClearedClient2SurnameAsExplicitNull}.
 * TODO(DSTEW write-side): once the amendment PATCH contract carries an explicit null (e.g. {@code
 * JsonNullable} fields on {@code ClaimPatch} or raw-body presence detection), add an end-to-end
 * cleared-field scenario here.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("Claim Amendment History E2E (DSTEW-1813 / DSTEW-1814) Integration Test")
class ClaimAmendmentHistoryE2eIntegrationTest extends MockServerIntegrationTest {

  private static final String PATCH_A_CLAIM_ENDPOINT =
      API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";
  private static final String HISTORY_ENDPOINT = API_URI_PREFIX + "/claims/{claimId}/history";

  private static final String AMENDMENT_USER_ID = "00000000-0000-0000-0000-000000000001";
  private static final String REQUESTED_BY_PROVIDER = "PROVIDER";
  private static final String REASON_PROVIDER_ERROR = "PROVIDER_ERROR";

  // Derived FSP-consequence flag keys on the AMENDMENT event metadata (DSTEW-1762).
  private static final String FLAG_PRICING_RECALCULATED = "pricing_recalculated";
  private static final String FLAG_PRICE_CHANGED = "price_changed";
  private static final String FLAG_ESCAPE_CASE_LOGGED = "escape_case_logged";

  @SuppressWarnings("java:S1075")
  private static final String FEE_CALCULATION_PATH = "/api/v1/fee-calculation";

  @Autowired private ClaimsApiProperties claimsApiProperties;

  private boolean originalAmendmentFlag;

  @BeforeEach
  void setUp() throws Exception {
    originalAmendmentFlag = claimsApiProperties.getAmendments().isEnabled();
    claimsApiProperties.getAmendments().setEnabled("true");

    seedClaimsData();

    // Let the genuine AmendmentExternalValidationStep run against controlled external responses.
    stubExternalValidationEndpoints();
    // CLAIM_1 belongs to a LEGAL_HELP submission; keep the fee-code Area-of-Law gate happy.
    stubFeeDetailsAreaOfLaw("LEGAL_HELP");

    // Put CLAIM_1 into an amendable state and give it the baseline calculated fee the repricing
    // path requires (otherwise the amendment is rejected with CFD_MISSING before any diff is
    // built).
    Claim claim1 = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    claim1.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(claim1);
    createBaselineCalculatedFeeDetail(claim1);

    // Each test controls (and asserts on) the fee-calculation stub itself.
    mockServerClient.clear(request().withPath(FEE_CALCULATION_PATH), ClearType.EXPECTATIONS);
  }

  @AfterEach
  void tearDown() {
    claimsApiProperties.getAmendments().setEnabled(String.valueOf(originalAmendmentFlag));
  }

  @Test
  @DisplayName(
      "A repricing amendment surfaces an AMENDMENT history event with the requested change and FSP consequence")
  void repricingAmendmentSurfacesAmendmentHistoryEvent() throws Exception {
    // A pricing-impacting requested change: it produces a REQUESTED diff entry AND triggers the FSP
    // recalculation whose fee delta is recorded as FSP-sourced diff entries.
    ClaimPatch patch = basePatch();
    patch.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));

    // FSP returns a total that differs from the baseline (100.00), so the recalculation is a
    // genuine
    // consequence of the requested change.
    String fspResponse =
        "{\"feeCode\":\"FEE-123\",\"schemeId\":\"SCHEME-TEST\",\"escapeCaseFlag\":false,"
            + "\"feeCalculation\":{\"totalAmount\":650.00,\"netProfitCostsAmount\":450.00,"
            + "\"vatIndicator\":true}}";
    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION_PATH))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(fspResponse));

    // --- Act 1: submit the amendment through the public PATCH endpoint ---
    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    // The amendment is committed; grab its id so we can assert the event's source_id.
    List<ClaimAmendment> amendments =
        claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID);
    assertThat(amendments).hasSize(1);
    UUID amendmentId = amendments.getFirst().getId();

    // --- Act 2: read the public history endpoint ---
    String body =
        mockMvc
            .perform(
                get(HISTORY_ENDPOINT, CLAIM_1_ID).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode events = OBJECT_MAPPER.readTree(body).get("events");
    JsonNode amendmentEvent = firstEventOfType(events, "AMENDMENT");

    // --- Assert: envelope + metadata (DSTEW-1813) ---
    assertThat(amendmentEvent).as("an AMENDMENT event is present in the timeline").isNotNull();
    assertThat(amendmentEvent.get("actor_id").asText()).isEqualTo(AMENDMENT_USER_ID);
    assertThat(amendmentEvent.get("source_id").asText()).isEqualTo(amendmentId.toString());
    assertThat(amendmentEvent.get("event_timestamp").isNull()).isFalse();

    JsonNode metadata = amendmentEvent.get("metadata");
    assertThat(metadata.get("requested_by_code").asText()).isEqualTo(REQUESTED_BY_PROVIDER);
    assertThat(metadata.get("amendment_reason_code").asText()).isEqualTo(REASON_PROVIDER_ERROR);

    // --- Assert: change detail (DSTEW-1814) ---
    JsonNode changes = metadata.get("changes");
    assertThat(changes).as("changes array is present").isNotNull();

    // Every entry is well-formed: field_identifier + change_source present, before/after keys
    // exist.
    for (JsonNode change : changes) {
      assertThat(change.hasNonNull("field_identifier")).isTrue();
      assertThat(change.hasNonNull("change_source")).isTrue();
      assertThat(change.has("before")).isTrue();
      assertThat(change.has("after")).isTrue();
      assertThat(change.get("change_source").asText()).isIn("REQUESTED", "FSP");
    }

    // This repricing amendment produces exactly one REQUESTED change and the FSP fee consequences.
    assertThat(changes).hasSize(5);
    assertThat(countByChangeSource(changes, "REQUESTED")).isEqualTo(1);
    assertThat(countByChangeSource(changes, "FSP")).isEqualTo(4);

    // --- REQUESTED change: the provider's edit, with its actual before/after values ---
    // Seeded claimSummaryFee net profit costs (250) -> the amended value (9999.00).
    JsonNode requested = changeByField(changes, "claimSummaryFee.netProfitCostsAmount");
    assertThat(requested.get("change_source").asText()).isEqualTo("REQUESTED");
    assertThat(requested.get("before").decimalValue()).isEqualByComparingTo("250");
    assertThat(requested.get("after").decimalValue()).isEqualByComparingTo("9999.00");

    // --- FSP consequences: the recalculated fee values returned by the stubbed FSP response ---
    // Total fee recalculated from the baseline (100.00) to the FSP-returned total (650.00).
    JsonNode fspTotal = changeByField(changes, "fee.totalAmount");
    assertThat(fspTotal.get("change_source").asText()).isEqualTo("FSP");
    assertThat(fspTotal.get("before").decimalValue()).isEqualByComparingTo("100.00");
    assertThat(fspTotal.get("after").decimalValue()).isEqualByComparingTo("650.00");

    // Fee net profit costs: previously unset on the baseline (explicit null) -> FSP value (450.00).
    JsonNode fspNetProfitCosts = changeByField(changes, "fee.netProfitCostsAmount");
    assertThat(fspNetProfitCosts.get("change_source").asText()).isEqualTo("FSP");
    assertThat(fspNetProfitCosts.get("before").isNull()).isTrue();
    assertThat(fspNetProfitCosts.get("after").decimalValue()).isEqualByComparingTo("450.00");

    // VAT indicator: previously unset (explicit null) -> FSP value (true).
    JsonNode fspVatIndicator = changeByField(changes, "fee.vatIndicator");
    assertThat(fspVatIndicator.get("change_source").asText()).isEqualTo("FSP");
    assertThat(fspVatIndicator.get("before").isNull()).isTrue();
    assertThat(fspVatIndicator.get("after").asBoolean()).isTrue();

    // Scheme id: previously unset (explicit null) -> FSP value ("SCHEME-TEST").
    JsonNode fspSchemeId = changeByField(changes, "fee.schemeId");
    assertThat(fspSchemeId.get("change_source").asText()).isEqualTo("FSP");
    assertThat(fspSchemeId.get("before").isNull()).isTrue();
    assertThat(fspSchemeId.get("after").asText()).isEqualTo("SCHEME-TEST");

    // --- Assert: FSP consequence FLAGS (DSTEW-1762) ---
    // These derive from the calculated_fee_detail row that the commit pipeline links to this
    // amendment (calculated_fee_detail.claim_amendment_id = am.id). A pricing amendment therefore
    // sets pricing_recalculated=true; the FSP total moved 100.00 -> 650.00 so price_changed=true;
    // the FSP escapeCaseFlag stayed false (no fee.escapeCaseFlag change entry) so
    // escape_case_logged=false.
    assertThat(metadata.get(FLAG_PRICING_RECALCULATED).asBoolean()).isTrue();
    assertThat(metadata.get(FLAG_PRICE_CHANGED).asBoolean()).isTrue();
    assertThat(metadata.get(FLAG_ESCAPE_CASE_LOGGED).asBoolean()).isFalse();
  }

  @Test
  @DisplayName(
      "A repricing amendment that crosses the escape threshold surfaces escape_case_logged=true")
  void repricingAmendmentCrossingEscapeThresholdSetsEscapeFlag() throws Exception {
    // The baseline calculated fee has escapeCaseFlag=false; the FSP now returns
    // escapeCaseFlag=true,
    // so the diff carries an FSP-sourced fee.escapeCaseFlag change (false -> true) and the history
    // SQL derives escape_case_logged=true.
    ClaimPatch patch = basePatch();
    patch.setNetProfitCostsAmount(BigDecimal.valueOf(15000.00));

    String fspResponse =
        "{\"feeCode\":\"FEE-ESCAPE\",\"schemeId\":\"SCHEME-TEST\",\"escapeCaseFlag\":true,"
            + "\"feeCalculation\":{\"totalAmount\":15000.00,\"netProfitCostsAmount\":15000.00,"
            + "\"vatIndicator\":true}}";
    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION_PATH))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(fspResponse));

    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    String body =
        mockMvc
            .perform(
                get(HISTORY_ENDPOINT, CLAIM_1_ID).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode events = OBJECT_MAPPER.readTree(body).get("events");
    JsonNode amendmentEvent = firstEventOfType(events, "AMENDMENT");
    assertThat(amendmentEvent).as("an AMENDMENT event is present in the timeline").isNotNull();

    JsonNode metadata = amendmentEvent.get("metadata");

    // The FSP escapeCaseFlag transition (false -> true) is recorded as an FSP-sourced change entry.
    JsonNode escapeChange = changeByField(metadata.get("changes"), "fee.escapeCaseFlag");
    assertThat(escapeChange.get("change_source").asText()).isEqualTo("FSP");
    assertThat(escapeChange.get("after").asBoolean()).isTrue();

    // All three FSP consequence flags are true for an escape-threshold-crossing repricing.
    assertThat(metadata.get(FLAG_PRICING_RECALCULATED).asBoolean()).isTrue();
    assertThat(metadata.get(FLAG_PRICE_CHANGED).asBoolean()).isTrue();
    assertThat(metadata.get(FLAG_ESCAPE_CASE_LOGGED).asBoolean()).isTrue();
  }

  @Test
  @DisplayName(
      "A non-pricing amendment surfaces an AMENDMENT event whose changes are all REQUESTED (no FSP)")
  void nonPricingAmendmentSurfacesRequestedOnlyChanges() throws Exception {
    // A non-pricing requested change must not trigger FSP, so every diff entry is
    // REQUESTED-sourced.
    String amendedForename = "NewForename";
    ClaimPatch patch = basePatch();
    patch.setClientForename(amendedForename);

    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    String body =
        mockMvc
            .perform(
                get(HISTORY_ENDPOINT, CLAIM_1_ID).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode events = OBJECT_MAPPER.readTree(body).get("events");
    JsonNode amendmentEvent = firstEventOfType(events, "AMENDMENT");
    assertThat(amendmentEvent).as("an AMENDMENT event is present in the timeline").isNotNull();

    JsonNode changes = amendmentEvent.get("metadata").get("changes");
    assertThat(changes).isNotNull();

    // Exactly the one provider-requested field change, with its actual before/after values, and no
    // FSP consequence (a forename change does not impact pricing). "Alice" is the seeded baseline.
    assertThat(changes).hasSize(1);
    JsonNode nameChange = changeByField(changes, "client.clientForename");
    assertThat(nameChange.get("change_source").asText()).isEqualTo("REQUESTED");
    assertThat(nameChange.get("before").asText()).isEqualTo(SEEDED_CLIENT_FORENAME);
    assertThat(nameChange.get("after").asText()).isEqualTo(amendedForename);

    // No FSP repricing occurred, so no calculated_fee_detail row is linked to this amendment: every
    // FSP consequence flag stays false.
    JsonNode metadata = amendmentEvent.get("metadata");
    assertThat(metadata.get(FLAG_PRICING_RECALCULATED).asBoolean()).isFalse();
    assertThat(metadata.get(FLAG_PRICE_CHANGED).asBoolean()).isFalse();
    assertThat(metadata.get(FLAG_ESCAPE_CASE_LOGGED).asBoolean()).isFalse();

    // FSP must never be called for a non-pricing amendment.
    mockServerClient.verify(
        request().withPath(FEE_CALCULATION_PATH),
        org.mockserver.verify.VerificationTimes.exactly(0));
  }

  @Test
  @DisplayName(
      "A failed amendment (stale version, 409) writes nothing and produces no AMENDMENT history event")
  void failedAmendmentProducesNoHistoryEvent() throws Exception {
    // Drive a genuinely failing amendment through the public PATCH endpoint: a stale claim version
    // is rejected with 409 before anything is committed. Parent AC: a failed amendment attempt is
    // not business history, so the timeline must contain no AMENDMENT event for it. This is the
    // write-to-read counterpart to the write-side rollback suites (which prove no claim_amendment
    // row is written); here we additionally prove the failure never surfaces on the read side.
    ClaimPatch patch = basePatch();
    patch.setVersion(999L); // stale - does not match the claim's current version
    patch.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));

    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict());

    // Write side: the failed attempt persisted no claim_amendment row.
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID)).isEmpty();

    // Read side: the timeline carries no AMENDMENT event for the failed attempt.
    String body =
        mockMvc
            .perform(
                get(HISTORY_ENDPOINT, CLAIM_1_ID).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode events = OBJECT_MAPPER.readTree(body).get("events");
    assertThat(firstEventOfType(events, "AMENDMENT"))
        .as("a failed amendment must not appear as a timeline event")
        .isNull();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private ClaimPatch basePatch() {
    ClaimPatch patch = new ClaimPatch();
    patch.setVersion(1L);
    patch.setAmendmentUserId(UUID.fromString(AMENDMENT_USER_ID));
    patch.setAmendmentRequestedBy(REQUESTED_BY_PROVIDER);
    patch.setAmendmentReasonCode(REASON_PROVIDER_ERROR);
    return patch;
  }

  private void createBaselineCalculatedFeeDetail(Claim claim) {
    ClaimSummaryFee summaryFee =
        claimSummaryFeeRepository
            .findByClaimId(claim.getId())
            .orElseGet(
                () ->
                    claimSummaryFeeRepository.saveAndFlush(
                        ClaimSummaryFee.builder()
                            .id(Uuid7.timeBasedUuid())
                            .claim(claim)
                            .createdByUserId("Test")
                            .build()));

    CalculatedFeeDetail cfd = new CalculatedFeeDetail();
    cfd.setId(Uuid7.timeBasedUuid());
    cfd.setClaim(claim);
    cfd.setClaimSummaryFee(summaryFee);
    cfd.setEscapeCaseFlag(false);
    cfd.setFeeCode("FEE-123");
    cfd.setTotalAmount(BigDecimal.valueOf(100.00));
    cfd.setCreatedByUserId("Test");
    cfd.setCreatedOn(Instant.now().minus(1, ChronoUnit.DAYS));
    calculatedFeeDetailRepository.saveAndFlush(cfd);
  }

  private static JsonNode firstEventOfType(JsonNode events, String eventType) {
    for (JsonNode event : events) {
      if (eventType.equals(event.path("event_type").asText())) {
        return event;
      }
    }
    return null;
  }

  /** Returns the single change with the given field identifier, failing if it is absent. */
  private static JsonNode changeByField(JsonNode changes, String fieldIdentifier) {
    for (JsonNode change : changes) {
      if (fieldIdentifier.equals(change.path("field_identifier").asText())) {
        return change;
      }
    }
    throw new AssertionError("No change found for field_identifier '" + fieldIdentifier + "'");
  }

  private static long countByChangeSource(JsonNode changes, String changeSource) {
    long count = 0;
    for (JsonNode change : changes) {
      if (changeSource.equals(change.path("change_source").asText())) {
        count++;
      }
    }
    return count;
  }
}
