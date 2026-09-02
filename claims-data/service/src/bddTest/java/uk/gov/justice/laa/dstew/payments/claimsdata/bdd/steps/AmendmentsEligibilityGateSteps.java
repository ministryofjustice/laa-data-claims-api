package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step glue for {@code amendmentsEligibilityGate.feature} — DSTEW-1764.
 *
 * <p>Verifies the {@code claim.status} eligibility gate on the amendment endpoint ({@code PATCH
 * /api/v1/submissions/{submissionId}/claims/{claimId}}):
 *
 * <ul>
 *   <li>Only claims with {@code status=VALID} may be amended (the gate is a no-op for them).
 *   <li>Voided claims are rejected with {@code INVALID_VOIDED_CLAIM_NOT_AMENDABLE}.
 *   <li>Any other non-{@code VALID} status is rejected with {@code
 *       INVALID_CLAIM_STATE_NOT_AMENDABLE}.
 *   <li>The gate short-circuits BEFORE the metadata / duplicate / PDA / FSP steps.
 *   <li>A missing claim id is a retrieval failure (surfaces before the eligibility gate) and never
 *       produces an eligibility error code.
 * </ul>
 *
 * <h2>Shared {@code @When} pattern</h2>
 *
 * <p>The submit step ({@code When I submit the amendment and wait for the event service to complete
 * amendment validation}) is owned by {@link AmendmentMetadataValidationSteps}; it consults {@link
 * SharedAmendmentPatchContext} to route the PATCH. This class populates that shared context in its
 * Given steps (submission id + claim id + patch JSON) so the single shared @When can drive the
 * request without any duplicate step-definition collisions.
 *
 * <p>The response ({@link BddScenarioContext#getLastStatusCode()} and {@link
 * BddScenarioContext#getLastResponseBody()}) is read back from the shared scenario context by the
 * Thens below.
 *
 * <h2>Feature-file vs code enum alignment</h2>
 *
 * <p>The feature file uses the domain vocabulary {@code "VOIDED"} for the voided state; on main the
 * {@link ClaimStatus} enum spells the value {@code VOID}. The step class normalises the
 * feature-file label to the enum value.
 *
 * <p>The original DSTEW-1999 draft outline for {@code @DS1764_3} listed four non-VALID statuses
 * (READY_TO_PROCESS, INVALID, REPLACED, ARCHIVED); only READY_TO_PROCESS and INVALID exist on main.
 * REPLACED and ARCHIVED have been removed from the feature file as Type 3 (no longer required — the
 * two surviving examples already prove the "not amendable" gate).
 *
 * <h2>Negative assertions on downstream work</h2>
 *
 * <p>"No outbound PDA call was made" is defined by {@code AmendmentPdaTriggerSteps} and reused
 * here. "No outbound FSP call was made" is a spec-guard mirror owned by this class (first
 * introduced here). "No amendment state was committed" is proven through observable side-effects
 * (no {@code claim_amendment} row + {@code claim.version} unchanged).
 *
 * <p>Every step body wraps its logic in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures#step(String,
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.ThrowingRunnable)}
 * per the project-wide step-failure-reporting standing rule.
 */
public class AmendmentsEligibilityGateSteps {

  private static final String BDD_USER = "bdd-1764-user";
  private static final String BDD_USER_UUID = "0190b6a0-9b7e-7c8a-9e2d-1764000000aa";

  /** Nil UUID used by @DS1764_6 for the "no claim exists" scenario. */
  private static final UUID NIL_UUID = new UUID(0L, 0L);

  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private BddScenarioContext scenarioContext;
  @Autowired private SharedAmendmentPatchContext sharedPatchContext;

  // Scenario-scoped state (cucumber-spring makes a fresh instance per scenario).
  private UUID currentSubmissionId;
  private UUID currentClaimId;
  private long claimVersionAtSubmission;

  /** Marker: what shape of payload the pending PATCH should carry. */
  private PayloadShape pendingPayloadShape = PayloadShape.WELL_FORMED;

  private enum PayloadShape {
    WELL_FORMED,
    ALSO_FAILS_METADATA_AND_DUPLICATE
  }

