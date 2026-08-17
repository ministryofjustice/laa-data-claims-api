package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.DEFAULT_OFFICE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/** Step definitions for amendment metadata reference-data lookup scenarios (DSTEW-1594). */
public class AmendmentMetadataLookupSteps {

  private static final String SEED_ACTOR = "bdd-seed-loader";
  private static final String DEFAULT_SUBMISSION_PERIOD = "JUL-2025";

  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext context;
  @Autowired private RequestedByReferenceRepository requestedByReferenceRepository;
  @Autowired private AmendmentReasonReferenceRepository amendmentReasonReferenceRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimAmendmentRepository claimAmendmentRepository;
  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private CacheManager cacheManager;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Given("the amendment metadata reference data has been seeded with the BC-574 defaults")
  public void theAmendmentMetadataReferenceDataHasBeenSeededWithTheBc574Defaults()
      throws Exception {
    seedBc574Defaults();
    api.setAmendmentRequestedInContextByReferences();
    api.assertLastResponseStatus(200);

    JsonNode requestedBy = requestedByArrayFromLastResponse();
    List<String> codes = jsonTextValues(requestedBy, "code");
    assertThat(codes)
        .as("BC-574 default Requested By values should be available")
        .contains("PROVIDER", "CONTRACT_MANAGEMENT", "ASSURANCE");
  }

  @Given("the amendment metadata reference data contains no active Requested By values")
  public void theAmendmentMetadataReferenceDataContainsNoActiveRequestedByValues() {
    replaceReferenceData(List.of(), List.of());
  }

  @Given(
      "the amendment metadata reference data contains only Requested By {string} with reason {string}")
  public void theAmendmentMetadataReferenceDataContainsOnlyRequestedByWithReason(
      String requestedByCode, String reasonCode) {
    replaceReferenceData(
        List.of(requestedBy(requestedByCode, labelForRequestedBy(requestedByCode), 10)),
        List.of(reason(requestedByCode, reasonCode, labelForReason(reasonCode), 10)));
  }

  @Given("the Requested By value {string} is marked inactive")
  public void theRequestedByValueIsMarkedInactive(String requestedByCode) {
    jdbcTemplate.update(
        "DELETE FROM claims.amendment_reason_reference WHERE requested_by_code = ?",
        requestedByCode);
    jdbcTemplate.update(
        "DELETE FROM claims.requested_by_reference WHERE code = ?", requestedByCode);
    clearReferenceCache();
  }

  @Given("the Amendment Reason {string} under Requested By {string} is marked inactive")
  public void theAmendmentReasonUnderRequestedByIsMarkedInactive(
      String reasonCode, String requestedByCode) {
    jdbcTemplate.update(
        "DELETE FROM claims.amendment_reason_reference WHERE requested_by_code = ? AND code = ?",
        requestedByCode,
        reasonCode);
    clearReferenceCache();
  }

  @Given(
      "a new active Requested By value with code {string}, label {string} and display_order {int} is loaded without redeploying the service")
  public void aNewActiveRequestedByValueIsLoadedWithoutRedeploying(
      String code, String label, int displayOrder) {
    insertRequestedByRow(code, label, displayOrder, SEED_ACTOR);
  }

  @Given(
      "a new active Amendment Reason with code {string} under Requested By {string} with label {string} and display_order {int} is loaded without redeploying the service")
  public void aNewActiveAmendmentReasonIsLoadedWithoutRedeploying(
      String reasonCode, String requestedByCode, String label, int displayOrder) {
    insertReasonRow(requestedByCode, reasonCode, label, displayOrder, SEED_ACTOR);
  }

  @Given("the display label for Requested By code {string} is updated to {string}")
  @When("the display label for Requested By {string} is updated to {string}")
  public void theDisplayLabelForRequestedByIsUpdated(String requestedByCode, String newLabel) {
    RequestedByReferenceEntity row = requiredRequestedByRow(requestedByCode);
    row.setDisplayLabel(newLabel);
    requestedByReferenceRepository.saveAndFlush(row);
    clearReferenceCache();
  }

