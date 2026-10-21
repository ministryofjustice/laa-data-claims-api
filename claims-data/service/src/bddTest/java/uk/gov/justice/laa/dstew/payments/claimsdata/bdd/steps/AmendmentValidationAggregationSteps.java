package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.BddMockServerSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step definitions for {@code amendmentsValidationAggregation.feature} (DSTEW-1770).
 *
 * <p>Owns the Step-12 cross-source aggregation glue. Four aggregation sources are driven with their
 * real shipped triggers on a fully-valid Legal Help claim (seeded by the reused {@code an original
 * claim exists with area of law} step), so several ERROR-severity issues from different sources
 * aggregate into one Step-12 multi-message response. PDA aggregation is covered separately by
 * DSTEW-1774. This class owns only the new phrases: the collected-failure composers, colliding
 * sibling setup, the collected-then-terminal composers (an earlier step collects an ERROR before a
 * later FATAL gate; the orchestrator appends rather than replaces, so the terminal response still
 * carries the earlier code), and strict envelope assertions; seed, submit, and generic outcome
 * assertions are reused from sibling amendment step classes.
 */
@Slf4j
public class AmendmentValidationAggregationSteps {

  private static final String SEED_ACTOR = "bdd-DSTEW-1770";
  private static final String VALID_REQUESTED_BY = "PROVIDER";
  private static final String VALID_REASON = "PROVIDER_ERROR";
  private static final String VALID_USER_ID = "0190b6a0-9b7e-7c8a-9e2d-177000000001";
  // The UCN the AmendableClaimFixture seeds on the target claim's Client; the colliding sibling
  // carries the same UCN so the post-amendment duplicate key matches.
  private static final String TARGET_UCN = "01011990/A/BCDE";
  // A fee code the fee-details stub resolves to a DIFFERENT Area of Law, to drive the terminal
  // gate.
  private static final String CROSS_AOL_FEE_CODE = "AGG2";
  private static final String NON_AMENDABLE_FIELD = "representation_order_date";
  private static final String LONG_FORENAME = "A".repeat(500);

  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private BddScenarioContext scenarioContext;
  @Autowired private BddMockServerSupport mock;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClientRepository clientRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // Scenario-scoped: the codes the scenario declared it will collect (used by the strict
  // assertion).
  private final List<String> expectedCollectedCodes = new ArrayList<>();

  // ---------------------------------------------------------------------------
  // Given — colliding sibling + PDA mismatch setup (DS1770_1)
  // ---------------------------------------------------------------------------