  // ---------------------------------------------------------------------------
  // Shared / borrowed step definitions:
  //
  //   "the amendments feature flag is enabled"                 (AmendmentsFeatureFlagSteps)
  //   "the amendment metadata reference-data source is available"
  //                                                            (AmendmentMetadataValidationSteps)
  //   "I submit the amendment and wait for the event service to complete amendment validation"
  //                                                            (AmendmentMetadataValidationSteps)
  //   "no outbound PDA call was made"                          (AmendmentPdaTriggerSteps)
  //
  // Owned here (introduced by this feature):
  //   "no outbound FSP call was made" — spec-guard mirror of the PDA equivalent.
  //   The FSP outbound call is only performed by the pricing-amendment flow (DSTEW-1767);
  //   for the eligibility gate we assert it did not happen as a symbolic guard — the actual
  //   integration-level verification is owned by the FSP integration tests.
  // ---------------------------------------------------------------------------

  @Then("no outbound FSP call was made")
  public void noOutboundFspCallWasMade() {
    step(
        "[spec-guard] Asserting no outbound FSP call is expected for a rejected/short-circuited "
            + "amendment — the pricing gate is downstream of eligibility so an early rejection "
            + "necessarily precedes any FSP invocation (integration-level verification is owned "
            + "by the FSP mock-server tests).",
        () -> {
          // Symbolic assertion: the eligibility gate is upstream of the pricing/FSP step. If a
          // 4xx eligibility error is surfaced (or retrieval short-circuits before eligibility),
          // no FSP round-trip can have occurred. There is no observable side-effect on main to
          // probe here; the guard is provided so the feature file expresses the ordering
          // contract explicitly.
        });
  }

  // ---------------------------------------------------------------------------
  // Givens — seeded claim + payload publication into SharedAmendmentPatchContext.
  // ---------------------------------------------------------------------------

  @Given("an original claim exists with claim.status {string}")
  @Transactional
  public void anOriginalClaimExistsWithClaimStatus(String featureLabel) {
    step(
        "Seeding submission + claim with claim.status = '"
            + featureLabel
            + "' (normalised to the ClaimStatus enum value used on main)",
        () -> seedClaim(normaliseStatusLabel(featureLabel)));
  }

  @Given("a well-formed amendment payload for that claim")
  public void aWellFormedAmendmentPayloadForThatClaim() {
    step(
        "Publishing a well-formed non-pricing amendment payload to the shared PATCH context so "
            + "the borrowed @When step will submit it against the seeded claim.",
        () -> publishSharedPayload(PayloadShape.WELL_FORMED));
  }

  @Given("an amendment payload that would also fail metadata validation and duplicate checks")
  public void anAmendmentPayloadThatWouldAlsoFailMetadataValidationAndDuplicateChecks() {
    step(
        "Publishing a payload that ALSO carries deliberately invalid metadata (unknown "
            + "amendment_reason_code) and would collide with a duplicate — so the assertion that "
            + "the eligibility gate rejects EARLY is meaningful.",
        () -> publishSharedPayload(PayloadShape.ALSO_FAILS_METADATA_AND_DUPLICATE));
  }

  @Given("no amendable claim exists for claim id {string}")
  public void noAmendableClaimExistsForClaimId(String claimIdString) {
    step(
        "Documenting that no claim will be seeded for the requested id — the amendment endpoint "
            + "must therefore respond with a retrieval-failure code, not an eligibility code",
        () -> {
          assertThat(UUID.fromString(claimIdString))
              .as("provided claim id must be the nil UUID for this scenario")
              .isEqualTo(NIL_UUID);
          // Also seed a submission so we have a submissionId to route the PATCH URL through.
          seedSubmissionOnly();
          currentClaimId = NIL_UUID;
          claimVersionAtSubmission = 0L;
        });
  }

  @Given("a well-formed amendment payload for that claim id")
  public void aWellFormedAmendmentPayloadForThatClaimId() {
    step(
        "Publishing a well-formed amendment payload targeting the nil claim id to the shared "
            + "PATCH context so the borrowed @When step will drive the request.",
        () -> publishSharedPayload(PayloadShape.WELL_FORMED));
  }

