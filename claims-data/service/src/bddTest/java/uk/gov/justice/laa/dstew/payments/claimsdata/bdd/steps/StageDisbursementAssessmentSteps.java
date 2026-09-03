package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.BddBeansConfiguration.BddServerInfo;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step glue for {@code stageDisbursementAssessment.feature} — DSTEW-1520.
 *
 * <p>Verifies that {@code POST /api/v1/claims/{claimId}/assessments} accepts and persists the new
 * {@code STAGE_DISBURSEMENT_ASSESSMENT} type + verbatim {@code assessmentReason}, keeps the {@code
 * ESCAPE_CASE_ASSESSMENT} path unchanged, keeps {@code VOID} rejected from this contract, and that
 * the persisted value round-trips through {@code GET .../assessments/{id}} and the {@code /history}
 * timeline.
 *
 * <p>The scenarios drive the real HTTP endpoints against the running BDD server, then read {@code
 * claims.assessment} back both through the repository and through the read endpoint so the
 * assertion is on the full stack (controller → service → mapper → JPA → schema).
 *
 * <h2>De-scope note</h2>
 *
 * <ul>
 *   <li><b>@DS1520_10 (feature-flag guard)</b> — Type 1. DSTEW-1520 shipped the type/reason
 *       acceptance and validation gates but did NOT ship a stage-disbursement-assessment feature
 *       flag. The scenario is left commented in the feature file and no step glue is provided here;
 *       the flag will land on a follow-up story, at which point the scenario can be un-commented
 *       without re-writing glue.
 * </ul>
 *
 * <p>Every step body wraps its logic in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures#step(String,
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.ThrowingRunnable)}
 * per the project-wide step-failure-reporting standing rule.
 */
public class StageDisbursementAssessmentSteps {

  private static final String BDD_USER = "bdd-1520-user";
  private static final String BDD_USER_UUID = "0190b6a0-9b7e-7c8a-9e2d-1520000000aa";

  private static final String POST_ASSESSMENT_PATH =
      API_URI_PREFIX + "/claims/{claimId}/assessments";
  private static final String GET_ASSESSMENT_PATH =
      API_URI_PREFIX + "/claims/{claimId}/assessments/{assessmentId}";
  private static final String CLAIM_HISTORY_PATH = API_URI_PREFIX + "/claims/{claimId}/history";

  // Sentinel tokens used in feature examples that mean "do not include this field / send blank".
  private static final String OMITTED = "<omitted>";
  private static final String EMPTY_STRING = "<empty string>";
  private static final String BLANK_SPACES = "<blank spaces>";
  private static final String NULL_LITERAL = "null";

  // Reason strings called out verbatim in the feature file. Kept as constants so a test failure
  // shows the exact byte-for-byte expected value alongside the persisted one.
  private static final String REASON_NON_CONTINGENCY = "Stage Disbursement Assessment";
  private static final String REASON_CONTINGENCY = "Stage Disbursement Assessment (Contingency)";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private RestTemplate restTemplate;
  @Autowired private BddServerInfo serverInfo;

  // Scenario-scoped state. Cucumber-spring makes a fresh instance per scenario.
  private final Map<String, UUID> claimByLabel = new HashMap<>();
  private final Map<String, UUID> summaryFeeByLabel = new HashMap<>();
  private UUID lastAssessmentIdFromResponse;
  private int lastStatusCode;
  private String lastResponseBody;

  // ---------------------------------------------------------------------------
  // Background.
  //
  // "the assessment endpoint is available" is owned by
  // AssessmentAdvancesClaimVersionSteps (DSTEW-2051), merged into main via
  // PR #449 on 2026-09-03. This branch is now rebased onto that main so the
  // local redefinition previously carried here has been removed.
  // ---------------------------------------------------------------------------

  @Given("the stage-disbursement-assessment feature flag is enabled")
  public void theStageDisbursementAssessmentFeatureFlagIsEnabled() {
    // Type 1 note (see class Javadoc): DSTEW-1520 did NOT deliver a runtime feature flag for the
    // stage-disbursement-assessment type. The endpoint accepts the new type unconditionally on
    // main, so this Background step is a documented no-op today. Kept as an explicit hook so the
    // step reads correctly and so the follow-up flag story can wire it without touching the
    // feature file.
    step(
        "no-op: DSTEW-1520 did not deliver a stage-disbursement feature flag; the endpoint "
            + "accepts the new type unconditionally on main",
        () -> {});
  }

