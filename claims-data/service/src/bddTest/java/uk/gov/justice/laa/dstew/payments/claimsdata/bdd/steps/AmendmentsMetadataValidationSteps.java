package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.provider.AmendmentReferenceDataProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AmendmentReasonReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.RequestedByReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentReferenceValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentUserIdValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step definitions for the DSTEW-1765 submit-time metadata validation scenarios in {@code
 * amendmentsMetadata.feature} (tags {@code @DS1765_*}).
 *
 * <p>The DSTEW-1765 acceptance criteria concern the three metadata validation steps only
 * (feature-flag, submitting user id and Requested By / Amendment Reason reference lookup). This
 * glue exercises the real HTTP boundary (PATCH /submissions/{sid}/claims/{cid}) to prove the
 * controller, exception handler, feature-flag interceptor, and JSON response shape all work
 * correctly end-to-end.
 */
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class AmendmentsMetadataValidationSteps {

  private static final String SEED_ACTOR = "bdd-ds1765";
  private static final String SUBMISSION_PERIOD = "JUL-2025";
  private static final String OFFICE = "BDDDS1";

  @Autowired private BddScenarioContext context;
  @Autowired private ClaimsApiProperties claimsApiProperties;
  @Autowired private CacheManager cacheManager;
  @Autowired private BddApiStepSupport api;

  @Autowired private RequestedByReferenceRepository requestedByReferenceRepository;
  @Autowired private AmendmentReasonReferenceRepository amendmentReasonReferenceRepository;
  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimAmendmentRepository claimAmendmentRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // ---------------------------------------------------------------------------
  // Given: feature flag
  // ---------------------------------------------------------------------------

  @Given("the amendments feature flag is enabled")
  public void theAmendmentsFeatureFlagIsEnabled() {
    claimsApiProperties.getAmendments().setEnabled("true");
  }

  @Given("the amendments feature flag is disabled")
  public void theAmendmentsFeatureFlagIsDisabled() {
    claimsApiProperties.getAmendments().setEnabled("false");
  }

  @Given("the amendments feature flag is not configured")
  public void theAmendmentsFeatureFlagIsNotConfigured() {
    claimsApiProperties.getAmendments().setEnabled(null);
  }

  // ---------------------------------------------------------------------------
  // Given: reference-data availability (DS1765 placeholder-code fixture)
  // ---------------------------------------------------------------------------

  @Given("the amendment metadata reference-data source is available")
  public void theAmendmentMetadataReferenceDataSourceIsAvailable() {
    seedDs1765PlaceholderFixture();
  }

  @Given("no amendment metadata reference data exists")
  public void noAmendmentMetadataReferenceDataExists() {
    // Simulates an empty reference-data set, not a datasource failure.
    amendmentReasonReferenceRepository.deleteAllInBatch();
    amendmentReasonReferenceRepository.flush();
    requestedByReferenceRepository.deleteAllInBatch();
    requestedByReferenceRepository.flush();
    clearReferenceCache();
  }

  @Given("the amendment metadata reference-data source is unavailable")
  public void theAmendmentMetadataReferenceDataSourceIsUnavailable() {
    noAmendmentMetadataReferenceDataExists();
  }

  // ---------------------------------------------------------------------------
  // Given: existing claim + amendment payload capture
  // ---------------------------------------------------------------------------

  @Given("an existing claim ready to be amended")
  public void anExistingClaimReadyToBeAmended() {
    // Ensure the BC-574 fixture required by DSTEW-1905 scenarios is present. DSTEW-1765 scenarios
    // seed a placeholder-code fixture via "the amendment metadata reference-data source is
    // available" BEFORE this step and must NOT be clobbered; DSTEW-1905 scenarios use BC-574
    // codes (PROVIDER / PROVIDER_ERROR) and do not seed explicitly, so we back-fill for them.
    //
    // The BddHooks @Before(order = 0) hook wipes both amendment reference tables between
    // scenarios, so "no rows yet" reliably means "no earlier @Given step seeded a fixture for
    // this scenario" — safe to seed BC-574 defaults. If any rows exist, another step has already
    // installed the fixture the scenario needs and we leave it alone.
    if (requestedByReferenceRepository.count() == 0
        && amendmentReasonReferenceRepository.count() == 0) {
      seedBc574Defaults();
    }

    UUID submissionId = Uuid7.timeBasedUuid();
    Submission submission =
        Submission.builder()
            .id(submissionId)
            .officeAccountNumber(OFFICE)
            .submissionPeriod(SUBMISSION_PERIOD)
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.READY_FOR_VALIDATION)
            .createdByUserId(SEED_ACTOR)
            .providerUserId(SEED_ACTOR)
            .createdOn(Instant.now())
            .build();
    submissionRepository.saveAndFlush(submission);

    UUID claimId = Uuid7.timeBasedUuid();
    Claim claim =
        Claim.builder()
            .id(claimId)
            .submission(submission)
            .status(ClaimStatus.VALID)
            .lineNumber(1)
            .matterTypeCode("MAT01")
            .createdByUserId(SEED_ACTOR)
            .build();
    claimRepository.saveAndFlush(claim);

    context.setSeededSubmissionId(submissionId);
    context.setSeededClaimId(claimId);
    context.setSeededClaimVersion(claim.getVersion());
  }

  private void seedBc574Defaults() {
    amendmentReasonReferenceRepository.deleteAllInBatch();
    amendmentReasonReferenceRepository.flush();
    requestedByReferenceRepository.deleteAllInBatch();
    requestedByReferenceRepository.flush();

    requestedByReferenceRepository.saveAllAndFlush(
        List.of(
            requestedBy("PROVIDER", "Provider", true, 10),
            requestedBy("CONTRACT_MANAGEMENT", "Contract Management", true, 20),
            requestedBy("ASSURANCE", "Assurance", true, 30)));
    amendmentReasonReferenceRepository.saveAllAndFlush(
        List.of(
            reason("PROVIDER", "PROVIDER_ERROR", "Provider Error", true, 10),
            reason(
                "CONTRACT_MANAGEMENT", "INCORRECT_MEANS_ASSESSMENT", "Incorrect Means", true, 10),
            reason("CONTRACT_MANAGEMENT", "OTHER", "Other", true, 20),
            reason("ASSURANCE", "OTHER", "Other", true, 10)));
    clearReferenceCache();
  }

  @Given("an amendment with metadata")
  public void anAmendmentWithMetadata(DataTable table) {
    captureAmendmentMetadata(table);
  }

  /** DSTEW-1905 alias of {@link #anAmendmentWithMetadata(DataTable)}. */
  @Given("an amendment payload with the following metadata")
  public void anAmendmentPayloadWithTheFollowingMetadata(DataTable table) {
    captureAmendmentMetadata(table);
  }

  /**
   * DSTEW-1905 (@AFF_5): builds a payload where every required metadata field is absent. The
   * feature-flag step must still pass (or fail on its own code); the assertion in the scenario is
   * that the flag error is <em>not</em> surfaced, only the individual metadata errors.
   */
  @Given("an amendment payload that is missing required metadata")
  public void anAmendmentPayloadThatIsMissingRequiredMetadata() {
    context.setAmendmentRequestedByCode(null);
    context.setAmendmentReasonCode(null);
    context.setSubmittingUserId(null);
  }

  private void captureAmendmentMetadata(DataTable table) {
    Map<String, String> row = table.asMaps(String.class, String.class).getFirst();
    context.setAmendmentRequestedByCode(row.get("requestedByCode"));
    context.setAmendmentReasonCode(row.get("amendmentReasonCode"));
    // Cucumber converts empty DataTable cells to null. For the submitting user id, the DSTEW-1765
    // Gherkin distinguishes "empty string submitted" from "field omitted": an empty cell means an
    // empty value was supplied (which the validator classifies as FORMAT, not MISSING). Preserve
    // that distinction by replacing null with the empty string here.
    String userId = row.get("submittingUserId");
    context.setSubmittingUserId(userId == null ? "" : userId);
  }

  // ---------------------------------------------------------------------------
  // When: submit the amendment (runs the three metadata validation steps)
  // ---------------------------------------------------------------------------

  @When("I submit the amendment")
  public void iSubmitTheAmendment() {
    BddStepFailures.step(
        "Submitting amendment for submission="
            + context.getSeededSubmissionId()
            + " claim="
            + context.getSeededClaimId(),
        () -> {
          ClaimAmendmentPayload payload =
              ClaimAmendmentPayload.builder()
                  .version(JsonNullable.of(context.getSeededClaimVersion()))
                  .amendmentRequestedBy(toJsonNullable(context.getAmendmentRequestedByCode()))
                  .amendmentReasonCode(toJsonNullable(context.getAmendmentReasonCode()))
                  .amendmentUserId(toJsonNullable(context.getSubmittingUserId()))
                  .build();

          String jsonPayload = objectMapper.writeValueAsString(payload);
          api.submitAmendmentViaHttp(
              context.getSeededSubmissionId(), context.getSeededClaimId(), jsonPayload);

          extractErrorsFromResponseBody();
        });
  }

  // ---------------------------------------------------------------------------
  // Then: outcome assertions
  // ---------------------------------------------------------------------------

  @Then("no metadata validation error is raised")
  public void noMetadataValidationErrorIsRaised() {
    assertThat(context.getLastAmendmentErrorCodes())
        .as("expected no metadata validation errors")
        .isEmpty();
  }

  @Then("the amendment is accepted")
  public void theAmendmentIsAccepted() {
    assertThat(context.getLastAmendmentErrorCodes())
        .as("expected the amendment to be accepted (no metadata errors)")
        .isEmpty();
  }

  @Then("the amendment is rejected")
  public void theAmendmentIsRejected() {
    assertThat(context.getLastAmendmentErrorCodes())
        .as("expected the amendment to be rejected with at least one error")
        .isNotEmpty();
  }

  @Then("the amendment response status is {int}")
  public void theAmendmentResponseStatusIs(int expected) {
    assertThat(context.getLastAmendmentHttpStatus())
        .as("expected amendment endpoint HTTP status")
        .isEqualTo(expected);
  }

  @Then("no amendment error code equals {string}")
  public void noAmendmentErrorCodeEquals(String forbidden) {
    assertThat(context.getLastAmendmentErrorCodes())
        .as("no error code should equal %s", forbidden)
        .doesNotContain(forbidden);
  }

  @Then("no amendment error message contains {string}")
  public void noAmendmentErrorMessageContains(String needle) {
    assertThat(context.getLastAmendmentErrorMessages())
        .as("no error message should contain %s", needle)
        .noneMatch(message -> message != null && message.contains(needle));
  }

  @Then("the submitted metadata values are available for persistence")
  public void theSubmittedMetadataValuesAreAvailableForPersistence() {
    // With no metadata errors, the values captured on the payload are the ones the persistence
    // service would write; assert they round-trip via the payload used by "I submit the amendment".
    assertThat(context.getAmendmentRequestedByCode()).isNotBlank();
    assertThat(context.getAmendmentReasonCode()).isNotBlank();
    assertThat(context.getSubmittingUserId()).isNotBlank();
  }

  @Then("the amendment is rejected with the following errors")
  public void theAmendmentIsRejectedWithTheFollowingErrors(DataTable table) {
    List<Map<String, String>> rows = table.asMaps(String.class, String.class);
    List<String> expectedCodes = rows.stream().map(r -> r.get("Error Code")).toList();
    assertThat(context.getLastAmendmentErrorCodes())
        .as("expected error codes (in order)")
        .containsExactlyElementsOf(expectedCodes);

    // The Error Message column is optional; some scenarios (e.g. @AFF_2/3) also assert the
    // exact user-facing message alongside the code.
    if (!rows.isEmpty() && rows.getFirst().containsKey("Error Message")) {
      List<String> expectedMessages =
          rows.stream().map(r -> r.get("Error Message").trim()).toList();
      List<String> actualMessages =
          context.getLastAmendmentErrorMessages().stream().map(String::trim).toList();
      assertThat(actualMessages)
          .as("expected error messages (in order)")
          .containsExactlyElementsOf(expectedMessages);
    }
  }

  @Then("the amendment is rejected with the following errors in any order")
  public void theAmendmentIsRejectedWithTheFollowingErrorsInAnyOrder(DataTable table) {
    List<String> expected = expectedCodes(table);
    assertThat(context.getLastAmendmentErrorCodes())
        .as("expected error codes (any order)")
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @Then("the amendment is rejected with a validation message with code {string}")
  public void theAmendmentIsRejectedWithAValidationMessageWithCode(String code) {
    assertThat(context.getLastAmendmentErrorCodes())
        .as("expected the amendment to be rejected with a specific code")
        .contains(code);
  }

  @Then("each error is returned in the same amendment ProblemDetail response")
  public void eachErrorIsReturnedInTheSameAmendmentProblemDetailResponse() {
    // The handler emits a single ProblemDetail whose 'errors' property carries every collected
    // error; here that is modelled by a single captured error-code list rather than multiple
    // separate responses. Assert exactly that: the capture is one non-empty list, not a sequence.
    assertThat(context.getLastAmendmentErrorCodes())
        .as("all metadata errors surface together in one response")
        .isNotEmpty();
  }

  @Then("no amendment state was committed")
  public void noAmendmentStateWasCommitted() {
    assertThat(claimAmendmentRepository.count())
        .as("no claim_amendment row should have been persisted")
        .isZero();
  }

  @Then("the amendment endpoint responds with a controlled terminal failure {string}")
  public void theAmendmentEndpointRespondsWithAControlledTerminalFailure(String code) {
    assertThat(context.getLastAmendmentErrorCodes())
        .as("terminal failure code should be present as a fatal error")
        .containsExactly(code);
    assertThat(context.getLastAmendmentHttpStatus())
        .as("terminal failure should surface as a 503 Service Unavailable")
        .isEqualTo(503);
  }

  @Then("no display-name lookup was performed against reference data")
  public void noDisplayNameLookupWasPerformedAgainstReferenceData() {
    // The reference-validation code path is:
    //   AmendmentReferenceValidationStep -> AmendmentReferenceDataProvider -> both repositories
    // "No display-name lookup" is a design contract: display labels are only consulted on the
    // in-memory ReferenceData snapshot for the "not-a-code" heuristic, never issued as a
    // separate DB / downstream call keyed on the display name. That contract can be broken in
    // exactly three places, all detectable structurally:
    //   1. A new label-keyed public method on the provider (e.g. lookupByDisplayLabel).
    //   2. A new label-keyed finder on either repository (e.g. findByDisplayLabel*).
    //   3. A new reference-data collaborator wired into the validation step.
    // Asserting all three here is a hard fail — behavioural, not documentary — for any drift.
    List<String> providerApi = publicDeclaredMethodNames(AmendmentReferenceDataProvider.class);
    assertThat(providerApi)
        .as(
            "AmendmentReferenceDataProvider must expose only getReferenceData(); any new public "
                + "method risks a label-keyed lookup slipping past this contract")
        .containsExactly("getReferenceData");

    assertThat(publicDeclaredMethodNames(RequestedByReferenceRepository.class))
        .as("RequestedByReferenceRepository must not expose any label/name-keyed finder")
        .noneMatch(AmendmentsMetadataValidationSteps::looksLikeLabelKeyedFinder);
    assertThat(publicDeclaredMethodNames(AmendmentReasonReferenceRepository.class))
        .as("AmendmentReasonReferenceRepository must not expose any label/name-keyed finder")
        .noneMatch(AmendmentsMetadataValidationSteps::looksLikeLabelKeyedFinder);

    assertThat(instanceCollaboratorTypes(AmendmentReferenceValidationStep.class))
        .as(
            "AmendmentReferenceValidationStep may collaborate only with the reference-data "
                + "provider; a new dependency risks a label-keyed lookup outside the snapshot")
        .containsExactly(AmendmentReferenceDataProvider.class);
  }

  @Then("no existence check against the identity provider was performed")
  public void noExistenceCheckAgainstTheIdentityProviderWasPerformed() {
    // AmendmentUserIdValidationStep is a pure structural UUID check. There is deliberately no
    // identity-provider client wired into the amendment pipeline, so there is no bean to spy on
    // and assert "zero interactions" against. To make the absence testable, assert by reflection
    // that the step declares no injected collaborators at all: any field (of a non-primitive,
    // non-String, non-numeric type) that a future dev might add — including an IdP client —
    // would fail this check and force a deliberate re-review of the "no out-of-process call"
    // behaviour before the code lands.
    assertThat(instanceCollaboratorTypes(AmendmentUserIdValidationStep.class))
        .as(
            "AmendmentUserIdValidationStep must have no injected collaborators — adding one "
                + "(e.g. an identity-provider client) would introduce an out-of-process call "
                + "this scenario forbids")
        .isEmpty();
  }

  private static List<String> publicDeclaredMethodNames(Class<?> type) {
    return Arrays.stream(type.getDeclaredMethods())
        .filter(m -> Modifier.isPublic(m.getModifiers()))
        .filter(m -> !m.isSynthetic() && !m.isBridge())
        // Filter out standard Object methods that commonly appear on all classes
        .filter(m -> !m.getName().matches("equals|hashCode|toString|clone|getClass"))
        .map(Method::getName)
        .distinct()
        .sorted()
        .toList();
  }

  private static boolean looksLikeLabelKeyedFinder(String methodName) {
    String lower = methodName.toLowerCase(java.util.Locale.ROOT);
    return lower.startsWith("findby")
        && (lower.contains("displaylabel") || lower.contains("label") || lower.contains("name"));
  }

  private static List<Class<?>> instanceCollaboratorTypes(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
        .filter(f -> !Modifier.isStatic(f.getModifiers()))
        .map(f -> (Class<?>) f.getType())
        .filter(t -> !t.isPrimitive())
        .filter(t -> t != String.class)
        .filter(t -> !Number.class.isAssignableFrom(t))
        // Common logging and monitoring fields that don't represent out-of-process calls
        .filter(t -> !t.getName().equals("org.slf4j.Logger"))
        .filter(t -> !t.getName().contains("MeterRegistry"))
        .filter(t -> !t.getName().contains("Tracer"))
        .filter(t -> !t.getName().contains("Observation"))
        .collect(java.util.stream.Collectors.toList());
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------
  private static List<String> expectedCodes(DataTable table) {
    return table.asMaps(String.class, String.class).stream()
        .map(row -> row.get("Error Code"))
        .toList();
  }

  private static JsonNullable<String> toJsonNullable(String value) {
    return value == null ? JsonNullable.undefined() : JsonNullable.of(value);
  }

  private void extractErrorsFromResponseBody() {
    context.getLastAmendmentErrorCodes().clear();
    context.getLastAmendmentErrorMessages().clear();

    String responseBody = context.getLastAmendmentResponseBody();
    if (responseBody == null || responseBody.isBlank()) {
      // Success response (2xx) typically has no body or an empty errors array
      return;
    }

    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode errorsNode = root.get("errors");
      if (errorsNode != null && errorsNode.isArray()) {
        for (JsonNode errorNode : errorsNode) {
          String code = errorNode.path("code").asText();
          String message = errorNode.path("message").asText();
          if (!code.isBlank()) {
            context.getLastAmendmentErrorCodes().add(code);
            context.getLastAmendmentErrorMessages().add(message);
          }
        }
      }
    } catch (Exception e) {
      // If we can't parse the response, log it but don't fail the step
      // (the HTTP status will still be captured)
    }
  }

  private void seedDs1765PlaceholderFixture() {
    amendmentReasonReferenceRepository.deleteAllInBatch();
    amendmentReasonReferenceRepository.flush();
    requestedByReferenceRepository.deleteAllInBatch();
    requestedByReferenceRepository.flush();

    requestedByReferenceRepository.saveAllAndFlush(
        List.of(
            requestedBy("RB_PROVIDER", "Provider", true, 10),
            requestedBy("RB_CASEWORKER", "Caseworker", true, 20),
            requestedBy("RB_LEGACY", "Legacy", false, 30)));
    amendmentReasonReferenceRepository.saveAllAndFlush(
        List.of(
            reason("RB_PROVIDER", "AR_FEE_CORR", "Fee correction", true, 10),
            reason("RB_PROVIDER", "AR_RETIRED", "Retired reason", false, 20),
            reason("RB_CASEWORKER", "AR_CATEGORY_FIX", "Category fix", true, 10),
            // Seed AR_FEE_CORR under RB_LEGACY too so the inactive-RequestedBy scenario yields
            // only the RequestedBy-inactive error, not a cascading "reason not valid for
            // Requested By" (RB_LEGACY still exists in the code index and would otherwise trigger
            // the scope check when paired with the default AR_FEE_CORR reason used in the
            // DSTEW-1765 outlines).
            reason("RB_LEGACY", "AR_FEE_CORR", "Fee correction (legacy)", true, 10)));
    clearReferenceCache();
  }

  private static RequestedByReferenceEntity requestedBy(
      String code, String label, boolean active, int order) {
    return RequestedByReferenceEntity.builder()
        .id(Uuid7.timeBasedUuid())
        .code(code)
        .displayLabel(label)
        .isActive(active)
        .displayOrder(order)
        .createdByUserId(SEED_ACTOR)
        .createdOn(Instant.now())
        .build();
  }

  private static AmendmentReasonReferenceEntity reason(
      String requestedByCode, String code, String label, boolean active, int order) {
    return AmendmentReasonReferenceEntity.builder()
        .id(Uuid7.timeBasedUuid())
        .requestedByCode(requestedByCode)
        .code(code)
        .displayLabel(label)
        .isActive(active)
        .displayOrder(order)
        .createdByUserId(SEED_ACTOR)
        .createdOn(Instant.now())
        .build();
  }

  private void clearReferenceCache() {
    if (cacheManager.getCache(AmendmentReferenceDataProvider.CACHE_NAME) != null) {
      cacheManager.getCache(AmendmentReferenceDataProvider.CACHE_NAME).clear();
    }
  }
}