  @Given("a colliding sibling claim exists whose duplicate key matches the post-amendment state")
  public void aCollidingSiblingClaimExists() {
    Claim target = requireTarget();
    // Seed a VALID sibling on the SAME submission sharing the target's fee code, UFN and (via its
    // Client) UCN, so the post-amendment target state duplicates it in the same submission
    // (INVALID_CLAIM_HAS_DUPLICATE_IN_SAME_SUBMISSION). The duplicate key is
    // office + fee_code + UFN + UCN, so the sibling needs a Client carrying the same UCN.
    Claim sibling =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(Uuid7.timeBasedUuid())
                .submission(target.getSubmission())
                .status(ClaimStatus.VALID)
                .feeCode(target.getFeeCode())
                .lineNumber(2)
                .matterTypeCode(target.getMatterTypeCode())
                .scheduleReference(target.getScheduleReference())
                .uniqueFileNumber(target.getUniqueFileNumber())
                .caseReferenceNumber("CRN-1770-SIB")
                .caseStartDate(target.getCaseStartDate())
                .caseConcludedDate(target.getCaseConcludedDate())
                .createdByUserId(SEED_ACTOR)
                .build());
    clientRepository.saveAndFlush(
        Client.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(sibling)
            .clientForename("Jane")
            .clientSurname("Smith")
            .clientDateOfBirth(LocalDate.of(1990, Month.JANUARY, 1))
            .uniqueClientNumber(TARGET_UCN)
            .clientPostcode("SW1H 9HE")
            .genderCode("F")
            .ethnicityCode("99")
            .disabilityCode("COG")
            .createdByUserId(SEED_ACTOR)
            .createdOn(Instant.now())
            .build());
    log.info(
        "[DS1770] seeded colliding sibling (feeCode={}, ufn={}, ucn={}) on submission {}",
        target.getFeeCode(),
        target.getUniqueFileNumber(),
        TARGET_UCN,
        target.getSubmission().getId());
  }

  // ---------------------------------------------------------------------------
  // Given — collected-failure composers
  // ---------------------------------------------------------------------------

  @Given("an amendment is submitted that triggers the following collected failures")
  public void anAmendmentTriggersTheFollowingCollectedFailures(DataTable table) {
    Set<String> sources = new LinkedHashSet<>();
    expectedCollectedCodes.clear();
    for (Map<String, String> row : table.asMaps(String.class, String.class)) {
      sources.add(sourceKey(row.get("source")));
      String code = row.get("expectedErrorCode");
      if (code != null && !code.isBlank()) {
        expectedCollectedCodes.add(code.trim());
      }
    }

    ObjectNode patch = baseValidPatch();
    if (sources.contains("metadata")) {
      // A malformed amendment_user_id fails Jackson parsing ("Failed to read request") and
      // short-circuits before validation, so it cannot be aggregated. An UNKNOWN requested-by code
      // deserializes cleanly and is collected as an ERROR (INVALID_REQUESTED_BY_UNKNOWN).
      patch.put("amendment_requested_by", "RB_NOT_A_REAL_CODE");
    }
    if (sources.contains("amendability")) {
      patch.put(NON_AMENDABLE_FIELD, "15/07/2025");
    }
    if (sources.contains("reusable")) {
      patch.put("client_forename", LONG_FORENAME);
    }
    // NOTE: the duplicate source is armed by the seeded colliding sibling (see
    // aCollidingSiblingClaimExists) which shares the target's unchanged duplicate key, so no patch
    // mutation is needed. PDA-validation aggregation (INVALID_AREA_OF_LAW_FOR_PROVIDER) is proven
    // in
    // its own DSTEW-1774 file, which owns the non-matching /schedules fixture; it is intentionally
    // not co-triggered here because it needs a fee-code change whose post-amendment key would then
    // have to be kept consistent with the duplicate sibling.
    publish(patch);
    log.info("[DS1770] composed collected-failure patch for sources {}", sources);
  }

  @Given("an amendment is submitted that triggers a single collected error with code {string}")
  public void anAmendmentTriggersASingleCollectedError(String code) {
    ObjectNode patch = baseValidPatch();
    if ("SCHEMA_VALIDATION_ERROR".equals(code)) {
      patch.put("client_forename", LONG_FORENAME);
    } else {
      throw new IllegalArgumentException("Unsupported single collected error code: " + code);
    }
    expectedCollectedCodes.clear();
    expectedCollectedCodes.add(code);
    publish(patch);
  }

  @Given(
      "an amendment is submitted that triggers no collected validation error and impacts pricing")
  public void anAmendmentTriggersNoCollectedErrorAndImpactsPricing() throws IOException {
    // A valid, amendable, pricing-impacting change (case_start_date) drives the flow past Step 12
    // into the Step 13 FSP trigger. Stub PDA + FSP OK so both external calls succeed.
    mock.stubProviderSchedulesOk();
    mock.stubAmendmentFspOk();
    ObjectNode patch = baseValidPatch();
    patch.put("case_start_date", "15/07/2025");
    publish(patch);
  }

  @Given(
      "an amendment is submitted that first collects {string} from an earlier step then hits"
          + " terminal {string}")
  public void anAmendmentCollectsEarlierThenHitsTerminal(String collectedCode, String terminalKind)
      throws IOException {
    // Drive a genuine, NON-fatal ERROR from a step that runs BEFORE the terminal gate so the
    // orchestrator has already collected it when the fatal arrives.
    // INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW is raised by FieldAmendabilityValidationStep
    // (pipeline step 7) — well before AmendmentExternalValidationStep (step 10) where the fee-code
    // gate lives. (The previous SCHEMA_VALIDATION_ERROR driver proved nothing: it is produced
    // INSIDE AmendmentExternalValidationStep AFTER the fee-code gate already returned, so it was
    // never collected first.)
    ObjectNode patch = baseValidPatch();
    expectedCollectedCodes.clear();
    if ("INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW".equals(collectedCode)) {
      patch.put(NON_AMENDABLE_FIELD, "15/07/2025");
    } else {
      throw new IllegalArgumentException("Unsupported earlier-collected code: " + collectedCode);
    }
    expectedCollectedCodes.add(collectedCode);
    armTerminal(terminalKind, patch);
    publish(patch);
  }

  @Given(
      "an amendment is submitted that would edit a non-amendable field but trips the early terminal"
          + " {string} first")
  public void anAmendmentTripsEarlyTerminalBeforeCollection(String terminalKind)
      throws IOException {
    // The patch requests an amendability-violating edit (would raise
    // INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW at pipeline step 7), but an EARLY fatal gate
    // short-circuits before that step runs, so nothing is ever collected and the terminal response
    // carries the terminal code alone.
    ObjectNode patch = baseValidPatch();
    expectedCollectedCodes.clear();
    patch.put(NON_AMENDABLE_FIELD, "15/07/2025");
    armTerminal(terminalKind, patch);
    publish(patch);
  }

  private void armTerminal(String terminalKind, ObjectNode patch) throws IOException {
    switch (terminalKind.trim()) {
      case "fee-code Area-of-Law change" -> {
        // fee-details resolves a DIFFERENT Area of Law → FATAL INVALID_FEE_CODE_AREA_OF_LAW_CHANGE
        // inside AmendmentExternalValidationStep (pipeline step 10).
        mock.stubFeeDetailsAreaOfLaw("CRIME_LOWER");
        mock.stubProviderSchedulesOk();
        patch.put("fee_code", CROSS_AOL_FEE_CODE);
      }
      case "OCC version conflict" ->
          // A stale/mismatched version trips the early version gate (pipeline step 3) →
          // CLAIM_VERSION_CONFLICT, short-circuiting before any later step can collect.
          patch.put("version", 99);
      default -> throw new IllegalArgumentException("Unsupported terminal kind: " + terminalKind);
    }
  }

  @Given("an amendment is submitted that omits the required metadata fields")
  public void anAmendmentOmitsRequiredMetadataFields(DataTable table) {
    expectedCollectedCodes.clear();
    for (Map<String, String> row : table.asMaps(String.class, String.class)) {
      String code = row.get("Error Code");
      if (code != null && !code.isBlank()) {
        expectedCollectedCodes.add(code.trim());
      }
    }
    // Omit amendment_requested_by + amendment_reason_code (→ their MISSING codes); include a valid
    // user id and a genuine, amendable field change so the request is not a no-op.
    ObjectNode patch = objectMapper.createObjectNode();
    patch.put("client_surname", "Jones");
    patch.put("amendment_user_id", VALID_USER_ID);
    patch.put("version", currentVersion());
    publish(patch);
  }

  // ---------------------------------------------------------------------------
  // Then — strict aggregation / envelope assertions
  // ---------------------------------------------------------------------------

  @Then("the aggregated response strictly carries each collected code")
  public void theAggregatedResponseStrictlyCarriesEachCollectedCode() {
    JsonNode body = scenarioContext.getLastResponseBody();
    Integer status = scenarioContext.getLastStatusCode();
    assertThat(status)
        .as("aggregated rejection must be a client error (body=%s)", body)
        .isNotNull()
        .isBetween(400, 499);
    assertThat(body).as("aggregated rejection must carry a JSON body").isNotNull();
    assertThat(expectedCollectedCodes)
        .as("scenario must declare the collected codes it expects")
        .isNotEmpty();
    // Assert against the STRUCTURED errors[].code list rather than a raw substring match on the
    // serialised body: a substring check can pass spuriously (e.g. a code echoed inside a message
    // or an unrelated field) without the code actually being a distinct aggregated error entry.
    assertThat(errorCodes())
        .as(
            "aggregated Step-12 response must carry each collected code as a distinct entry (body=%s)",
            body)
        .containsAll(expectedCollectedCodes);
  }

  @Then("the response strictly carries terminal code {string}")
  public void theResponseStrictlyCarriesTerminalCode(String code) {
    JsonNode body = scenarioContext.getLastResponseBody();
    Integer status = scenarioContext.getLastStatusCode();
    assertThat(body).as("terminal response must carry a JSON body").isNotNull();
    assertThat(status)
        .as("terminal failure must produce an error status (body=%s)", body)
        .isNotNull()
        .isGreaterThanOrEqualTo(400);
    assertThat(errorCodes())
        .as(
            "terminal response must carry code %s as a structured error entry (body=%s)",
            code, body)
        .contains(code);
  }

  @Then("the response also carries the earlier collected code {string}")
  public void theResponseAlsoCarriesEarlierCollectedCode(String code) {
    JsonNode body = scenarioContext.getLastResponseBody();
    assertThat(body).as("terminal response must carry a JSON body").isNotNull();
    // The orchestrator APPENDS prior errors rather than replacing them when a fatal arrives
    // (ClaimAmendmentValidationService returns state.getErrors(); the exception handler renders
    // every entry). So an error collected by an EARLIER step must still be present alongside the
    // terminal code — that is the collected-then-terminal behaviour this scenario proves.
    assertThat(errorCodes())
        .as(
            "terminal response must still carry the earlier-collected code %s alongside the"
                + " terminal (body=%s)",
            code, body)
        .contains(code);
  }

  @Then("the response envelope matches the existing structured validation error contract")
  public void theResponseEnvelopeMatchesTheStructuredContract() {
    JsonNode body = scenarioContext.getLastResponseBody();
    assertThat(body).as("rejection must carry a ProblemDetail body").isNotNull();
    assertThat(body.path("status").isNumber())
        .as("ProblemDetail envelope must expose a numeric status (body=%s)", body)
        .isTrue();
    assertThat(body.path("errors").isArray())
        .as("multi-message envelope must expose a nested errors array (body=%s)", body)
        .isTrue();
    assertThat(body.path("errors").size())
        .as("multi-message envelope must carry at least the declared errors (body=%s)", body)
        .isGreaterThanOrEqualTo(expectedCollectedCodes.size());
  }

  @Then("each message includes a distinct error code")
  public void eachMessageIncludesADistinctErrorCode() {
    List<String> codes = errorCodes();
    assertThat(codes).as("declared codes must be present").containsAll(expectedCollectedCodes);
    assertThat(codes)
        .as("each aggregated error must carry a distinct code (codes=%s)", codes)
        .doesNotHaveDuplicates();
  }

  @Then("each message preserves the user-displayable text supplied by the source validator")
  public void eachMessagePreservesUserDisplayableText() {
    JsonNode errors = scenarioContext.getLastResponseBody().path("errors");
    assertThat(errors.isArray()).isTrue();
    errors.forEach(
        node ->
            assertThat(node.path("message").asText(""))
                .as("each aggregated error must preserve a non-blank user-displayable message")
                .isNotBlank());
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private ObjectNode baseValidPatch() {
    ObjectNode patch = objectMapper.createObjectNode();
    patch.put("amendment_requested_by", VALID_REQUESTED_BY);
    patch.put("amendment_reason_code", VALID_REASON);
    patch.put("amendment_user_id", VALID_USER_ID);
    patch.put("version", currentVersion());
    return patch;
  }

  private void publish(ObjectNode patch) {
    sharedPatchContext.setPatchJson(patch.toString());
  }

  private int currentVersion() {
    Long version = requireTarget().getVersion();
    return version == null ? 0 : version.intValue();
  }

  private Claim requireTarget() {
    return claimRepository
        .findById(sharedPatchContext.getClaimId())
        .orElseThrow(() -> new AssertionError("target claim not seeded for DS1770 scenario"));
  }

  private List<String> errorCodes() {
    JsonNode errors = scenarioContext.getLastResponseBody().path("errors");
    List<String> codes = new ArrayList<>();
    if (errors.isArray()) {
      errors.forEach(
          node -> {
            String code = node.path("code").asText(null);
            if (code != null) {
              codes.add(code);
            }
          });
    }
    return codes;
  }

  private static String sourceKey(String source) {
    if (source == null) {
      return "";
    }
    String lower = source.toLowerCase();
    if (lower.contains("metadata")) {
      return "metadata";
    }
    if (lower.contains("amendability")) {
      return "amendability";
    }
    if (lower.contains("reusable")) {
      return "reusable";
    }
    if (lower.contains("pda")) {
      return "pda";
    }
    if (lower.contains("duplicate")) {
      return "duplicate";
    }
    throw new IllegalArgumentException("Unrecognised aggregation source: " + source);
  }
}