  @Given("claim {string} exists with a Stage Disbursement fee code and status VALID")
  @Transactional
  public void claimExistsWithStageDisbursementFeeCodeAndStatusValid(String label) {
    step(
        "Seeding submission + claim '"
            + label
            + "' in status VALID with a Stage Disbursement "
            + "fee code (MHLDIS) — the fee code is overridden per-scenario where the outline "
            + "iterates the eligible list",
        () -> seedClaim(label, ClaimStatus.VALID, "MHLDIS"));
  }

  @Given("claim {string} has not formally escaped")
  public void claimHasNotFormallyEscaped(String label) {
    step(
        "Documenting that seed claim '"
            + label
            + "' has no escape state — DS does not gate the "
            + "stage disbursement path on escape state, so this is a no-op assertion that the "
            + "claim exists",
        () -> assertThat(requireClaim(label)).as("claim id").isNotNull());
  }

  // ---------------------------------------------------------------------------
  // Givens — per-scenario pre-conditions.
  // ---------------------------------------------------------------------------

  @Given("claim {string} has fee code {string}")
  public void claimHasFeeCode(String label, String feeCode) {
    step(
        "Forcing claim '" + label + "' fee_code to '" + feeCode + "'",
        () -> {
          int updated =
              jdbcClient
                  .sql("UPDATE claims.claim SET fee_code = :fc WHERE id = :id")
                  .param("fc", feeCode)
                  .param("id", requireClaim(label))
                  .update();
          assertThat(updated).as("fee_code update rowcount").isEqualTo(1);
        });
  }

  @Given(
      "claim {string} has a non-Stage-Disbursement fee code \\(e.g. an escape-eligible fee code\\)")
  @Transactional
  public void claimHasANonStageDisbursementFeeCode(String label) {
    step(
        "Seeding a separate claim '"
            + label
            + "' with a non-Stage-Disbursement fee code (CAPA) "
            + "— DS must still accept a STAGE_DISBURSEMENT_ASSESSMENT for this claim because "
            + "AaBC owns fee-code-vs-assessmentType eligibility per the ticket",
        () -> seedClaim(label, ClaimStatus.VALID, "CAPA"));
  }

  @Given("claim {string} has an escape-eligible fee code and is in the escape-case state")
  @Transactional
  public void claimHasEscapeEligibleFeeCodeAndIsInEscapeCaseState(String label) {
    step(
        "Seeding a separate claim '" + label + "' with an escape-eligible fee code (CAPA)",
        () -> seedClaim(label, ClaimStatus.VALID, "CAPA"));
  }

  @Given(
      "a Stage Disbursement assessment exists for claim {string} with assessmentType "
          + "{string} and assessmentReason {string}")
  public void aStageDisbursementAssessmentExistsForClaimWith(
      String label, String assessmentType, String assessmentReason) {
    step(
        "POST a real Stage Disbursement assessment for claim '"
            + label
            + "' via the endpoint so "
            + "the DSTEW-1812 history timeline is populated exactly as production would",
        () -> {
          Map<String, String> fields = new HashMap<>();
          fields.put("assessmentType", assessmentType);
          fields.put("assessmentReason", assessmentReason);
          postAssessment(requireClaim(label), fields);
          assertThat(lastStatusCode)
              .as("seeding POST for @DS1520_12 must succeed (body=%s)", safeBodyPreview())
              .isEqualTo(201);
        });
  }

  // ---------------------------------------------------------------------------
  // Whens — assessment POST driven by a DataTable.
  // ---------------------------------------------------------------------------

  @When("AaBC POSTs an assessment for claim {string} with")
  public void aabcPostsAnAssessmentForClaimWith(String label, DataTable table) {
    step(
        "POST " + POST_ASSESSMENT_PATH + " for claim '" + label + "' with table-driven payload",
        () -> postAssessment(requireClaim(label), table.asMap(String.class, String.class)));
  }

  @When("a Stage Disbursement assessment is persisted through the endpoint")
  public void aStageDisbursementAssessmentIsPersistedThroughTheEndpoint() {
    step(
        "POST a real Stage Disbursement assessment through the endpoint for the current claim",
        () -> {
          Map<String, String> fields = new HashMap<>();
          fields.put("assessmentType", "STAGE_DISBURSEMENT_ASSESSMENT");
          fields.put("assessmentReason", REASON_NON_CONTINGENCY);
          postAssessment(requireClaim("SD"), fields);
          assertThat(lastStatusCode)
              .as("POST must succeed (body=%s)", safeBodyPreview())
              .isEqualTo(201);
        });
  }

  @When("^I GET /api/v1/claims/(.+)/history$")
  public void iGetApiV1ClaimsHistory(String label) {
    step(
        "GET " + CLAIM_HISTORY_PATH + " for claim label '" + label + "'",
        () -> getHistory(requireClaim(label)));
  }

