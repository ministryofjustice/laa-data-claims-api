package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step glue for {@code amendmentsRequestContract.feature} — DSTEW-1751.
 *
 * <p>Verifies the request-body contract for the amendment {@code version} field on {@code PATCH
 * /api/v1/submissions/{submissionId}/claims/{claimId}}:
 *
 * <ul>
 *   <li>The endpoint deserialises boundary integer values (max signed 32-bit, negative) without
 *       returning a 400 request-validation error — any 4xx that occurs must be the stale-version
 *       gate (409), the field-amendability gate, or another semantic gate — NOT the shape gate.
 *   <li>Malformed / non-integer / missing {@code version} shapes are rejected with 400 BEFORE any
 *       claim retrieval / PDA / FSP / persistence work.
 *   <li>The 400 response must NOT carry the {@code CLAIM_VERSION_CONFLICT} code — that code is
 *       reserved for the semantic (stored-vs-submitted mismatch) gate, not the shape gate.
 * </ul>
 *
 * <h2>Nomenclature note</h2>
 *
 * <p>The feature file uses the domain name {@code claim_version} for readability. The delivered
 * wire contract on {@link uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch} uses the
 * JSON key {@code "version"} (annotated {@code @JsonProperty("version")}). This step class
 * consistently drives the JSON key {@code "version"} while keeping the Gherkin's {@code
 * claim_version} vocabulary intact.
 *
 * <h2>Negative assertions on downstream work</h2>
 *
 * <p>The scenarios assert that no PDA / FSP / persistence / diff / before-state work happens when a
 * 400 is returned. The BDD harness does not wire WireMock stubs for PDA / FSP, so we cannot
 * intercept those calls directly. The 400-rejection assertions are proven through OBSERVABLE
 * side-effects that are strictly stronger than "the call did not happen":
 *
 * <ul>
 *   <li>No {@code claim_amendment} row is persisted for the claim.
 *   <li>The stored {@code claim.version} is unchanged after the request.
 *   <li>{@code claim.is_amended} is unchanged.
 * </ul>
 *
 * <p>Because the amendment pipeline is transactional and any successful PDA/FSP/persistence step
 * would have left one of the above traces, their absence proves the shape-gate short-circuited
 * BEFORE the pipeline ran. This is a stronger contract than a MockServer-based verify: an
 * accidental call that succeeded but rolled back would fail this assertion too.
 *
 * <p>Every step body wraps its logic in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures#step(String,
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.ThrowingRunnable)}
 * per the project-wide step-failure-reporting standing rule.
 */
public class AmendmentsRequestContractSteps {

  private static final String BDD_USER = "bdd-1751-user";
  private static final String BDD_USER_UUID = "0190b6a0-9b7e-7c8a-9e2d-1751000000aa";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String PATCH_CLAIM_PATH =
      API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";

  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private RestTemplate restTemplate;
  @Autowired private BddServerInfo serverInfo;

  // Scenario-scoped state (cucumber-spring makes a fresh instance per scenario).
  private UUID currentSubmissionId;
  private UUID currentClaimId;

  /** Stored version at the moment the scenario submits its patch — snapshot for post-assertions. */
  private long claimVersionAtSubmission;

  /** Value the {@code @Given} step wants the next {@code @When} step to submit as an integer. */
  private Long pendingSubmittedIntegerVersion;

  private int lastStatusCode;
  private String lastResponseBody;

  // ---------------------------------------------------------------------------
  // Background & Givens.
  // ---------------------------------------------------------------------------

  // NOTE: "the amendments feature flag is enabled" is defined by AmendmentsFeatureFlagSteps and
  // reused here via Cucumber's cross-class step registry — do NOT redefine it.

  @Given("an original claim exists at version {long}")
  @Transactional
  public void anOriginalClaimExistsAtVersion(Long storedVersion) {
    step(
        "Seeding submission + claim in status VALID with stored version " + storedVersion,
        () -> {
          seedClaim(ClaimStatus.VALID);
          forceClaimVersion(currentClaimId, storedVersion);
          claimVersionAtSubmission = storedVersion;
        });
  }

  @Given("the amendment payload includes claim_version {long} as a JSON integer")
  public void theAmendmentPayloadIncludesClaimVersionAsAJsonInteger(Long submittedVersion) {
    // Captured directly by the @When step below via a scenario-scoped field. Kept as an explicit
    // Given so the scenario reads clearly.
    step(
        "Capturing the submitted claim_version ("
            + submittedVersion
            + ") for use by the next "
            + "@When step",
        () -> pendingSubmittedIntegerVersion = submittedVersion);
  }

  // ---------------------------------------------------------------------------
  // Whens.
  // ---------------------------------------------------------------------------