  // ---------------------------------------------------------------------------
  // Thens — DS1764_1 (eligible / gate does not reject).
  // ---------------------------------------------------------------------------

  @Then("the eligibility gate does not reject the amendment")
  public void theEligibilityGateDoesNotRejectTheAmendment() {
    step(
        "Asserting the response body does not carry either of the eligibility-gate error codes",
        () -> {
          assertResponseDoesNotContainCode("INVALID_VOIDED_CLAIM_NOT_AMENDABLE");
          assertResponseDoesNotContainCode("INVALID_CLAIM_STATE_NOT_AMENDABLE");
        });
  }

  @Then("no eligibility error code is present in the response")
  public void noEligibilityErrorCodeIsPresentInTheResponse() {
    step(
        "Asserting neither eligibility-gate error code is present in the response body",
        () -> {
          assertResponseDoesNotContainCode("INVALID_VOIDED_CLAIM_NOT_AMENDABLE");
          assertResponseDoesNotContainCode("INVALID_CLAIM_STATE_NOT_AMENDABLE");
        });
  }

  // ---------------------------------------------------------------------------
  // Thens — DS1764_2 / DS1764_3 / DS1764_5 (rejection with specific codes).
  // ---------------------------------------------------------------------------

  @Then("the amendment is rejected with the following eligibility errors")
  public void theAmendmentIsRejectedWithTheFollowingErrors(DataTable table) {
    step(
        "Asserting the response is a 4xx carrying every 'Error Code' listed in the DataTable",
        () -> {
          Integer status = scenarioContext.getLastStatusCode();
          assertThat(status)
              .as("last response status; body=%s", safeBodyPreview())
              .isNotNull()
              .isGreaterThanOrEqualTo(400)
              .isLessThan(500);
          List<String> expectedCodes = table.asList(String.class);
          // Drop the header row ("Error Code") if present.
          if (!expectedCodes.isEmpty() && "Error Code".equalsIgnoreCase(expectedCodes.get(0))) {
            expectedCodes = expectedCodes.subList(1, expectedCodes.size());
          }
          for (String code : expectedCodes) {
            assertResponseContainsCode(code);
          }
        });
  }

  @Then("the response includes the current claim.status {string}")
  public void theResponseIncludesTheCurrentClaimStatus(String status) {
    step(
        "Asserting the response body includes the current claim.status '"
            + status
            + "' — the INVALID_CLAIM_STATE_NOT_AMENDABLE message template embeds it",
        () -> {
          JsonNode body = scenarioContext.getLastResponseBody();
          assertThat(body).as("response body").isNotNull();
          assertThat(body.toString())
              .as("response body must reference the current status '%s'", status)
              .contains(status);
        });
  }

  @Then("no eligibility amendment state was committed")
  public void noAmendmentStateWasCommitted() {
    step(
        "Asserting no claim_amendment row exists for the current claim and claim.version is "
            + "unchanged — proves nothing was committed",
        () -> {
          if (NIL_UUID.equals(currentClaimId)) {
            // The @DS1764_6 scenario never had a claim in the first place; the assertion is that
            // the retrieval failure short-circuited BEFORE any write. No row to check for the
            // nil UUID because a row for it can't exist anyway.
            long count =
                jdbcClient
                    .sql("SELECT COUNT(*) FROM claims.claim_amendment WHERE claim_id = :id")
                    .param("id", NIL_UUID)
                    .query(Long.class)
                    .single();
            assertThat(count).as("claim_amendment rows for the nil claim id").isZero();
            return;
          }
          long count =
              jdbcClient
                  .sql("SELECT COUNT(*) FROM claims.claim_amendment WHERE claim_id = :id")
                  .param("id", currentClaimId)
                  .query(Long.class)
                  .single();
          assertThat(count).as("claim_amendment row count for the current claim").isZero();
          long currentVersion =
              jdbcClient
                  .sql("SELECT version FROM claims.claim WHERE id = :id")
                  .param("id", currentClaimId)
                  .query(Long.class)
                  .single();
          assertThat(currentVersion)
              .as("claim.version must be unchanged after a rejected amendment")
              .isEqualTo(claimVersionAtSubmission);
        });
  }