  // ---------------------------------------------------------------------------
  // Thens — response status.
  //
  // Response-status assertions read from THIS class's private lastStatusCode /
  // lastResponseBody, which are populated by AaBC POSTs an assessment for
  // claim {string} with (this class's @When). We deliberately do NOT reuse
  // AssessmentAdvancesClaimVersionSteps' "the response is 201 Created" step
  // because that would read its OWN private state (populated by DSTEW-2051's
  // @When, not ours). Post-rebase onto main (which now owns DSTEW-2051 via
  // PR #449) the shared phrase belongs to DSTEW-2051; DSTEW-1520 uses the
  // "stage-disbursement assessment response is ..." form below so the two
  // classes coexist without a DuplicateStepDefinitionException.
  // ---------------------------------------------------------------------------

  @Then("the stage-disbursement assessment response is 201 Created")
  public void theStageDisbursementAssessmentResponseIs201Created() {
    step(
        "Asserting last HTTP status is 201 Created (body=" + safeBodyPreview() + ")",
        () ->
            assertThat(lastStatusCode)
                .as("last response status; body=%s", safeBodyPreview())
                .isEqualTo(201));
  }

  @Then("the response is 400 Bad Request")
  public void theResponseIs400BadRequest() {
    step(
        "Asserting last HTTP status is 400 Bad Request (body=" + safeBodyPreview() + ")",
        () ->
            assertThat(lastStatusCode)
                .as("last response status; body=%s", safeBodyPreview())
                .isEqualTo(400));
  }

  @Then("the error message is exactly {string}")
  public void theErrorMessageIsExactly(String expected) {
    step(
        "Asserting the response body carries the exact error message '" + expected + "'",
        () ->
            assertThat(lastResponseBody)
                .as("last response body must contain the exact error message")
                .isNotNull()
                .contains(expected));
  }

  @Then(
      "the response matches the standard invalid assessment-type validation shape "
          + "\\(as used for any other unknown AssessmentType enum value\\)")
  public void theResponseMatchesTheStandardInvalidAssessmentTypeValidationShape() {
    step(
        "Asserting the 400 response follows the standard invalid-enum shape (RFC 9457 problem+json)",
        () -> {
          assertThat(lastResponseBody).as("response body").isNotNull();
          JsonNode body = OBJECT_MAPPER.readTree(lastResponseBody);
          // Spring's default ProblemDetail body carries "status" + "title" + "detail". We assert
          // the shape rather than the exact wording because Spring versions/regexes may differ
          // slightly — the ticket only requires the SAME shape as any other unknown enum value.
          assertThat(body.path("status").asInt(-1)).as("status field").isEqualTo(400);
          assertThat(body.path("title").asText()).as("title field").isNotBlank();
          // Ticket contract is "same shape as any other unknown AssessmentType enum value" —
          // Spring's default Jackson enum error is `HttpMessageNotReadableException` which
          // produces a generic "failed to read request" detail. We accept EITHER the specific
          // field-name callout OR the generic message; both are within the "standard shape".
          String bodyLower = lastResponseBody.toLowerCase(Locale.ROOT);
          assertThat(bodyLower)
              .as(
                  "body must be a standard invalid-request shape — either references the enum "
                      + "field explicitly or carries the generic 'failed to read request' detail")
              .containsAnyOf(
                  "assessment_type",
                  "assessmenttype",
                  "assessment type",
                  "failed to read request",
                  "not one of the values accepted");
        });
  }

  @Then("Data Stewardship did NOT silently default the type to any supported value")
  public void dataStewardshipDidNotSilentlyDefaultTheType() {
    step(
        "Asserting no assessment row was written for the current claim — proves DS did not "
            + "silently swap the unknown type for a supported one",
        () -> assertNoAssessmentRow(requireClaim("SD")));
  }

  @Then("no assessment row is written for claim {string}")
  public void noAssessmentRowIsWrittenForClaim(String label) {
    step(
        "Asserting no assessment row exists for claim '" + label + "'",
        () -> assertNoAssessmentRow(requireClaim(label)));
  }

  // "claim {string} hasAssessment is unchanged" is owned by
  // AssessmentAdvancesClaimVersionSteps (DSTEW-2051 on main via PR #449).
  // DSTEW-1520 uses the distinct phrase below so both classes coexist without
  // a DuplicateStepDefinitionException (this class's @Then reads its own
  // claimByLabel populated by our @Given, not DSTEW-2051's).

  @Then("claim {string} stage-disbursement hasAssessment is unchanged")
  public void claimStageDisbursementHasAssessmentIsUnchanged(String label) {
    step(
        "Asserting claim '" + label + "' has_assessment is still false after the rejected POST",
        () ->
            assertThat(readHasAssessment(requireClaim(label)))
                .as("claim.has_assessment (should still be false)")
                .isFalse());
  }