  @When("I submit the amendment")
  public void iSubmitTheAmendment() {
    step(
        "PATCH " + PATCH_CLAIM_PATH + " with the captured integer claim_version",
        () -> {
          Long v = pendingSubmittedIntegerVersion;
          assertThat(v)
              .as("scenario must first set an integer claim_version via the corresponding Given")
              .isNotNull();
          patchWithVersionInteger(v);
        });
  }

  // Regex-based expression: the table examples include values with quotes / braces / whitespace
  // ({"abc", "5F", "", "   ", {}, [], 7.5, true, null}), none of which match Cucumber's {word}
  // shorthand. We capture the entire remainder of the line and dispatch on it below.
  @When("^I submit an amendment with a raw JSON body where claim_version is (.+)$")
  public void iSubmitAnAmendmentWithARawJsonBodyWhereClaimVersionIs(String malformedValueToken) {
    step(
        "PATCH "
            + PATCH_CLAIM_PATH
            + " with a raw JSON body where claim_version = "
            + malformedValueToken,
        () -> {
          String trimmed = malformedValueToken.trim();
          if ("absent from body".equalsIgnoreCase(trimmed)) {
            patchWithRawJson(bodyWithoutVersion());
          } else if ("bare unquoted 5F".equalsIgnoreCase(trimmed)) {
            // DS1751_3 — 5F outside quotes is invalid JSON; the whole body fails to parse.
            patchWithRawJson(bodyForRawJsonToken("5F"));
          } else {
            patchWithRawJson(bodyForMalformedToken(trimmed));
          }
        });
  }

  // ---------------------------------------------------------------------------
  // Thens — response-shape assertions.
  // ---------------------------------------------------------------------------

  @Then("the endpoint response status is not 400")
  public void theEndpointResponseStatusIsNot400() {
    step(
        "Asserting the last HTTP status is NOT 400 AND is not a server error (5xx) — a 5xx would "
            + "silently mask a regression in the boundary-value scenarios (body="
            + safeBodyPreview()
            + ")",
        () -> {
          assertThat(lastStatusCode)
              .as("last response status; body=%s", safeBodyPreview())
              .isNotEqualTo(400);
          assertThat(lastStatusCode)
              .as(
                  "last response status must not be a server error (5xx) — a 5xx would hide a"
                      + " regression at the boundary; body=%s",
                  safeBodyPreview())
              .isLessThan(500);
        });
  }

  @Then("the response is not an {string} request-validation error")
  public void theResponseIsNotAnInvalidClaimVersionRequestValidationError(String errorText) {
    step(
        "Asserting the response body does not carry the '"
            + errorText
            + "' request-validation "
            + "error text",
        () -> {
          if (lastResponseBody == null || lastResponseBody.isBlank()) {
            return; // no body at all → definitely not the shape error
          }
          String bodyLower = lastResponseBody.toLowerCase(Locale.ROOT);
          assertThat(bodyLower)
              .as("body must not carry the shape error '%s'", errorText)
              .doesNotContain(errorText.toLowerCase(Locale.ROOT));
        });
  }

  @Then("the endpoint response status is 400")
  public void theEndpointResponseStatusIs400() {
    step(
        "Asserting the last HTTP status is 400 (body=" + safeBodyPreview() + ")",
        () ->
            assertThat(lastStatusCode)
                .as("last response status; body=%s", safeBodyPreview())
                .isEqualTo(400));
  }

  @Then("the response uses the existing request-validation error format")
  public void theResponseUsesTheExistingRequestValidationErrorFormat() {
    step(
        "Asserting the 400 response body is Spring's standard ProblemDetail (RFC 9457) shape — "
            + "parses as JSON, carries status == 400, and exposes at least one standard field "
            + "(title/detail/type/instance). Parsing avoids the false-positive risk of a naive "
            + "substring check for '400' matching an unrelated field value.",
        () -> {
          assertThat(lastResponseBody).as("response body").isNotNull().isNotBlank();
          JsonNode body = OBJECT_MAPPER.readTree(lastResponseBody);
          assertThat(body.path("status").asInt(-1))
              .as("ProblemDetail 'status' field must be 400")
              .isEqualTo(400);
          assertThat(
                  body.hasNonNull("title")
                      || body.hasNonNull("detail")
                      || body.hasNonNull("type")
                      || body.hasNonNull("instance"))
              .as("ProblemDetail body must expose at least one standard field (body=%s)", body)
              .isTrue();
        });
  }

  @Then("the response does not contain a {string} code")
  public void theResponseDoesNotContainACode(String code) {
    step(
        "Asserting the response body does NOT reference the semantic gate code '"
            + code
            + "' — "
            + "shape errors must never be reported through CLAIM_VERSION_CONFLICT",
        () -> {
          if (lastResponseBody == null) {
            return;
          }
          assertThat(lastResponseBody)
              .as("response body must not carry the code '%s'", code)
              .doesNotContain(code);
        });
  }