  @Given(
      "the display label for Amendment Reason code {string} under Requested By {string} is updated to {string}")
  @When(
      "the display label for Amendment Reason {string} under Requested By {string} is updated to {string}")
  public void theDisplayLabelForAmendmentReasonIsUpdated(
      String reasonCode, String requestedByCode, String newLabel) {
    AmendmentReasonReferenceEntity row = requiredReasonRow(requestedByCode, reasonCode);
    row.setDisplayLabel(newLabel);
    amendmentReasonReferenceRepository.saveAndFlush(row);
    clearReferenceCache();
  }

  @Given("the Requested By value {string} was originally created by actor {string}")
  public void theRequestedByValueWasOriginallyCreatedByActor(String requestedByCode, String actor) {
    jdbcTemplate.update(
        """
        UPDATE claims.requested_by_reference
        SET created_by_user_id = ?, updated_by_user_id = NULL, updated_on = NULL
        WHERE code = ?
        """,
        actor,
        requestedByCode);
    clearReferenceCache();
  }

  @Given(
      "an amendment record persists the codes requested_by_code {string} and amendment_reason_code {string}")
  public void anAmendmentRecordPersistsTheCodes(String requestedByCode, String amendmentReasonCode)
      throws Exception {
    UUID submissionId = Uuid7.timeBasedUuid();
    Submission submission =
        Submission.builder()
            .id(submissionId)
            .officeAccountNumber(DEFAULT_OFFICE)
            .submissionPeriod(DEFAULT_SUBMISSION_PERIOD)
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.READY_FOR_VALIDATION)
            .createdByUserId("bdd-amendment-user")
            .providerUserId("bdd-amendment-user")
            .createdOn(Instant.now())
            .build();
    submissionRepository.saveAndFlush(submission);

    UUID claimId = Uuid7.timeBasedUuid();
    Claim seededClaim =
        Claim.builder()
            .id(claimId)
            .submission(submission)
            .status(ClaimStatus.VALID)
            .lineNumber(1)
            .matterTypeCode("MAT01")
            .createdByUserId("bdd-amendment-user")
            .build();
    claimRepository.saveAndFlush(seededClaim);

    Claim claim =
        claimRepository
            .findById(claimId)
            .orElseThrow(() -> new AssertionError("Claim should exist after createClaim"));

    ClaimAmendment amendment =
        ClaimAmendment.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .requestedByCode(requestedByCode)
            .amendmentReasonCode(amendmentReasonCode)
            .beforeState("{}")
            .requestPayload("{}")
            .diff("{}")
            .createdByUserId("bdd-amendment-user")
            .createdOn(Instant.now())
            .build();