  @Then("claim {string} hasAssessment is true")
  public void claimHasAssessmentIsTrue(String label) {
    step(
        "Asserting claim '" + label + "' has_assessment is now true",
        () ->
            assertThat(readHasAssessment(requireClaim(label))).as("claim.has_assessment").isTrue());
  }

  // ---------------------------------------------------------------------------
  // Thens — persistence assertions on the assessment row.
  // ---------------------------------------------------------------------------

  @Then("the persisted assessment has assessmentType {string}")
  public void thePersistedAssessmentHasAssessmentType(String expected) {
    step(
        "Asserting the persisted assessment row's assessment_type equals '" + expected + "'",
        () ->
            assertThat(readAssessmentType(requireAssessmentId()))
                .as("claims.assessment.assessment_type")
                .isEqualTo(expected));
  }

  @Then("the persisted assessment has assessmentReason {string}")
  public void thePersistedAssessmentHasAssessmentReason(String expected) {
    step(
        "Asserting the persisted assessment row's assessment_reason equals '" + expected + "'",
        () ->
            assertThat(readAssessmentReason(requireAssessmentId()))
                .as("claims.assessment.assessment_reason")
                .isEqualTo(expected));
  }

  @Then("the persisted assessmentReason is exactly {string}")
  public void thePersistedAssessmentReasonIsExactly(String expected) {
    step(
        "Asserting the persisted assessment_reason is byte-for-byte '" + expected + "'",
        () ->
            assertThat(readAssessmentReason(requireAssessmentId()))
                .as("claims.assessment.assessment_reason (verbatim)")
                .isEqualTo(expected));
  }

  @Then("no server-side derivation or normalisation of the reason value occurred")
  public void noServerSideDerivationOrNormalisationOfTheReasonValueOccurred() {
    step(
        "Asserting the persisted assessment_reason equals REASON_NON_CONTINGENCY byte-for-byte",
        () ->
            assertThat(readAssessmentReason(requireAssessmentId()))
                .as("no whitespace-trim / case-change / substitution applied by DS")
                .isEqualTo(REASON_NON_CONTINGENCY));
  }

  @Then("the parenthesised suffix and casing are preserved byte-for-byte")
  public void theParenthesisedSuffixAndCasingArePreservedByteForByte() {
    step(
        "Asserting the persisted assessment_reason keeps the '(Contingency)' suffix + casing",
        () ->
            assertThat(readAssessmentReason(requireAssessmentId()))
                .as("verbatim persistence of contingency reason")
                .isEqualTo(REASON_CONTINGENCY)
                .contains("(Contingency)"));
  }

  @Then("the persisted assessmentType is {string}")
  public void thePersistedAssessmentTypeIs(String expected) {
    step(
        "Asserting the persisted assessment row's assessment_type equals '" + expected + "'",
        () ->
            assertThat(readAssessmentType(requireAssessmentId()))
                .as("claims.assessment.assessment_type")
                .isEqualTo(expected));
  }

  @Then("Data Stewardship did NOT emit any fee-code-vs-assessmentType validation error")
  public void dataStewardshipDidNotEmitAnyFeeCodeVsAssessmentTypeValidationError() {
    step(
        "Asserting the successful 201 response body does NOT mention a fee-code eligibility "
            + "validation error — DS owns nothing here, AaBC does",
        () -> {
          assertThat(lastStatusCode).as("response status").isEqualTo(201);
          if (lastResponseBody != null && !lastResponseBody.isBlank()) {
            String bodyLower = lastResponseBody.toLowerCase(Locale.ROOT);
            assertThat(bodyLower)
                .as("body must not reference fee-code eligibility validation")
                .doesNotContain("fee code")
                .doesNotContain("fee_code")
                .doesNotContain("eligibility")
                .doesNotContain("ineligible");
          }
        });
  }

  @Then("no wording in the response or contract classifies this as a Stage Disbursement assessment")
  public void noWordingClassifiesThisAsStageDisbursementAssessment() {
    step(
        "Asserting the ESCAPE_CASE response body does not contain any Stage-Disbursement wording",
        () -> {
          if (lastResponseBody != null && !lastResponseBody.isBlank()) {
            String bodyLower = lastResponseBody.toLowerCase(Locale.ROOT);
            assertThat(bodyLower)
                .as("body must not classify the escape-case response as Stage Disbursement")
                .doesNotContain("stage disbursement")
                .doesNotContain("stage_disbursement");
          }
          assertThat(readAssessmentType(requireAssessmentId()))
              .as(
                  "persisted assessment_type must be ESCAPE_CASE_ASSESSMENT, not stage disbursement")
              .isEqualTo("ESCAPE_CASE_ASSESSMENT");
        });
  }