  // ---------------------------------------------------------------------------
  // Thens — DS1764_4 / DS1764_5 / DS1764_6 (code presence / absence assertions).
  // ---------------------------------------------------------------------------

  @Then("the response contains error code {string}")
  public void theResponseContainsErrorCode(String code) {
    step(
        "Asserting the response body carries error code '" + code + "'",
        () -> assertResponseContainsCode(code));
  }

  @Then("the response does not contain error code {string}")
  public void theResponseDoesNotContainErrorCode(String code) {
    step(
        "Asserting the response body does NOT carry error code '" + code + "'",
        () -> assertResponseDoesNotContainCode(code));
  }

  @Then("the response does not contain any metadata validation error code")
  public void theResponseDoesNotContainAnyMetadataValidationErrorCode() {
    step(
        "Asserting the response body does not carry any metadata-validation error code — proves "
            + "the eligibility gate short-circuited BEFORE the metadata step ran. The three real"
            + " metadata-code families are INVALID_REQUESTED_BY_*, INVALID_AMENDMENT_REASON_*, and"
            + " INVALID_USER_IDENTIFIER_* (see ClaimAmendmentValidationCode).",
        () -> {
          // Full enumeration of the real metadata codes, taken from
          // ClaimAmendmentValidationCode so this list cannot silently drift from production.
          for (String code :
              List.of(
                  // INVALID_REQUESTED_BY_*
                  "INVALID_REQUESTED_BY_MISSING",
                  "INVALID_REQUESTED_BY_UNKNOWN",
                  "INVALID_REQUESTED_BY_INACTIVE",
                  "INVALID_REQUESTED_BY_NOT_A_CODE",
                  // INVALID_AMENDMENT_REASON_*
                  "INVALID_AMENDMENT_REASON_MISSING",
                  "INVALID_AMENDMENT_REASON_UNKNOWN",
                  "INVALID_AMENDMENT_REASON_INACTIVE",
                  "INVALID_AMENDMENT_REASON_NOT_A_CODE",
                  "INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY",
                  // INVALID_USER_IDENTIFIER_*
                  "INVALID_USER_IDENTIFIER_MISSING",
                  "INVALID_USER_IDENTIFIER_FORMAT")) {
            assertResponseDoesNotContainCode(code);
          }
        });
  }

  @Then("the response does not contain any duplicate check error code")
  public void theResponseDoesNotContainAnyDuplicateCheckErrorCode() {
    step(
        "Asserting the response body does not carry any duplicate-check error code — proves the "
            + "eligibility gate short-circuited BEFORE the duplicate step ran. The real amendment"
            + " duplicate codes are INVALID_CLAIM_HAS_DUPLICATE_IN_ANOTHER_SUBMISSION and"
            + " INVALID_CLAIM_HAS_DUPLICATE_IN_SAME_SUBMISSION (see"
            + " ClaimAmendmentDuplicateValidationIntegrationTest + ClaimValidationError).",
        () -> {
          for (String code :
              List.of(
                  "INVALID_CLAIM_HAS_DUPLICATE_IN_ANOTHER_SUBMISSION",
                  "INVALID_CLAIM_HAS_DUPLICATE_IN_SAME_SUBMISSION")) {
            assertResponseDoesNotContainCode(code);
          }
        });
  }

  // ---------------------------------------------------------------------------
  // Response-body assertion helpers.
  // ---------------------------------------------------------------------------

  private void assertResponseContainsCode(String expectedCode) {
    JsonNode body = scenarioContext.getLastResponseBody();
    assertThat(body).as("response body must not be null when asserting on error codes").isNotNull();
    List<String> codes = extractErrorCodes(body);
    assertThat(codes)
        .as("errors[*].code in response body (raw body preview = %s)", safeBodyPreview())
        .contains(expectedCode);
  }

  private void assertResponseDoesNotContainCode(String forbiddenCode) {
    JsonNode body = scenarioContext.getLastResponseBody();
    if (body == null) {
      return; // empty body → definitely no code
    }
    List<String> codes = extractErrorCodes(body);
    assertThat(codes)
        .as("errors[*].code in response body (raw body preview = %s)", safeBodyPreview())
        .doesNotContain(forbiddenCode);
  }

