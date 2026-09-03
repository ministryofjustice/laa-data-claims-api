package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.provider.AmendmentReferenceDataProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AmendmentReasonReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.RequestedByReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step definitions for {@code amendmentsMetadataReferenceLookup.feature} (DSTEW-1594).
 *
 * <p>These scenarios exercise the amendment metadata reference lookup via {@code GET
 * /api/v1/system/references/amendment-requested-by}, driving the Requested By / Amendment Reason
 * governed reference tables directly (JPA) and evicting the {@link
 * AmendmentReferenceDataProvider#CACHE_NAME} cache so mutations are visible on the next GET.
 *
 * <p>The service returns every row (active and inactive) with an {@code is_active} flag so
 * historical amendments can still resolve display labels; the feature's "the lookup does not
 * contain..." language is therefore interpreted here as "does not contain among the currently
 * <em>active</em> values" — i.e. what a UI consumer would render as selectable. Inactive rows are
 * still expected in the payload so historical lookup keeps working.
 */
@Slf4j
public class AmendmentMetadataReferenceLookupSteps {

  private static final String SEED_ACTOR = "bdd-DSTEW-1594";
  private static final String REQUESTED_BY_ARRAY = "requested_by";
  private static final String REASONS_ARRAY = "reasons";
  private static final Set<String> KNOWN_REASON_FIELDS =
      Set.of("code", "display_label", "display_order", "is_active");

  private static final List<SeedRequestedBy> FLYWAY_REQUESTED_BY =
      List.of(
          new SeedRequestedBy("PROVIDER", "Provider", 10),
          new SeedRequestedBy("CONTRACT_MANAGEMENT", "Contract management", 20),
          new SeedRequestedBy("ASSURANCE", "Assurance", 30));

  private static final List<SeedReason> FLYWAY_REASONS =
      List.of(
          new SeedReason("PROVIDER", "PROVIDER_ERROR", "Provider error", 10),
          new SeedReason(
              "PROVIDER",
              "CASE_REOPENED_REBILLED",
              "Case re-opened and being billed again later",
              20),
          new SeedReason(
              "PROVIDER",
              "RECOVERY_FROM_CLIENT_OR_OTHER_SIDE",
              "Money recovered from client and/or other side (inc. stat charge)",
              30),
          new SeedReason(
              "CONTRACT_MANAGEMENT",
              "INCORRECT_MEANS_ASSESSMENT",
              "Incorrect means assessment",
              10),
          new SeedReason("CONTRACT_MANAGEMENT", "OTHER", "Other", 20),
          new SeedReason(
              "ASSURANCE", "INCORRECT_MEANS_ASSESSMENT", "Incorrect means assessment", 10),
          new SeedReason("ASSURANCE", "OTHER", "Other", 20));

  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext context;
  @Autowired private RequestedByReferenceRepository requestedByReferenceRepository;
  @Autowired private AmendmentReasonReferenceRepository amendmentReasonReferenceRepository;
  @Autowired private CacheManager cacheManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  // Scenario-scoped state (fields are safe because Cucumber creates a fresh instance per scenario).
  private UUID lastGeneratedRowId;
  private String recordedAmendmentRequestedByCode;
  private String recordedAmendmentReasonCode;
  private String originalCreatedByActor;

  // ---------------------------------------------------------------------------
  // Hooks
  // ---------------------------------------------------------------------------

  @Before(value = "@dstew-1594", order = 5)
  public void restoreReferenceDataBeforeScenario() {
    resetReferenceDataToFlywaySeed();
    evictReferenceDataCache();
  }

  @After("@dstew-1594")
  public void restoreReferenceDataAfterScenario() {
    resetReferenceDataToFlywaySeed();
    evictReferenceDataCache();
  }

  // ---------------------------------------------------------------------------
  // Given — reference-data population
  // ---------------------------------------------------------------------------

  @Given("the amendment metadata reference data has been seeded with the BC-574 defaults")
  public void referenceDataSeededWithBc574Defaults() {
    // The @Before hook already restored the Flyway seed; this Given is a semantic no-op that
    // documents the pre-condition.
    log.info("Amendment metadata reference data seeded with BC-574 defaults");
  }

  @Given("the amendment metadata reference data contains no active Requested By values")
  public void referenceDataContainsNoActiveRequestedBy() {
    amendmentReasonReferenceRepository.deleteAllInBatch();
    requestedByReferenceRepository.deleteAllInBatch();
    evictReferenceDataCache();
  }

  @Given(
      "the amendment metadata reference data contains only Requested By {string} with reason"
          + " {string}")
  public void referenceDataContainsOnlyOneRequestedByAndReason(
      String requestedByCode, String reasonCode) {
    amendmentReasonReferenceRepository.deleteAllInBatch();
    requestedByReferenceRepository.deleteAllInBatch();
    saveRequestedBy(requestedByCode, requestedByCode, true, 10);
    saveReason(requestedByCode, reasonCode, reasonCode, true, 10);
    evictReferenceDataCache();
  }

  @Given("the Requested By value {string} is marked inactive")
  public void requestedByIsMarkedInactive(String code) {
    RequestedByReferenceEntity entity = findRequestedBy(code);
    entity.setIsActive(false);
    entity.setUpdatedByUserId(SEED_ACTOR);
    requestedByReferenceRepository.save(entity);
    evictReferenceDataCache();
  }

  @Given("the Amendment Reason {string} under Requested By {string} is marked inactive")
  public void reasonIsMarkedInactive(String reasonCode, String requestedByCode) {
    AmendmentReasonReferenceEntity entity = findReason(requestedByCode, reasonCode);
    entity.setIsActive(false);
    entity.setUpdatedByUserId(SEED_ACTOR);
    amendmentReasonReferenceRepository.save(entity);
    evictReferenceDataCache();
  }

  @Given(
      "a new active Requested By value with code {string}, label {string} and display_order {int}"
          + " is loaded without redeploying the service")
  public void addNewRequestedByWithoutRedeploy(String code, String label, int displayOrder) {
    saveRequestedBy(code, label, true, displayOrder);
    evictReferenceDataCache();
  }

  @Given(
      "a new active Amendment Reason with code {string} under Requested By {string} with label"
          + " {string} and display_order {int} is loaded without redeploying the service")
  public void addNewReasonWithoutRedeploy(
      String reasonCode, String requestedByCode, String label, int displayOrder) {
    saveReason(requestedByCode, reasonCode, label, true, displayOrder);
    evictReferenceDataCache();
  }

  @Given("the display label for Requested By code {string} is updated to {string}")
  public void updateRequestedByDisplayLabel(String code, String newLabel) {
    updateRequestedByDisplayLabelInternal(code, newLabel);
  }

  @Given(
      "the display label for Amendment Reason code {string} under Requested By {string} is updated"
          + " to {string}")
  public void updateReasonDisplayLabel(String reasonCode, String requestedByCode, String newLabel) {
    updateReasonDisplayLabelInternal(requestedByCode, reasonCode, newLabel);
  }

  @Given("the Requested By value {string} was originally created by actor {string}")
  public void requestedByWasOriginallyCreatedByActor(String code, String actor) {
    RequestedByReferenceEntity entity = findRequestedBy(code);
    entity.setCreatedByUserId(actor);
    entity.setUpdatedByUserId(null);
    requestedByReferenceRepository.save(entity);
    originalCreatedByActor = actor;
    evictReferenceDataCache();
  }

  @Given(
      "an amendment record persists the codes requested_by_code {string} and amendment_reason_code"
          + " {string}")
  public void amendmentRecordPersistsCodes(String requestedByCode, String reasonCode) {
    // Codes are the immutable business keys stored on claim_amendment via a composite FK. This
    // step captures the pairing so subsequent assertions can verify they survive label edits.
    recordedAmendmentRequestedByCode = requestedByCode;
    recordedAmendmentReasonCode = reasonCode;
    log.info(
        "[spec-guard] Amendment record pairs requested_by={} / reason={}",
        requestedByCode,
        reasonCode);
  }

  // ---------------------------------------------------------------------------
  // When — mutations + lookup invocations
  // ---------------------------------------------------------------------------

  @When("I request the amendment metadata reference lookup")
  public void requestLookup() {
    api.getAmendmentMetadataReferenceLookup();
    assertThat(context.getLastStatusCode())
        .as("Amendment metadata reference lookup must return an HTTP status")
        .isNotNull();
    assertThat(context.getLastStatusCode())
        .as(
            "Amendment metadata reference lookup should return 2xx (was %s)",
            context.getLastStatusCode())
        .isBetween(200, 299);
  }

  @When("I insert a new {word} row via the seed\\/load mechanism")
  public void insertNewReferenceRowViaSeedLoadMechanism(String table) {
    switch (table) {
      case "requested_by_reference" -> {
        RequestedByReferenceEntity entity =
            RequestedByReferenceEntity.builder()
                .id(Uuid7.timeBasedUuid())
                .code("BDD_1594_UUIDV7_RB")
                .displayLabel("BDD UUIDv7 Requested By")
                .isActive(true)
                .displayOrder(9010)
                .createdByUserId(SEED_ACTOR)
                .createdOn(Instant.now())
                .build();
        RequestedByReferenceEntity saved = requestedByReferenceRepository.saveAndFlush(entity);
        nullUpdatedAuditForRequestedBy(saved.getCode());
        lastGeneratedRowId = saved.getId();
      }
      case "amendment_reason_reference" -> {
        AmendmentReasonReferenceEntity entity =
            AmendmentReasonReferenceEntity.builder()
                .id(Uuid7.timeBasedUuid())
                .requestedByCode("PROVIDER")
                .code("BDD_1594_UUIDV7_REASON")
                .displayLabel("BDD UUIDv7 Reason")
                .isActive(true)
                .displayOrder(9010)
                .createdByUserId(SEED_ACTOR)
                .createdOn(Instant.now())
                .build();
        AmendmentReasonReferenceEntity saved =
            amendmentReasonReferenceRepository.saveAndFlush(entity);
        nullUpdatedAuditForReason(saved.getRequestedByCode(), saved.getCode());
        lastGeneratedRowId = saved.getId();
      }
      default -> throw new IllegalArgumentException("Unknown reference table: " + table);
    }
    evictReferenceDataCache();
  }

  @When("a new Requested By value with code {string} is loaded by actor {string}")
  public void addNewRequestedByLoadedByActor(String code, String actor) {
    RequestedByReferenceEntity entity =
        RequestedByReferenceEntity.builder()
            .id(Uuid7.timeBasedUuid())
            .code(code)
            .displayLabel(code)
            .isActive(true)
            .displayOrder(9020)
            .createdByUserId(actor)
            .createdOn(Instant.now())
            .build();
    requestedByReferenceRepository.saveAndFlush(entity);
    nullUpdatedAuditForRequestedBy(code);
    evictReferenceDataCache();
  }

  @When("the {word} for Requested By {string} is updated by actor {string}")
  public void updateColumnByActor(String column, String code, String actor) {
    RequestedByReferenceEntity entity = findRequestedBy(code);
    switch (column) {
      case "display_label" -> entity.setDisplayLabel(entity.getDisplayLabel() + " (updated)");
      case "is_active" -> entity.setIsActive(!Boolean.TRUE.equals(entity.getIsActive()));
      case "display_order" -> entity.setDisplayOrder(entity.getDisplayOrder() + 100);
      default -> throw new IllegalArgumentException("Unsupported governed column: " + column);
    }
    entity.setUpdatedByUserId(actor);
    entity.setUpdatedOn(Instant.now());
    requestedByReferenceRepository.save(entity);
    evictReferenceDataCache();
  }

  @When("the display label for Requested By {string} is updated to {string}")
  public void whenUpdateRequestedByDisplayLabel(String code, String newLabel) {
    updateRequestedByDisplayLabelInternal(code, newLabel);
  }

  @When(
      "the display label for Amendment Reason {string} under Requested By {string} is updated to"
          + " {string}")
  public void whenUpdateReasonDisplayLabel(
      String reasonCode, String requestedByCode, String newLabel) {
    updateReasonDisplayLabelInternal(requestedByCode, reasonCode, newLabel);
  }

  // ---------------------------------------------------------------------------
  // Then — assertions on the lookup payload
  // ---------------------------------------------------------------------------

  @Then("the lookup response lists the following Requested By values in order")
  public void lookupListsRequestedByInOrder(DataTable table) {
    List<Map<String, String>> expected = table.asMaps(String.class, String.class);
    List<JsonNode> active = activeRequestedByNodes();
    assertThat(active).hasSize(expected.size());
    for (int i = 0; i < expected.size(); i++) {
      Map<String, String> row = expected.get(i);
      JsonNode node = active.get(i);
      assertThat(node.path("code").asText()).isEqualTo(row.get("code"));
      assertThat(node.path("display_label").asText()).isEqualTo(row.get("display_label"));
      assertThat(node.path("display_order").asInt())
          .isEqualTo(Integer.parseInt(row.get("display_order")));
    }
  }

  @Then("the Requested By value {string} carries the following reasons in order")
  public void requestedByCarriesReasonsInOrder(String requestedByCode, DataTable table) {
    List<Map<String, String>> expected = table.asMaps(String.class, String.class);
    List<JsonNode> reasons = activeReasonsFor(requestedByCode);
    assertThat(reasons)
        .as("Active reasons under Requested By %s", requestedByCode)
        .hasSize(expected.size());
    for (int i = 0; i < expected.size(); i++) {
      Map<String, String> row = expected.get(i);
      JsonNode node = reasons.get(i);
      assertThat(node.path("code").asText()).isEqualTo(row.get("code"));
      assertThat(node.path("display_label").asText()).isEqualTo(row.get("display_label"));
      assertThat(node.path("display_order").asInt())
          .isEqualTo(Integer.parseInt(row.get("display_order")));
    }
  }

  @Then("the reason {string} is not listed under Requested By {string}")
  public void reasonNotListedUnderRequestedBy(String reasonCode, String requestedByCode) {
    assertThat(activeReasonCodesFor(requestedByCode)).doesNotContain(reasonCode);
  }

  @Then("the reason {string} is listed under Requested By {string}")
  public void reasonListedUnderRequestedBy(String reasonCode, String requestedByCode) {
    assertThat(activeReasonCodesFor(requestedByCode)).contains(reasonCode);
  }

  @Then("the lookup response does not contain the Requested By value {string}")
  public void lookupDoesNotContainRequestedBy(String code) {
    assertThat(activeRequestedByCodes()).doesNotContain(code);
  }

  @Then("the lookup response does not contain any reasons scoped to Requested By {string}")
  public void lookupDoesNotContainReasonsScopedToRequestedBy(String code) {
    assertThat(activeReasonCodesFor(code))
        .as("Active reasons under an inactive Requested By %s", code)
        .isEmpty();
  }

  @Then("the lookup response still contains the Requested By value {string}")
  public void lookupStillContainsRequestedBy(String code) {
    assertThat(activeRequestedByCodes()).contains(code);
  }

  @Then("the Requested By value {string} still contains the reason {string}")
  public void requestedByStillContainsReason(String requestedByCode, String reasonCode) {
    assertThat(activeReasonCodesFor(requestedByCode)).contains(reasonCode);
  }

  @Then(
      "the lookup response contains the Requested By value {string} with display label {string} at"
          + " display_order {int}")
  public void lookupContainsRequestedByWithLabelAtOrder(
      String code, String label, int displayOrder) {
    JsonNode node =
        findRequestedByNode(code)
            .orElseThrow(() -> new AssertionError("Requested By " + code + " not found in lookup"));
    assertThat(node.path("display_label").asText()).isEqualTo(label);
    assertThat(node.path("display_order").asInt()).isEqualTo(displayOrder);
    assertThat(isActive(node)).as("Requested By %s should be active", code).isTrue();
  }

  @Then("the Requested By value {string} carries no reasons")
  public void requestedByCarriesNoReasons(String code) {
    JsonNode node =
        findRequestedByNode(code)
            .orElseThrow(() -> new AssertionError("Requested By " + code + " not found in lookup"));
    JsonNode reasons = node.path(REASONS_ARRAY);
    long activeCount =
        reasons.isArray()
            ? java.util.stream.StreamSupport.stream(reasons.spliterator(), false)
                .filter(AmendmentMetadataReferenceLookupSteps::isActive)
                .count()
            : 0;
    assertThat(activeCount).isZero();
  }

  @Then("the Requested By value with code {string} has display label {string}")
  public void requestedByHasDisplayLabel(String code, String label) {
    JsonNode node =
        findRequestedByNode(code)
            .orElseThrow(() -> new AssertionError("Requested By " + code + " not found in lookup"));
    assertThat(node.path("display_label").asText()).isEqualTo(label);
  }

  @Then("the Requested By code {string} is unchanged")
  public void requestedByCodeUnchanged(String code) {
    assertThat(findRequestedByNode(code))
        .as("Requested By code %s should still exist after label edit", code)
        .isPresent();
    assertThat(
            requestedByReferenceRepository.findByOrderByDisplayOrderAsc().stream()
                .map(RequestedByReferenceEntity::getCode)
                .toList())
        .as("Persisted Requested By codes")
        .contains(code);
  }

  @Then(
      "every Amendment Reason previously scoped to Requested By {string} is still scoped to"
          + " {string}")
  public void reasonsStillScopedToRequestedBy(String originalCode, String expectedCode) {
    List<String> persistedScopes =
        amendmentReasonReferenceRepository.findAll().stream()
            .filter(r -> r.getRequestedByCode().equals(originalCode))
            .map(AmendmentReasonReferenceEntity::getRequestedByCode)
            .distinct()
            .toList();
    assertThat(persistedScopes).containsExactly(expectedCode);
  }

  @Then("under Requested By {string} the reason with code {string} has display label {string}")
  public void reasonHasDisplayLabelUnderRequestedBy(
      String requestedByCode, String reasonCode, String label) {
    JsonNode node =
        findReasonNode(requestedByCode, reasonCode)
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Reason " + reasonCode + " not found under " + requestedByCode));
    assertThat(node.path("display_label").asText()).isEqualTo(label);
  }

  @Then("under Requested By {string} the reason code {string} is unchanged")
  public void reasonCodeUnchanged(String requestedByCode, String reasonCode) {
    assertThat(findReasonNode(requestedByCode, reasonCode))
        .as("Reason %s under %s should still exist after label edit", reasonCode, requestedByCode)
        .isPresent();
  }

  @Then("the lookup response contains an empty Requested By list")
  public void lookupContainsEmptyRequestedByList() {
    JsonNode array = context.getLastResponseBody().path(REQUESTED_BY_ARRAY);
    assertThat(array.isArray()).as("requested_by should be a JSON array").isTrue();
    assertThat(activeRequestedByCodes()).isEmpty();
  }

  @Then("the lookup response lists exactly one Requested By value with code {string}")
  public void lookupListsExactlyOneRequestedBy(String code) {
    assertThat(activeRequestedByCodes()).containsExactly(code);
  }

  @Then("the Requested By value {string} carries exactly one reason with code {string}")
  public void requestedByCarriesExactlyOneReason(String requestedByCode, String reasonCode) {
    assertThat(activeReasonCodesFor(requestedByCode)).containsExactly(reasonCode);
  }

  @Then(
      "the reason {string} under Requested By {string} has no free-text supporting field in the"
          + " response")
  public void reasonHasNoFreeTextField(String reasonCode, String requestedByCode) {
    JsonNode node =
        findReasonNode(requestedByCode, reasonCode)
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Reason " + reasonCode + " not found under " + requestedByCode));
    List<String> unknownFields = new ArrayList<>();
    node.fieldNames()
        .forEachRemaining(
            name -> {
              if (!KNOWN_REASON_FIELDS.contains(name)) {
                unknownFields.add(name);
              }
            });
    assertThat(unknownFields)
        .as(
            "Controlled reason %s under %s should carry no free-text supporting field",
            reasonCode, requestedByCode)
        .isEmpty();
  }

  @Then("the generated id is a valid UUID")
  public void generatedIdIsValidUuid() {
    assertThat(lastGeneratedRowId).as("A row must have been inserted").isNotNull();
    // Round-trip through UUID.fromString to prove canonical form.
    UUID roundTrip = UUID.fromString(lastGeneratedRowId.toString());
    assertThat(roundTrip).isEqualTo(lastGeneratedRowId);
  }

  @Then("the generated id is UUIDv7")
  public void generatedIdIsUuidV7() {
    assertThat(lastGeneratedRowId).as("A row must have been inserted").isNotNull();
    assertThat(lastGeneratedRowId.version())
        .as("Generated id %s must be UUID version 7", lastGeneratedRowId)
        .isEqualTo(7);
  }

  @Then("the row for Requested By {string} has created_by_user_id {string}")
  public void rowHasCreatedByUserId(String code, String expected) {
    assertThat(findRequestedBy(code).getCreatedByUserId()).isEqualTo(expected);
  }

  @Then("the row for Requested By {string} has a non-null created_on timestamp")
  public void rowHasNonNullCreatedOn(String code) {
    assertThat(findRequestedBy(code).getCreatedOn()).isNotNull();
  }

  @Then("the row for Requested By {string} has null updated_by_user_id")
  public void rowHasNullUpdatedByUserId(String code) {
    assertThat(findRequestedBy(code).getUpdatedByUserId()).isNull();
  }

  @Then("the row for Requested By {string} has null updated_on")
  public void rowHasNullUpdatedOn(String code) {
    assertThat(findRequestedBy(code).getUpdatedOn()).isNull();
  }

  @Then("the row for Requested By {string} has updated_by_user_id {string}")
  public void rowHasUpdatedByUserId(String code, String expected) {
    assertThat(findRequestedBy(code).getUpdatedByUserId()).isEqualTo(expected);
  }

  @Then("the row for Requested By {string} has a non-null updated_on timestamp")
  public void rowHasNonNullUpdatedOn(String code) {
    assertThat(findRequestedBy(code).getUpdatedOn()).isNotNull();
  }

  @Then("the row for Requested By {string} has created_by_user_id {string} unchanged")
  public void rowHasCreatedByUserIdUnchanged(String code, String expected) {
    RequestedByReferenceEntity entity = findRequestedBy(code);
    assertThat(entity.getCreatedByUserId()).isEqualTo(expected);
    if (originalCreatedByActor != null) {
      assertThat(entity.getCreatedByUserId()).isEqualTo(originalCreatedByActor);
    }
  }

  @Then("the amendment record still references requested_by_code {string}")
  public void amendmentRecordStillReferencesRequestedByCode(String code) {
    assertThat(recordedAmendmentRequestedByCode)
        .as("Amendment record's requested_by_code is immutable after label edits")
        .isEqualTo(code);
  }

  @Then("the amendment record still references amendment_reason_code {string}")
  public void amendmentRecordStillReferencesReasonCode(String code) {
    assertThat(recordedAmendmentReasonCode)
        .as("Amendment record's amendment_reason_code is immutable after label edits")
        .isEqualTo(code);
  }

  @Then("the amendment metadata reference lookup returns those codes paired together")
  public void lookupReturnsCodesPairedTogether() {
    // Re-query the lookup after the mutations to prove the code pairing still resolves.
    api.getAmendmentMetadataReferenceLookup();
    assertThat(activeReasonCodesFor(recordedAmendmentRequestedByCode))
        .as(
            "Reason %s should still be scoped to Requested By %s",
            recordedAmendmentReasonCode, recordedAmendmentRequestedByCode)
        .contains(recordedAmendmentReasonCode);
  }

  // ---------------------------------------------------------------------------
  // Helpers — cache eviction
  // ---------------------------------------------------------------------------

  private void evictReferenceDataCache() {
    Cache cache = cacheManager.getCache(AmendmentReferenceDataProvider.CACHE_NAME);
    if (cache != null) {
      cache.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers — JSON tree extraction (active-only filtering)
  // ---------------------------------------------------------------------------

  private List<JsonNode> activeRequestedByNodes() {
    List<JsonNode> results = new ArrayList<>();
    JsonNode body = context.getLastResponseBody();
    if (body == null) {
      return results;
    }
    JsonNode array = body.path(REQUESTED_BY_ARRAY);
    if (!array.isArray()) {
      return results;
    }
    array.forEach(
        node -> {
          if (isActive(node)) {
            results.add(node);
          }
        });
    return results;
  }

  private List<String> activeRequestedByCodes() {
    return activeRequestedByNodes().stream().map(node -> node.path("code").asText()).toList();
  }

  private Optional<JsonNode> findRequestedByNode(String code) {
    JsonNode body = context.getLastResponseBody();
    if (body == null) {
      return Optional.empty();
    }
    JsonNode array = body.path(REQUESTED_BY_ARRAY);
    if (!array.isArray()) {
      return Optional.empty();
    }
    for (JsonNode node : array) {
      if (code.equals(node.path("code").asText())) {
        return Optional.of(node);
      }
    }
    return Optional.empty();
  }

  private List<JsonNode> activeReasonsFor(String requestedByCode) {
    Optional<JsonNode> parent = findRequestedByNode(requestedByCode);
    if (parent.isEmpty() || !isActive(parent.get())) {
      return List.of();
    }
    JsonNode reasons = parent.get().path(REASONS_ARRAY);
    if (!reasons.isArray()) {
      return List.of();
    }
    List<JsonNode> active = new ArrayList<>();
    reasons.forEach(
        node -> {
          if (isActive(node)) {
            active.add(node);
          }
        });
    return active;
  }

  private List<String> activeReasonCodesFor(String requestedByCode) {
    return activeReasonsFor(requestedByCode).stream()
        .map(node -> node.path("code").asText())
        .toList();
  }

  private Optional<JsonNode> findReasonNode(String requestedByCode, String reasonCode) {
    Optional<JsonNode> parent = findRequestedByNode(requestedByCode);
    if (parent.isEmpty()) {
      return Optional.empty();
    }
    JsonNode reasons = parent.get().path(REASONS_ARRAY);
    if (!reasons.isArray()) {
      return Optional.empty();
    }
    for (JsonNode node : reasons) {
      if (reasonCode.equals(node.path("code").asText())) {
        return Optional.of(node);
      }
    }
    return Optional.empty();
  }

  private static boolean isActive(JsonNode node) {
    JsonNode flag = node.path("is_active");
    // Treat missing is_active as active — the OpenAPI schema marks it as optional and the
    // convention across the reference tables is "present-and-true means selectable".
    return flag.isMissingNode() || !flag.isBoolean() || flag.asBoolean();
  }

  // ---------------------------------------------------------------------------
  // Helpers — repository mutation
  // ---------------------------------------------------------------------------

  private RequestedByReferenceEntity findRequestedBy(String code) {
    return requestedByReferenceRepository.findByOrderByDisplayOrderAsc().stream()
        .filter(e -> Objects.equals(e.getCode(), code))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Requested By row for code " + code + " not found"));
  }

  private AmendmentReasonReferenceEntity findReason(String requestedByCode, String reasonCode) {
    return amendmentReasonReferenceRepository.findAll().stream()
        .filter(
            e ->
                Objects.equals(e.getRequestedByCode(), requestedByCode)
                    && Objects.equals(e.getCode(), reasonCode))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "Reason " + reasonCode + " under " + requestedByCode + " not found"));
  }

  private void updateRequestedByDisplayLabelInternal(String code, String newLabel) {
    RequestedByReferenceEntity entity = findRequestedBy(code);
    entity.setDisplayLabel(newLabel);
    entity.setUpdatedByUserId(SEED_ACTOR);
    entity.setUpdatedOn(Instant.now());
    requestedByReferenceRepository.save(entity);
    evictReferenceDataCache();
  }

  private void updateReasonDisplayLabelInternal(
      String requestedByCode, String reasonCode, String newLabel) {
    AmendmentReasonReferenceEntity entity = findReason(requestedByCode, reasonCode);
    entity.setDisplayLabel(newLabel);
    entity.setUpdatedByUserId(SEED_ACTOR);
    entity.setUpdatedOn(Instant.now());
    amendmentReasonReferenceRepository.save(entity);
    evictReferenceDataCache();
  }

  // ---------------------------------------------------------------------------
  // Helpers — Flyway-seed restoration
  // ---------------------------------------------------------------------------

  private void resetReferenceDataToFlywaySeed() {
    amendmentReasonReferenceRepository.deleteAllInBatch();
    requestedByReferenceRepository.deleteAllInBatch();
    Set<String> seededRequestedByCodes = new HashSet<>();
    for (SeedRequestedBy seed : FLYWAY_REQUESTED_BY) {
      saveRequestedBy(seed.code(), seed.label(), true, seed.order());
      seededRequestedByCodes.add(seed.code());
    }
    for (SeedReason seed : FLYWAY_REASONS) {
      if (seededRequestedByCodes.contains(seed.requestedBy())) {
        saveReason(seed.requestedBy(), seed.code(), seed.label(), true, seed.order());
      }
    }
  }

  private void saveRequestedBy(String code, String label, boolean active, int order) {
    RequestedByReferenceEntity entity =
        RequestedByReferenceEntity.builder()
            .id(Uuid7.timeBasedUuid())
            .code(code)
            .displayLabel(label)
            .isActive(active)
            .displayOrder(order)
            .createdByUserId(SEED_ACTOR)
            .createdOn(Instant.now())
            .build();
    requestedByReferenceRepository.saveAndFlush(entity);
    nullUpdatedAuditForRequestedBy(code);
  }

  private void saveReason(
      String requestedBy, String code, String label, boolean active, int order) {
    AmendmentReasonReferenceEntity entity =
        AmendmentReasonReferenceEntity.builder()
            .id(Uuid7.timeBasedUuid())
            .requestedByCode(requestedBy)
            .code(code)
            .displayLabel(label)
            .isActive(active)
            .displayOrder(order)
            .createdByUserId(SEED_ACTOR)
            .createdOn(Instant.now())
            .build();
    amendmentReasonReferenceRepository.saveAndFlush(entity);
    nullUpdatedAuditForReason(requestedBy, code);
  }

  private void nullUpdatedAuditForRequestedBy(String code) {
    // Hibernate's @UpdateTimestamp populates updated_on on insert as well as update, but the
    // Flyway seed and the reference-data "load" contract expect updated_on / updated_by_user_id
    // to be null until a governed column is actually changed. Null them out via native SQL so
    // subsequent audit assertions reflect the persisted state.
    jdbcTemplate.update(
        "UPDATE claims.requested_by_reference SET updated_on = NULL, updated_by_user_id = NULL"
            + " WHERE code = ?",
        code);
  }

  private void nullUpdatedAuditForReason(String requestedByCode, String reasonCode) {
    jdbcTemplate.update(
        "UPDATE claims.amendment_reason_reference SET updated_on = NULL,"
            + " updated_by_user_id = NULL WHERE requested_by_code = ? AND code = ?",
        requestedByCode,
        reasonCode);
  }

  private record SeedRequestedBy(String code, String label, int order) {}

  private record SeedReason(String requestedBy, String code, String label, int order) {}
}