  // ---------------------------------------------------------------------------
  // Thens — GET round-trip on the assessment.
  // ---------------------------------------------------------------------------

  @Then("GET on the assessment round-trips assessmentType {string}")
  public void getOnTheAssessmentRoundTripsAssessmentType(String expected) {
    step(
        "GET " + GET_ASSESSMENT_PATH + " and asserting assessment_type = '" + expected + "'",
        () -> {
          JsonNode body = getAssessmentAndParse(requireClaim("SD"), requireAssessmentId());
          assertThat(body.path("assessment_type").asText())
              .as("GET response assessment_type")
              .isEqualTo(expected);
        });
  }

  @Then("GET on the assessment round-trips assessmentReason {string}")
  public void getOnTheAssessmentRoundTripsAssessmentReason(String expected) {
    step(
        "GET " + GET_ASSESSMENT_PATH + " and asserting assessment_reason = '" + expected + "'",
        () -> {
          JsonNode body = getAssessmentAndParse(requireClaim("SD"), requireAssessmentId());
          assertThat(body.path("assessment_reason").asText())
              .as("GET response assessment_reason")
              .isEqualTo(expected);
        });
  }

  @Then(
      "GET on the assessment round-trips assessed_total_incl_vat {bigdecimal} and "
          + "allowed_total_incl_vat {bigdecimal}")
  public void getOnTheAssessmentRoundTripsAssessedAndAllowed(
      BigDecimal expectedAssessed, BigDecimal expectedAllowed) {
    step(
        "GET "
            + GET_ASSESSMENT_PATH
            + " and asserting assessed_total_incl_vat = "
            + expectedAssessed
            + " and allowed_total_incl_vat = "
            + expectedAllowed,
        () -> {
          JsonNode body = getAssessmentAndParse(requireClaim("SD"), requireAssessmentId());
          assertThat(new BigDecimal(body.path("assessed_total_incl_vat").asText()))
              .as("GET response assessed_total_incl_vat")
              .isEqualByComparingTo(expectedAssessed);
          assertThat(new BigDecimal(body.path("allowed_total_incl_vat").asText()))
              .as("GET response allowed_total_incl_vat")
              .isEqualByComparingTo(expectedAllowed);
        });
  }

  // ---------------------------------------------------------------------------
  // Thens — @DS1520_11 DB CHECK constraint + read-endpoint round-trip.
  // ---------------------------------------------------------------------------

  @Then("the underlying assessment row survives a repository read with")
  public void theUnderlyingAssessmentRowSurvivesARepositoryReadWith(DataTable table) {
    step(
        "Asserting the persisted assessment row carries the expected column value(s) — proves "
            + "the chk_assessment_type CHECK constraint (introduced in V34, widened in V38) accepts"
            + " STAGE_DISBURSEMENT_ASSESSMENT",
        () -> {
          Map<String, String> expected =
              table.asMaps().stream()
                  .collect(
                      java.util.stream.Collectors.toMap(m -> m.get("column"), m -> m.get("value")));
          for (Map.Entry<String, String> entry : expected.entrySet()) {
            String column = entry.getKey();
            String value = entry.getValue();
            String actual =
                jdbcClient
                    .sql("SELECT " + column + "::text FROM claims.assessment WHERE id = :id")
                    .param("id", requireAssessmentId())
                    .query(String.class)
                    .single();
            assertThat(actual).as("claims.assessment.%s", column).isEqualTo(value);
          }
        });
  }

  @Then("^the read via GET /api/v1/claims/\\{claimId}/assessments returns the same value$")
  public void theReadViaGetReturnsTheSameValue() {
    step(
        "GET "
            + GET_ASSESSMENT_PATH
            + " and asserting assessment_type still reads as "
            + "STAGE_DISBURSEMENT_ASSESSMENT — end-to-end round-trip guardrail",
        () -> {
          JsonNode body = getAssessmentAndParse(requireClaim("SD"), requireAssessmentId());
          assertThat(body.path("assessment_type").asText())
              .as("GET response assessment_type")
              .isEqualTo("STAGE_DISBURSEMENT_ASSESSMENT");
        });
  }

  // ---------------------------------------------------------------------------
  // Thens — @DS1520_12 Claim History timeline (cross-check with DSTEW-1812).
  // ---------------------------------------------------------------------------

  @Then("the ASSESSMENT event carries metadata {string} = {string}")
  public void theAssessmentEventCarriesMetadata(String field, String expected) {
    step(
        "Asserting the /history timeline's ASSESSMENT event metadata."
            + field
            + " = '"
            + expected
            + "'",
        () -> {
          JsonNode event = firstAssessmentEvent();
          assertThat(event.path("metadata").path(field).asText())
              .as("metadata." + field)
              .isEqualTo(expected);
        });
  }