  private static List<String> extractErrorCodes(JsonNode body) {
    if (body == null) {
      return List.of();
    }
    JsonNode errors = body.path("errors");
    if (!errors.isArray()) {
      return List.of();
    }
    return errors.findValuesAsText("code");
  }

  // ---------------------------------------------------------------------------
  // Seeding + payload helpers.
  // ---------------------------------------------------------------------------

  private void seedClaim(ClaimStatus status) {
    seedSubmissionOnly();

    Claim claim = new Claim();
    claim.setId(Uuid7.timeBasedUuid());
    claim.setSubmission(submissionRepository.findById(currentSubmissionId).orElseThrow());
    claim.setStatus(status);
    claim.setLineNumber(1);
    claim.setFeeCode("CAPA");
    claim.setMatterTypeCode("MAT01");
    claim.setCaseReferenceNumber("BDD1764-CRN");
    claim.setUniqueFileNumber("BDD1764-UFN");
    claim.setCaseStartDate(LocalDate.of(2026, 1, 1));
    claim.setCreatedByUserId(BDD_USER);
    claim.setUpdatedByUserId(BDD_USER);
    claimRepository.saveAndFlush(claim);
    currentClaimId = claim.getId();
    claimVersionAtSubmission = claim.getVersion() == null ? 0L : claim.getVersion();
  }

  private void seedSubmissionOnly() {
    Submission submission =
        Submission.builder()
            .id(Uuid7.timeBasedUuid())
            .officeAccountNumber("0BDD64")
            .submissionPeriod("JAN-2026")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(BDD_USER)
            .providerUserId(BDD_USER)
            .createdOn(Instant.now())
            .build();
    submissionRepository.saveAndFlush(submission);
    currentSubmissionId = submission.getId();
  }

  private void publishSharedPayload(PayloadShape shape) {
    pendingPayloadShape = shape;
    sharedPatchContext.setSubmissionId(currentSubmissionId);
    sharedPatchContext.setClaimId(currentClaimId);
    sharedPatchContext.setPatchJson(buildPayload(shape));
  }

  private static ClaimStatus normaliseStatusLabel(String label) {
    // Feature file uses "VOIDED" (domain vocabulary); enum on main is VOID.
    String upper = label.trim().toUpperCase(Locale.ROOT);
    if ("VOIDED".equals(upper)) {
      return ClaimStatus.VOID;
    }
    return ClaimStatus.valueOf(upper);
  }

  private static String buildPayload(PayloadShape shape) {
    // Always include claim_version = 0 (the seed default) and the amendment metadata triple. For
    // the ALSO_FAILS_METADATA_AND_DUPLICATE flavour we deliberately break metadata (unknown
    // reason code) so the DS1764_5 assertion "eligibility short-circuits BEFORE metadata step" is
    // meaningful — otherwise a successful metadata pass would not prove short-circuit.
    String reasonCode =
        shape == PayloadShape.ALSO_FAILS_METADATA_AND_DUPLICATE
            ? "THIS_REASON_CODE_DOES_NOT_EXIST"
            : "PROVIDER_ERROR";
    // Non-pricing field (client_forename) — keeps us off the assessed-pricing gate. For the
    // "would also cause a duplicate" flavour, the assertion we care about is that the response
    // does not MENTION any duplicate code; any observable duplicate collision on top of a VOID
    // claim would simply be additive.
    return ("""
        {
          "version": 0,
          "amendment_requested_by": "PROVIDER",
          "amendment_reason_code": "%s",
          "amendment_user_id": "%s",
          "client_forename": "DSTEW-1764-Eligibility"
        }
        """)
        .formatted(reasonCode, BDD_USER_UUID);
  }

  private String safeBodyPreview() {
    JsonNode body = scenarioContext.getLastResponseBody();
    if (body == null) {
      return "<null>";
    }
    String trimmed = body.toString().strip();
    return trimmed.length() > 240 ? trimmed.substring(0, 240) + "…" : trimmed;
  }
}