    claimAmendmentRepository.saveAndFlush(amendment);
    context.setLastPersistedClaimAmendmentId(amendment.getId());
  }

  @When("I request the amendment metadata reference lookup")
  public void iRequestTheAmendmentMetadataReferenceLookup() {
    api.setAmendmentRequestedInContextByReferences();
  }

  @When("^I insert a new (.+) row via the seed/load mechanism$")
  public void iInsertANewRowViaTheSeedLoadMechanism(String tableName) {
    UUID id = Uuid7.timeBasedUuid();
    String suffix = id.toString().substring(0, 8).toUpperCase();

    switch (tableName) {
      case "requested_by_reference" ->
          insertRequestedByRow("AUDITOR_" + suffix, "Auditor " + suffix, 40, SEED_ACTOR, id);
      case "amendment_reason_reference" ->
          insertReasonRow("PROVIDER", "OTHER_" + suffix, "Other " + suffix, 40, SEED_ACTOR, id);
      default ->
          throw new IllegalArgumentException("Unsupported table for seed/load step: " + tableName);
    }

    context.setLastGeneratedReferenceRowId(id);
  }

  @When("a new Requested By value with code {string} is loaded by actor {string}")
  public void aNewRequestedByValueWithCodeIsLoadedByActor(String code, String actor) {
    insertRequestedByRow(code, "Auditor", 40, actor);
  }

  @When("the {word} for Requested By {string} is updated by actor {string}")
  public void theColumnForRequestedByIsUpdatedByActor(String column, String code, String actor) {
    RequestedByReferenceEntity row = requiredRequestedByRow(code);

    switch (column) {
      case "display_label" -> row.setDisplayLabel(row.getDisplayLabel() + " (updated)");
      case "is_active" -> row.setIsActive(!Boolean.TRUE.equals(row.getIsActive()));
      case "display_order" -> row.setDisplayOrder(row.getDisplayOrder() + 1);
      default -> throw new IllegalArgumentException("Unsupported governed column: " + column);
    }

    row.setUpdatedByUserId(actor);
    requestedByReferenceRepository.saveAndFlush(row);
    clearReferenceCache();
  }

  @Then("the lookup response lists the following Requested By values in order")
  public void theLookupResponseListsTheFollowingRequestedByValuesInOrder(DataTable expectedTable)
      throws Exception {
    api.assertLastResponseStatus(200);

    List<Map<String, String>> expectedRows = expectedTable.asMaps(String.class, String.class);
    JsonNode requestedBy = requestedByArrayFromLastResponse();

    assertThat(requestedBy.isArray()).as("`requested_by` must be a JSON array").isTrue();
    assertThat(requestedBy.size())
        .as("Lookup should return the exact number of Requested By values listed in the table")
        .isEqualTo(expectedRows.size());

    for (int i = 0; i < expectedRows.size(); i++) {
      Map<String, String> expected = expectedRows.get(i);
      JsonNode actual = requestedBy.get(i);

      assertThat(actual.path("code").asText(""))
          .as("Requested By code at position %s", i)
          .isEqualTo(expected.get("code"));
      assertThat(actual.path("display_label").asText(""))
          .as("Requested By display_label at position %s", i)
          .isEqualTo(expected.get("display_label"));
      assertThat(actual.path("display_order").asInt(Integer.MIN_VALUE))
          .as("Requested By display_order at position %s", i)
          .isEqualTo(Integer.parseInt(expected.get("display_order")));
    }
  }

  @Then("the Requested By value {string} carries the following reasons in order")
  public void theRequestedByValueCarriesTheFollowingReasonsInOrder(
      String requestedByCode, DataTable expectedTable) throws Exception {
    api.assertLastResponseStatus(200);

    List<Map<String, String>> expectedRows = expectedTable.asMaps(String.class, String.class);
    JsonNode reasons = reasonsForRequestedBy(requestedByCode);

    assertThat(reasons.size())
        .as("Requested By %s should have the expected number of reasons", requestedByCode)
        .isEqualTo(expectedRows.size());

    for (int i = 0; i < expectedRows.size(); i++) {
      Map<String, String> expected = expectedRows.get(i);
      JsonNode actual = reasons.get(i);

      assertThat(actual.path("code").asText(""))
          .as("Reason code under Requested By %s at position %s", requestedByCode, i)
          .isEqualTo(expected.get("code"));
      assertThat(actual.path("display_label").asText(""))
          .as("Reason display_label under Requested By %s at position %s", requestedByCode, i)
          .isEqualTo(expected.get("display_label"));
      assertThat(actual.path("display_order").asInt(Integer.MIN_VALUE))
          .as("Reason display_order under Requested By %s at position %s", requestedByCode, i)
          .isEqualTo(Integer.parseInt(expected.get("display_order")));
    }
  }

  @Then("the reason {string} is listed under Requested By {string}")
  public void theReasonIsListedUnderRequestedBy(String reasonCode, String requestedByCode)
      throws Exception {
    assertReasonListedUnderRequestedBy(reasonCode, requestedByCode, true);
  }

  @Then("the reason {string} is not listed under Requested By {string}")
  public void theReasonIsNotListedUnderRequestedBy(String reasonCode, String requestedByCode)
      throws Exception {
    assertReasonListedUnderRequestedBy(reasonCode, requestedByCode, false);
  }

  @Then("the lookup response does not contain the Requested By value {string}")
  public void theLookupResponseDoesNotContainTheRequestedByValue(String requestedByCode)
      throws Exception {
    assertRequestedByPresent(requestedByCode, false);
  }

  @Then("the lookup response still contains the Requested By value {string}")
  public void theLookupResponseStillContainsTheRequestedByValue(String requestedByCode)
      throws Exception {
    assertRequestedByPresent(requestedByCode, true);
  }

  @Then("the lookup response does not contain any reasons scoped to Requested By {string}")
  public void theLookupResponseDoesNotContainAnyReasonsScopedToRequestedBy(String requestedByCode)
      throws Exception {
    JsonNode requestedBy = requestedByArrayFromLastResponse();
    for (JsonNode node : requestedBy) {
      if (requestedByCode.equals(node.path("code").asText(""))) {
        assertThat(node.path("reasons").isArray()).isTrue();
        assertThat(node.path("reasons")).isEmpty();
      }
    }
  }

  @Then("the Requested By value {string} still contains the reason {string}")
  public void theRequestedByValueStillContainsTheReason(String requestedByCode, String reasonCode)
      throws Exception {
    assertReasonListedUnderRequestedBy(reasonCode, requestedByCode, true);
  }

  @Then(
      "the lookup response contains the Requested By value {string} with display label {string} at display_order {int}")
  public void theLookupResponseContainsRequestedByWithDisplayAndOrder(
      String requestedByCode, String expectedLabel, int expectedOrder) throws Exception {
    JsonNode row = findRequestedByNode(requestedByCode);
    assertThat(row.path("display_label").asText("")).isEqualTo(expectedLabel);
    assertThat(row.path("display_order").asInt(Integer.MIN_VALUE)).isEqualTo(expectedOrder);
  }

  @Then("the Requested By value {string} carries no reasons")
  public void theRequestedByValueCarriesNoReasons(String requestedByCode) throws Exception {
    JsonNode reasons = reasonsForRequestedBy(requestedByCode);
    assertThat(reasons).isEmpty();
  }

  @Then("the Requested By value with code {string} has display label {string}")
  public void theRequestedByValueWithCodeHasDisplayLabel(String requestedByCode, String label)
      throws Exception {
    JsonNode row = findRequestedByNode(requestedByCode);
    assertThat(row.path("display_label").asText("")).isEqualTo(label);
  }

  @Then("the Requested By code {string} is unchanged")
  public void theRequestedByCodeIsUnchanged(String requestedByCode) throws Exception {
    assertRequestedByPresent(requestedByCode, true);
  }

  @Then(
      "every Amendment Reason previously scoped to Requested By {string} is still scoped to {string}")
  public void everyAmendmentReasonPreviouslyScopedToRequestedByIsStillScopedTo(
      String originalRequestedByCode, String expectedRequestedByCode) throws Exception {
    List<String> expectedCodes =
        amendmentReasonReferenceRepository.findAll().stream()
            .filter(r -> expectedRequestedByCode.equals(r.getRequestedByCode()))
            .map(AmendmentReasonReferenceEntity::getCode)
            .toList();

    JsonNode reasons = reasonsForRequestedBy(expectedRequestedByCode);
    assertThat(jsonTextValues(reasons, "code"))
        .as(
            "Reasons should remain scoped from `%s` to `%s`",
            originalRequestedByCode, expectedRequestedByCode)
        .containsExactlyElementsOf(expectedCodes);
  }

  @Then("under Requested By {string} the reason with code {string} has display label {string}")
  public void underRequestedByTheReasonWithCodeHasDisplayLabel(
      String requestedByCode, String reasonCode, String expectedLabel) throws Exception {
    JsonNode reason = findReasonNode(requestedByCode, reasonCode);
    assertThat(reason.path("display_label").asText("")).isEqualTo(expectedLabel);
  }

  @Then("under Requested By {string} the reason code {string} is unchanged")
  public void underRequestedByTheReasonCodeIsUnchanged(String requestedByCode, String reasonCode)
      throws Exception {
    assertReasonListedUnderRequestedBy(reasonCode, requestedByCode, true);
  }

  @Then("the lookup response contains an empty Requested By list")
  public void theLookupResponseContainsAnEmptyRequestedByList() throws Exception {
    api.assertLastResponseStatus(200);
    JsonNode requestedBy = requestedByArrayFromLastResponse();
    assertThat(requestedBy.isArray()).isTrue();
    assertThat(requestedBy).isEmpty();
  }

  @Then("the lookup response lists exactly one Requested By value with code {string}")
  public void theLookupResponseListsExactlyOneRequestedByValueWithCode(String requestedByCode)
      throws Exception {
    api.assertLastResponseStatus(200);

    JsonNode requestedBy = requestedByArrayFromLastResponse();
    assertThat(requestedBy.size())
        .as("Lookup should contain exactly one Requested By value")
        .isOne();
    assertThat(requestedBy.get(0).path("code").asText(""))
        .as("The single Requested By value should have the expected code")
        .isEqualTo(requestedByCode);
  }

  @Then("the Requested By value {string} carries exactly one reason with code {string}")
  public void theRequestedByValueCarriesExactlyOneReasonWithCode(
      String requestedByCode, String reasonCode) throws Exception {
    JsonNode reasons = reasonsForRequestedBy(requestedByCode);

    assertThat(reasons.size())
        .as("Requested By %s should contain exactly one reason", requestedByCode)
        .isOne();
    assertThat(reasons.get(0).path("code").asText(""))
        .as(
            "The single reason under Requested By %s should have the expected code",
            requestedByCode)
        .isEqualTo(reasonCode);
  }

  @Then(
      "the reason {string} under Requested By {string} has no free-text supporting field in the response")
  public void theReasonUnderRequestedByHasNoFreeTextSupportingFieldInTheResponse(
      String reasonCode, String requestedByCode) throws Exception {
    JsonNode reason = findReasonNode(requestedByCode, reasonCode);
    assertThat(reason.has("free_text")).as("reason should not expose free_text").isFalse();
    assertThat(reason.has("supporting_field"))
        .as("reason should not expose supporting_field")
        .isFalse();
    assertThat(reason.has("supporting_text"))
        .as("reason should not expose supporting_text")
        .isFalse();
  }

  @Then("the generated id is a valid UUID")
  public void theGeneratedIdIsAValidUuid() {
    UUID id = requiredGeneratedReferenceId();
    assertThat(Uuid7.isValidUuid(id.toString())).isTrue();
  }

  @Then("the generated id is UUIDv7")
  public void theGeneratedIdIsUuidv7() {
    UUID id = requiredGeneratedReferenceId();
    assertThat(id.version()).isEqualTo(7);
  }

  @Then("the row for Requested By {string} has created_by_user_id {string}")
  public void theRowForRequestedByHasCreatedByUserId(String code, String expectedActor) {
    RequestedByReferenceEntity row = requiredRequestedByRow(code);
    assertThat(row.getCreatedByUserId()).isEqualTo(expectedActor);
  }

  @Then("the row for Requested By {string} has a non-null created_on timestamp")
  public void theRowForRequestedByHasNonNullCreatedOnTimestamp(String code) {
    RequestedByReferenceEntity row = requiredRequestedByRow(code);
    assertThat(row.getCreatedOn()).isNotNull();
  }

  @Then("the row for Requested By {string} has null updated_by_user_id")
  public void theRowForRequestedByHasNullUpdatedByUserId(String code) {
    RequestedByReferenceEntity row = requiredRequestedByRow(code);
    assertThat(row.getUpdatedByUserId()).isNull();
  }

  @Then("the row for Requested By {string} has null updated_on")
  public void theRowForRequestedByHasNullUpdatedOn(String code) {
    RequestedByReferenceEntity row = requiredRequestedByRow(code);
    assertThat(row.getUpdatedOn()).isNull();
  }

  @Then("the row for Requested By {string} has updated_by_user_id {string}")
  public void theRowForRequestedByHasUpdatedByUserId(String code, String expectedActor) {
    RequestedByReferenceEntity row = requiredRequestedByRow(code);
    assertThat(row.getUpdatedByUserId()).isEqualTo(expectedActor);
  }

  @Then("the row for Requested By {string} has a non-null updated_on timestamp")
  public void theRowForRequestedByHasNonNullUpdatedOnTimestamp(String code) {
    RequestedByReferenceEntity row = requiredRequestedByRow(code);
    assertThat(row.getUpdatedOn()).isNotNull();
  }

  @Then("the row for Requested By {string} has created_by_user_id {string} unchanged")
  public void theRowForRequestedByHasCreatedByUserIdUnchanged(String code, String expectedActor) {
    RequestedByReferenceEntity row = requiredRequestedByRow(code);
    assertThat(row.getCreatedByUserId()).isEqualTo(expectedActor);
  }

  @Then("the amendment record still references requested_by_code {string}")
  public void theAmendmentRecordStillReferencesRequestedByCode(String expectedCode) {
    ClaimAmendment amendment = requiredPersistedAmendment();
    assertThat(amendment.getRequestedByCode()).isEqualTo(expectedCode);
  }

  @Then("the amendment record still references amendment_reason_code {string}")
  public void theAmendmentRecordStillReferencesAmendmentReasonCode(String expectedCode) {
    ClaimAmendment amendment = requiredPersistedAmendment();
    assertThat(amendment.getAmendmentReasonCode()).isEqualTo(expectedCode);
  }

  @Then("the amendment metadata reference lookup returns those codes paired together")
  public void theAmendmentMetadataReferenceLookupReturnsThoseCodesPairedTogether()
      throws Exception {
    ClaimAmendment amendment = requiredPersistedAmendment();
    api.setAmendmentRequestedInContextByReferences();
    api.assertLastResponseStatus(200);
    assertReasonListedUnderRequestedBy(
        amendment.getAmendmentReasonCode(), amendment.getRequestedByCode(), true);
  }

  private UUID requiredGeneratedReferenceId() {
    UUID id = context.getLastGeneratedReferenceRowId();
    assertThat(id).as("No generated reference row id captured").isNotNull();
    return id;
  }

  private ClaimAmendment requiredPersistedAmendment() {
    UUID amendmentId = context.getLastPersistedClaimAmendmentId();
    assertThat(amendmentId).as("No persisted amendment id captured").isNotNull();
    return claimAmendmentRepository
        .findById(amendmentId)
        .orElseThrow(() -> new AssertionError("Persisted amendment row not found: " + amendmentId));
  }

  private JsonNode requestedByArrayFromLastResponse() throws IOException {
    String body = context.getLastResponseBody();
    assertThat(body)
        .as("Last response body should be captured before lookup assertions")
        .isNotBlank();

    JsonNode root = objectMapper.readTree(body);
    return root.path("requested_by");
  }

  private JsonNode reasonsForRequestedBy(String requestedByCode) throws IOException {
    JsonNode requestedBy = findRequestedByNode(requestedByCode);
    JsonNode reasons = requestedBy.path("reasons");
    assertThat(reasons.isArray())
        .as("Requested By %s should expose reasons as an array", requestedByCode)
        .isTrue();
    return reasons;
  }

  private JsonNode findRequestedByNode(String requestedByCode) throws IOException {
    JsonNode requestedBy = requestedByArrayFromLastResponse();
    assertThat(requestedBy.isArray()).as("`requested_by` must be a JSON array").isTrue();

    return java.util.stream.StreamSupport.stream(requestedBy.spliterator(), false)
        .filter(node -> requestedByCode.equals(node.path("code").asText("")))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "Lookup response did not contain Requested By value `%s`"
                        .formatted(requestedByCode)));
  }

  private JsonNode findReasonNode(String requestedByCode, String reasonCode) throws Exception {
    JsonNode reasons = reasonsForRequestedBy(requestedByCode);
    return java.util.stream.StreamSupport.stream(reasons.spliterator(), false)
        .filter(node -> reasonCode.equals(node.path("code").asText("")))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "Requested By `%s` did not contain reason `%s`"
                        .formatted(requestedByCode, reasonCode)));
  }

  private void assertReasonListedUnderRequestedBy(
      String reasonCode, String requestedByCode, boolean expectedPresent) throws Exception {
    JsonNode reasons = reasonsForRequestedBy(requestedByCode);
    boolean present =
        java.util.stream.StreamSupport.stream(reasons.spliterator(), false)
            .anyMatch(node -> reasonCode.equals(node.path("code").asText("")));

    assertThat(present)
        .as("Reason %s presence under Requested By %s", reasonCode, requestedByCode)
        .isEqualTo(expectedPresent);
  }

  private void assertRequestedByPresent(String code, boolean expectedPresent) throws Exception {
    JsonNode requestedBy = requestedByArrayFromLastResponse();
    boolean present =
        java.util.stream.StreamSupport.stream(requestedBy.spliterator(), false)
            .anyMatch(node -> code.equals(node.path("code").asText("")));
    assertThat(present).isEqualTo(expectedPresent);
  }

  private void seedBc574Defaults() {
    replaceReferenceData(
        List.of(
            requestedBy("PROVIDER", "Provider", 10),
            requestedBy("CONTRACT_MANAGEMENT", "Contract Management", 20),
            requestedBy("ASSURANCE", "Assurance", 30)),
        List.of(
            reason("PROVIDER", "PROVIDER_ERROR", "Provider Error", 10),
            reason(
                "PROVIDER",
                "CASE_REOPENED_REBILLED",
                "Case re-opened and being billed again later",
                20),
            reason(
                "PROVIDER",
                "RECOVERY_FROM_CLIENT_OR_OTHER_SIDE",
                "Money recovered from client and/or other side (inc. stat charge)",
                30),
            reason(
                "CONTRACT_MANAGEMENT",
                "INCORRECT_MEANS_ASSESSMENT",
                "Incorrect Means Assessment",
                10),
            reason("CONTRACT_MANAGEMENT", "OTHER", "Other", 20),
            reason("ASSURANCE", "INCORRECT_MEANS_ASSESSMENT", "Incorrect Means Assessment", 10),
            reason("ASSURANCE", "OTHER", "Other", 20)));
  }

  private void replaceReferenceData(
      List<RequestedByReferenceEntity> requestedByValues,
      List<AmendmentReasonReferenceEntity> reasons) {
    amendmentReasonReferenceRepository.deleteAllInBatch();
    amendmentReasonReferenceRepository.flush();
    requestedByReferenceRepository.deleteAllInBatch();
    requestedByReferenceRepository.flush();

    requestedByReferenceRepository.saveAllAndFlush(new ArrayList<>(requestedByValues));
    amendmentReasonReferenceRepository.saveAllAndFlush(new ArrayList<>(reasons));
    clearReferenceCache();
  }

  private void insertRequestedByRow(String code, String label, int displayOrder, String actor) {
    insertRequestedByRow(code, label, displayOrder, actor, Uuid7.timeBasedUuid());
  }

  private void insertRequestedByRow(
      String code, String label, int displayOrder, String actor, UUID id) {
    jdbcTemplate.update(
        """
        INSERT INTO claims.requested_by_reference
        (id, code, display_label, is_active, display_order, created_by_user_id, created_on, updated_by_user_id, updated_on)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        code,
        label,
        true,
        displayOrder,
        actor,
        Timestamp.from(Instant.now()),
        null,
        null);
    clearReferenceCache();
  }

  private void insertReasonRow(
      String requestedByCode, String code, String label, int displayOrder, String actor) {
    insertReasonRow(requestedByCode, code, label, displayOrder, actor, Uuid7.timeBasedUuid());
  }

  private void insertReasonRow(
      String requestedByCode, String code, String label, int displayOrder, String actor, UUID id) {
    jdbcTemplate.update(
        """
        INSERT INTO claims.amendment_reason_reference
        (id, requested_by_code, code, display_label, is_active, display_order, created_by_user_id, created_on, updated_by_user_id, updated_on)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        requestedByCode,
        code,
        label,
        true,
        displayOrder,
        actor,
        Timestamp.from(Instant.now()),
        null,
        null);
    clearReferenceCache();
  }

  private RequestedByReferenceEntity requiredRequestedByRow(String code) {
    return requestedByReferenceRepository.findAll().stream()
        .filter(row -> code.equals(row.getCode()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Requested By row not found for code " + code));
  }

  private AmendmentReasonReferenceEntity requiredReasonRow(
      String requestedByCode, String reasonCode) {
    return amendmentReasonReferenceRepository.findAll().stream()
        .filter(
            row ->
                requestedByCode.equals(row.getRequestedByCode())
                    && reasonCode.equals(row.getCode()))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "Reason row not found for requestedBy="
                        + requestedByCode
                        + ", code="
                        + reasonCode));
  }

  private void clearReferenceCache() {
    if (cacheManager.getCache(AmendmentReferenceDataProvider.CACHE_NAME) != null) {
      cacheManager.getCache(AmendmentReferenceDataProvider.CACHE_NAME).clear();
    }
  }

  private static RequestedByReferenceEntity requestedBy(
      String code, String displayLabel, int displayOrder) {
    return RequestedByReferenceEntity.builder()
        .id(Uuid7.timeBasedUuid())
        .code(code)
        .displayLabel(displayLabel)
        .isActive(true)
        .displayOrder(displayOrder)
        .createdByUserId(SEED_ACTOR)
        .createdOn(Instant.now())
        .build();
  }

  private static AmendmentReasonReferenceEntity reason(
      String requestedByCode, String code, String displayLabel, int displayOrder) {
    return AmendmentReasonReferenceEntity.builder()
        .id(Uuid7.timeBasedUuid())
        .requestedByCode(requestedByCode)
        .code(code)
        .displayLabel(displayLabel)
        .isActive(true)
        .displayOrder(displayOrder)
        .createdByUserId(SEED_ACTOR)
        .createdOn(Instant.now())
        .build();
  }

  private static String labelForRequestedBy(String requestedByCode) {
    return switch (requestedByCode) {
      case "PROVIDER" -> "Provider";
      case "CONTRACT_MANAGEMENT" -> "Contract Management";
      case "ASSURANCE" -> "Assurance";
      default -> requestedByCode;
    };
  }

  private static String labelForReason(String reasonCode) {
    return switch (reasonCode) {
      case "PROVIDER_ERROR" -> "Provider Error";
      case "CASE_REOPENED_REBILLED" -> "Case re-opened and being billed again later";
      case "RECOVERY_FROM_CLIENT_OR_OTHER_SIDE" ->
          "Money recovered from client and/or other side (inc. stat charge)";
      case "INCORRECT_MEANS_ASSESSMENT" -> "Incorrect Means Assessment";
      case "OTHER" -> "Other";
      default -> reasonCode;
    };
  }

  private static List<String> jsonTextValues(JsonNode array, String fieldName) {
    assertThat(array.isArray()).as("Expected JSON array when reading `%s`", fieldName).isTrue();
    return java.util.stream.StreamSupport.stream(array.spliterator(), false)
        .map(node -> node.path(fieldName).asText(""))
        .toList();
  }
}