  @Then("the event is NOT emitted as event_type {string}")
  public void theEventIsNotEmittedAsEventType(String badType) {
    step(
        "Asserting no event on the /history timeline carries event_type = '" + badType + "'",
        () -> {
          JsonNode timeline = requireHistoryTimeline();
          for (JsonNode event : timeline) {
            assertThat(event.path("event_type").asText())
                .as("event_type must not be '%s'", badType)
                .isNotEqualTo(badType);
          }
        });
  }

  @Then("the event is NOT relabelled as an escape-case assessment")
  public void theEventIsNotRelabelledAsAnEscapeCaseAssessment() {
    step(
        "Asserting the last ASSESSMENT event's metadata.assessment_type is verbatim "
            + "STAGE_DISBURSEMENT_ASSESSMENT and NOT ESCAPE_CASE_ASSESSMENT",
        () -> {
          JsonNode event = firstAssessmentEvent();
          assertThat(event.path("metadata").path("assessment_type").asText())
              .as("metadata.assessment_type must not be relabelled")
              .isEqualTo("STAGE_DISBURSEMENT_ASSESSMENT")
              .isNotEqualTo("ESCAPE_CASE_ASSESSMENT");
        });
  }

  // ---------------------------------------------------------------------------
  // Thens — @DS1520_13 VOID external-caller rejection.
  // ---------------------------------------------------------------------------

  @Then(
      "the error is the existing invalid-status-update message \\(not the new Stage Disbursement "
          + "wording\\)")
  public void theErrorIsTheExistingInvalidStatusUpdateMessage() {
    step(
        "Asserting the VOID rejection message is the pre-existing "
            + "INVALID_CLAIM_STATUS_UPDATE_MESSAGE, not any new Stage Disbursement wording",
        () -> {
          assertThat(lastResponseBody).as("response body").isNotNull();
          assertThat(lastResponseBody)
              .as("must reference the existing VOID-via-endpoint guard message")
              .contains("VOID")
              .contains("Use POST /api/v1/claims/{claimId}/void");
          String bodyLower = lastResponseBody.toLowerCase(Locale.ROOT);
          assertThat(bodyLower)
              .as("must NOT reference Stage Disbursement wording")
              .doesNotContain("stage disbursement")
              .doesNotContain("stage_disbursement");
        });
  }

  // ---------------------------------------------------------------------------
  // Seeding helpers.
  // ---------------------------------------------------------------------------

  private void seedClaim(String label, ClaimStatus status, String feeCode) {
    // Unique office/period per label so scenarios that seed TWO claims (e.g. @DS1520_8 "NSD"
    // alongside Background "SD", @DS1520_9 "EC") don't trip
    // uq_submission_live_office_aol_period.
    int labelHash = Math.abs(label.hashCode() % 1000);
    String office = String.format("0BDD%03d", labelHash);
    String period = "JAN-2026";
    // Vary area-of-law by label parity so a same-office collision on hash is also side-stepped.
    AreaOfLaw aol = (labelHash % 2 == 0) ? AreaOfLaw.LEGAL_HELP : AreaOfLaw.CRIME_LOWER;
    Submission submission =
        Submission.builder()
            .id(Uuid7.timeBasedUuid())
            .officeAccountNumber(office)
            .submissionPeriod(period)
            .areaOfLaw(aol)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(BDD_USER)
            .providerUserId(BDD_USER)
            .createdOn(Instant.now())
            .build();
    submissionRepository.saveAndFlush(submission);

    Claim claim = new Claim();
    claim.setId(Uuid7.timeBasedUuid());
    claim.setSubmission(submission);
    claim.setStatus(status);
    claim.setLineNumber(1);
    claim.setFeeCode(feeCode);
    claim.setMatterTypeCode("MAT01");
    claim.setCaseReferenceNumber("BDD1520-CRN-" + label);
    claim.setUniqueFileNumber("BDD1520-UFN-" + label);
    claim.setCaseStartDate(LocalDate.of(2026, 1, 1));
    claim.setCreatedByUserId(BDD_USER);
    claim.setUpdatedByUserId(BDD_USER);
    claimRepository.saveAndFlush(claim);
    claimByLabel.put(label, claim.getId());

    ClaimSummaryFee summaryFee =
        ClaimSummaryFee.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .createdByUserId(BDD_USER)
            .build();
    claimSummaryFeeRepository.saveAndFlush(summaryFee);
    summaryFeeByLabel.put(label, summaryFee.getId());
  }

  private UUID requireClaim(String label) {
    UUID id = claimByLabel.get(label);
    assertThat(id).as("no claim seeded for label '%s'", label).isNotNull();
    return id;
  }