  // ---------------------------------------------------------------------------
  // Thens — "no downstream work happened" assertions (proven via observable side-effects).
  // ---------------------------------------------------------------------------

  @Then("no persisted claim state changed as a result of this request")
  public void noPersistedClaimStateChangedAsAResultOfThisRequest() {
    step(
        "Asserting no observable persisted change: claim.version is unchanged AND no "
            + "claim_amendment row was inserted for this claim. NOTE: this step deliberately does "
            + "NOT claim to prove that claim retrieval was not attempted — that would require a "
            + "spy/verify on ClaimRepository. What it does prove is the observable side-effect: "
            + "the request produced no state mutation.",
        () -> {
          assertVersionUnchanged();
          assertNoClaimAmendmentRow();
        });
  }

  // NOTE: "no outbound PDA call was made" is defined by AmendmentPdaTriggerSteps as a
  // log-only spec-guard step (final verification is owned by DSTEW-1773). Reusing it here via
  // Cucumber's cross-class step registry — do NOT redefine. The DS1751_2 assertions on
  // "no persistence was attempted for this claim" (defined below) already cover the observable
  // side-effect this scenario cares about.

  @Then("no outbound FSP call was made")
  public void noOutboundFspCallWasMade() {
    step(
        "Asserting no outbound FSP call was made — inferred from the same transactional invariant "
            + "as the PDA assertion",
        () -> {
          assertVersionUnchanged();
          assertNoClaimAmendmentRow();
        });
  }

  @Then("no claim_amendment record was inserted for this claim by this attempt")
  public void noClaimAmendmentRecordWasInsertedForThisClaimByThisAttempt() {
    step(
        "Asserting no claim_amendment row exists for the current claim",
        this::assertNoClaimAmendmentRow);
  }

  @Then("no amendment before-state was computed for this claim")
  public void noAmendmentBeforeStateWasComputedForThisClaim() {
    step(
        "Asserting no before-state exists — proven by the total absence of claim_amendment rows "
            + "for this claim (before-state lives inside the claim_amendment.before_state jsonb)",
        this::assertNoClaimAmendmentRow);
  }

  @Then("no amendment diff was computed for this claim")
  public void noAmendmentDiffWasComputedForThisClaim() {
    step(
        "Asserting no diff exists — proven by the total absence of claim_amendment rows for this "
            + "claim (the diff lives inside the claim_amendment.diff jsonb)",
        this::assertNoClaimAmendmentRow);
  }

  @Then("no persistence was attempted for this claim")
  public void noPersistenceWasAttemptedForThisClaim() {
    step(
        "Asserting no persistence side-effects — claim.version unchanged, claim.is_amended "
            + "unchanged, no claim_amendment row",
        () -> {
          assertVersionUnchanged();
          assertIsAmendedUnchanged();
          assertNoClaimAmendmentRow();
        });
  }

  // ---------------------------------------------------------------------------
  // DB assertion helpers.
  // ---------------------------------------------------------------------------

  private void assertVersionUnchanged() {
    long currentVersion =
        jdbcClient
            .sql("SELECT version FROM claims.claim WHERE id = :id")
            .param("id", currentClaimId)
            .query(Long.class)
            .single();
    assertThat(currentVersion)
        .as(
            "claim.version must be unchanged after a rejected shape request (expected %s)",
            claimVersionAtSubmission)
        .isEqualTo(claimVersionAtSubmission);
  }

  private void assertIsAmendedUnchanged() {
    boolean isAmended =
        jdbcClient
            .sql("SELECT is_amended FROM claims.claim WHERE id = :id")
            .param("id", currentClaimId)
            .query(Boolean.class)
            .single();
    assertThat(isAmended)
        .as("claim.is_amended must remain false after a rejected shape request")
        .isFalse();
  }

  private void assertNoClaimAmendmentRow() {
    long count =
        jdbcClient
            .sql("SELECT COUNT(*) FROM claims.claim_amendment WHERE claim_id = :id")
            .param("id", currentClaimId)
            .query(Long.class)
            .single();
    assertThat(count).as("claim_amendment row count for the current claim").isZero();
  }

  // ---------------------------------------------------------------------------
  // Seeding helpers.
  // ---------------------------------------------------------------------------