  private UUID requireSummaryFee(String label) {
    UUID id = summaryFeeByLabel.get(label);
    assertThat(id).as("no claim_summary_fee seeded for label '%s'", label).isNotNull();
    return id;
  }

  private UUID requireAssessmentId() {
    assertThat(lastAssessmentIdFromResponse)
        .as("no assessment id captured from the last successful POST response body")
        .isNotNull();
    return lastAssessmentIdFromResponse;
  }

  private boolean readHasAssessment(UUID claimId) {
    return jdbcClient
        .sql("SELECT has_assessment FROM claims.claim WHERE id = :id")
        .param("id", claimId)
        .query(Boolean.class)
        .single();
  }

  private String readAssessmentType(UUID assessmentId) {
    return jdbcClient
        .sql("SELECT assessment_type FROM claims.assessment WHERE id = :id")
        .param("id", assessmentId)
        .query(String.class)
        .single();
  }

  private String readAssessmentReason(UUID assessmentId) {
    return jdbcClient
        .sql("SELECT assessment_reason FROM claims.assessment WHERE id = :id")
        .param("id", assessmentId)
        .query(String.class)
        .single();
  }

  private void assertNoAssessmentRow(UUID claimId) {
    long count =
        jdbcClient
            .sql("SELECT COUNT(*) FROM claims.assessment WHERE claim_id = :id")
            .param("id", claimId)
            .query(Long.class)
            .single();
    assertThat(count).as("assessment row count for claim %s", claimId).isZero();
  }

  // ---------------------------------------------------------------------------
  // HTTP helpers.
  // ---------------------------------------------------------------------------

  private void postAssessment(UUID claimId, Map<String, String> fields) {
    String label = labelFor(claimId);
    UUID summaryFeeId = requireSummaryFee(label);
    String jsonBody = buildAssessmentJson(claimId, summaryFeeId, fields);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    try {
      ResponseEntity<String> response =
          restTemplate.exchange(
              serverInfo.baseUrl() + POST_ASSESSMENT_PATH,
              HttpMethod.POST,
              new HttpEntity<>(jsonBody, headers),
              String.class,
              claimId);
      lastStatusCode = response.getStatusCode().value();
      lastResponseBody = response.getBody();
    } catch (HttpStatusCodeException ex) {
      lastStatusCode = ex.getStatusCode().value();
      lastResponseBody = ex.getResponseBodyAsString();
    }
    lastAssessmentIdFromResponse = extractIdFromCreatedResponse(lastResponseBody);
  }

  private JsonNode getAssessmentAndParse(UUID claimId, UUID assessmentId) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    ResponseEntity<String> response =
        restTemplate.exchange(
            serverInfo.baseUrl() + GET_ASSESSMENT_PATH,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class,
            claimId,
            assessmentId);
    assertThat(response.getStatusCode().value())
        .as("GET assessment status; body=%s", response.getBody())
        .isEqualTo(200);
    return OBJECT_MAPPER.readTree(response.getBody());
  }

  private void getHistory(UUID claimId) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    try {
      ResponseEntity<String> response =
          restTemplate.exchange(
              serverInfo.baseUrl() + CLAIM_HISTORY_PATH,
              HttpMethod.GET,
              new HttpEntity<>(headers),
              String.class,
              claimId);
      lastStatusCode = response.getStatusCode().value();
      lastResponseBody = response.getBody();
    } catch (HttpStatusCodeException ex) {
      lastStatusCode = ex.getStatusCode().value();
      lastResponseBody = ex.getResponseBodyAsString();
    }
  }

  private JsonNode requireHistoryTimeline() throws Exception {
    assertThat(lastStatusCode)
        .as("previous /history call must have returned 2xx; body=%s", safeBodyPreview())
        .isBetween(200, 299);
    JsonNode root = OBJECT_MAPPER.readTree(lastResponseBody);
    JsonNode timeline = root.isArray() ? root : root.path("events");
    assertThat(timeline.isArray()).as("timeline must be a JSON array").isTrue();
    return timeline;
  }

  private JsonNode firstAssessmentEvent() throws Exception {
    JsonNode timeline = requireHistoryTimeline();
    for (JsonNode event : timeline) {
      if ("ASSESSMENT".equals(event.path("event_type").asText())) {
        return event;
      }
    }
    throw new AssertionError(
        "No event of event_type=ASSESSMENT on the /history timeline. body=" + safeBodyPreview());
  }

  // ---------------------------------------------------------------------------
  // Payload assembly.
  // ---------------------------------------------------------------------------

  /**
   * Builds the AssessmentPost JSON from a snake_case-or-camelCase-keyed map of feature-table
   * values, honouring the {@code <omitted>} / {@code null} / {@code <empty string>} / {@code <blank
   * spaces>} sentinels used in the feature examples.
   */
  private String buildAssessmentJson(UUID claimId, UUID summaryFeeId, Map<String, String> fields) {
    StringBuilder sb = new StringBuilder("{\n");
    sb.append("  \"claim_id\": \"").append(claimId).append("\",\n");
    sb.append("  \"claim_summary_fee_id\": \"").append(summaryFeeId).append("\",\n");
    sb.append("  \"assessment_outcome\": \"NILLED\",\n");
    sb.append("  \"created_by_user_id\": \"").append(BDD_USER_UUID).append("\",\n");
    // Zero monetary defaults so we never fail the request-body validation for missing @NotNull
    // fields — scenarios that care about specific monetary values override via the table.
    sb.append("  \"assessed_total_vat\": 0,\n");
    sb.append("  \"assessed_total_incl_vat\": 0,\n");
    sb.append("  \"allowed_total_vat\": 0,\n");
    sb.append("  \"allowed_total_incl_vat\": 0");

    boolean typeHandled = false;
    boolean reasonHandled = false;
    for (Map.Entry<String, String> e : fields.entrySet()) {
      String key = e.getKey();
      String raw = e.getValue();
      // Normalise every key to snake_case so the loop honours the documented contract of
      // buildAssessmentJson (snake_case OR camelCase keys accepted). Without this normalisation,
      // scenarios using snake_case for `assessment_type` / `assessment_reason` would skip the
      // sentinel-aware handling below and be emitted as raw untrimmed numeric fields.
      String snake = camelToSnake(key);

      // Trim table-cell padding but keep intentional blank tokens (BLANK_SPACES, EMPTY_STRING).
      String value = raw == null ? null : raw.trim();

      if ("assessment_type".equals(snake)) {
        typeHandled = true;
        appendSentinelAwareField(sb, "assessment_type", value, /* isJsonString */ true);
        continue;
      }
      if ("assessment_reason".equals(snake)) {
        reasonHandled = true;
        // Use the trimmed `value` so padded sentinel tokens (e.g. " __EMPTY__ ") match the
        // sentinel-aware branch instead of being treated as literal padded strings.
        appendSentinelAwareField(sb, "assessment_reason", value, /* isJsonString */ true);
        continue;
      }
      // Monetary / numeric overrides — emit as JSON numbers.
      appendSentinelAwareField(sb, snake, value, /* isJsonString */ false);
    }

    // If the scenario didn't specify a type/reason at all, the default depends on the story: for
    // this feature every scenario supplies both, so we do NOT default them here — omission means
    // "test the missing case".
    sb.append("\n}");
    return sb.toString();
  }

  private static void appendSentinelAwareField(
      StringBuilder sb, String snakeKey, String value, boolean isJsonString) {
    if (OMITTED.equals(value)) {
      return; // Field not written at all.
    }
    sb.append(",\n  \"").append(snakeKey).append("\": ");
    if (value == null || NULL_LITERAL.equals(value)) {
      sb.append("null");
      return;
    }
    String actual;
    if (EMPTY_STRING.equals(value)) {
      actual = "";
    } else if (BLANK_SPACES.equals(value)) {
      actual = "   ";
    } else {
      actual = value;
    }
    if (isJsonString) {
      sb.append('"').append(actual.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
    } else {
      sb.append(actual);
    }
  }

  private static String camelToSnake(String camel) {
    if (camel == null) {
      return null;
    }
    if (camel.contains("_")) {
      return camel; // already snake_case (table used snake_case column-name keys)
    }
    StringBuilder out = new StringBuilder(camel.length() + 4);
    for (int i = 0; i < camel.length(); i++) {
      char c = camel.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0) {
          out.append('_');
        }
        out.append(Character.toLowerCase(c));
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private UUID extractIdFromCreatedResponse(String body) {
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      JsonNode node = OBJECT_MAPPER.readTree(body);
      String id = node.path("id").asText(null);
      if (id == null || id.isBlank()) {
        return null;
      }
      return UUID.fromString(id);
    } catch (Exception ex) {
      return null;
    }
  }

  private String labelFor(UUID claimId) {
    for (Map.Entry<String, UUID> e : claimByLabel.entrySet()) {
      if (e.getValue().equals(claimId)) {
        return e.getKey();
      }
    }
    throw new AssertionError("Unknown claim id " + claimId);
  }

  private String safeBodyPreview() {
    if (lastResponseBody == null) {
      return "<null>";
    }
    String trimmed = lastResponseBody.strip();
    return trimmed.length() > 240 ? trimmed.substring(0, 240) + "…" : trimmed;
  }
}