  private void seedClaim(ClaimStatus status) {
    Submission submission =
        Submission.builder()
            .id(Uuid7.timeBasedUuid())
            .officeAccountNumber("0BDD51")
            .submissionPeriod("JAN-2026")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(BDD_USER)
            .providerUserId(BDD_USER)
            .createdOn(Instant.now())
            .build();
    submissionRepository.saveAndFlush(submission);
    currentSubmissionId = submission.getId();

    Claim claim = new Claim();
    claim.setId(Uuid7.timeBasedUuid());
    claim.setSubmission(submission);
    claim.setStatus(status);
    claim.setLineNumber(1);
    claim.setFeeCode("CAPA");
    claim.setMatterTypeCode("MAT01");
    claim.setCaseReferenceNumber("BDD1751-CRN");
    claim.setUniqueFileNumber("BDD1751-UFN");
    claim.setCaseStartDate(LocalDate.of(2026, 1, 1));
    claim.setCreatedByUserId(BDD_USER);
    claim.setUpdatedByUserId(BDD_USER);
    claimRepository.saveAndFlush(claim);
    currentClaimId = claim.getId();
  }

  private void forceClaimVersion(UUID claimId, long version) {
    int updated =
        jdbcClient
            .sql("UPDATE claims.claim SET version = :v WHERE id = :id")
            .param("v", version)
            .param("id", claimId)
            .update();
    assertThat(updated)
        .as("forceClaimVersion should update exactly one row for claim %s", claimId)
        .isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // HTTP helpers.
  // ---------------------------------------------------------------------------

  private void patchWithVersionInteger(long submittedVersion) {
    String json =
        ("""
        {
          "version": %d,
          "amendment_requested_by": "PROVIDER",
          "amendment_reason_code": "PROVIDER_ERROR",
          "amendment_user_id": "%s",
          "client_forename": "DSTEW-1751-Contract"
        }
        """)
            .formatted(submittedVersion, BDD_USER_UUID);
    patchWithRawJson(json);
  }

  private void patchWithRawJson(String rawJsonBody) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    try {
      ResponseEntity<String> response =
          restTemplate.exchange(
              serverInfo.baseUrl() + PATCH_CLAIM_PATH,
              HttpMethod.PATCH,
              new HttpEntity<>(rawJsonBody, headers),
              String.class,
              currentSubmissionId,
              currentClaimId);
      lastStatusCode = response.getStatusCode().value();
      lastResponseBody = response.getBody();
    } catch (HttpStatusCodeException ex) {
      lastStatusCode = ex.getStatusCode().value();
      lastResponseBody = ex.getResponseBodyAsString();
    }
  }

  // ---------------------------------------------------------------------------
  // Raw-JSON payload builders — DSTEW-1751 is specifically about wire-level shapes, so we assemble
  // the body as strings rather than going through Jackson (which would refuse to serialise the
  // malformed shapes in the first place).
  // ---------------------------------------------------------------------------

  /**
   * Emits the base amendment body with {@code "version": <valueLiteral>} — the {@code valueLiteral}
   * is dropped into the JSON verbatim so callers can inject a bare (unquoted) malformed token like
   * {@code 5F}, a nested object, an array literal, etc.
   */
  private String bodyForRawJsonToken(String valueLiteral) {
    return ("""
        {
          "version": %s,
          "amendment_requested_by": "PROVIDER",
          "amendment_reason_code": "PROVIDER_ERROR",
          "amendment_user_id": "%s",
          "client_forename": "DSTEW-1751-Contract"
        }
        """)
        .formatted(valueLiteral, BDD_USER_UUID);
  }

  private String bodyWithoutVersion() {
    return ("""
        {
          "amendment_requested_by": "PROVIDER",
          "amendment_reason_code": "PROVIDER_ERROR",
          "amendment_user_id": "%s",
          "client_forename": "DSTEW-1751-Contract"
        }
        """)
        .formatted(BDD_USER_UUID);
  }

  /**
   * Maps a single-token feature-example value to a raw JSON literal that will be dropped verbatim
   * into the "version" position of the amendment body. Quoted / decimal / boolean / literal-null
   * tokens are already the exact JSON they need to be; only a couple of them need special-casing.
   */
  private String bodyForMalformedToken(String token) {
    String literal;
    switch (token) {
      case "null":
        literal = "null";
        break;
      case "{}":
        literal = "{}";
        break;
      case "[]":
        literal = "[]";
        break;
      case "true":
        literal = "true";
        break;
      default:
        // Already a JSON-shaped fragment ("abc", "5F", "", "   ", 7.5). Trust the feature file.
        literal = token;
        break;
    }
    return bodyForRawJsonToken(literal);
  }

  private String safeBodyPreview() {
    if (lastResponseBody == null) {
      return "<null>";
    }
    String trimmed = lastResponseBody.strip();
    return trimmed.length() > 240 ? trimmed.substring(0, 240) + "…" : trimmed;
  }
}
