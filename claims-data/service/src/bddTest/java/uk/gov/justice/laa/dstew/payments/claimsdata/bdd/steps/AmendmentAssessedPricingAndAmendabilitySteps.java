package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.isUatMode;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.AmendableClaimFixture;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClientFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendableClaimFields;

/**
 * Step definitions for {@code amendmentsAssessedPricingAndAmendability.feature} (DSTEW-1767).
 *
 * <p>Backs the two amendment-specific gates that do not need fee-code detail lookup:
 *
 * <ul>
 *   <li><b>Field amendability</b> — {@code FieldAmendabilityValidationStep} rejects a
 *       provider-requested change to a field not amendable for the claim's {@link AreaOfLaw}
 *       (registry: {@link AmendableClaimFields}) with {@code
 *       INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW}.
 *   <li><b>Assessed-claim pricing</b> — {@code AssessedClaimPricingValidationStep} rejects a
 *       pricing-impacting change on an already-assessed claim with {@code
 *       INVALID_PRICING_AMENDMENT_ON_ASSESSED_CLAIM} before any FSP call.
 * </ul>
 *
 * <p>Provisioning + the shared {@code When I submit ...} step reuse the DSTEW-2301 harness: {@link
 * AmendableClaimFixture} seeds a fully-valid amendable claim graph, and {@link
 * SharedAmendmentPatchContext} carries the submission / claim / patch json to the shared submit
 * step owned by {@link AmendmentMetadataValidationSteps}.
 *
 * <p><b>Local vs UAT.</b> Following the established amendment-harness convention, code-level
 * assertions on aggregated / field-level messages are strict in UAT mode and best-effort (assert
 * the endpoint produced a ProblemDetail rejection, then log the codes) in local mode, where a
 * minimally-seeded claim may short-circuit on an earlier gate. The deterministic registry checks
 * ({@code is/NOT on the AaBC amendable-fields list}) and the two single-code pricing assertions are
 * strict in both modes.
 *
 * <p>The {@code classifier will mark ...} step is a spec-guard: real pricing impact is derived from
 * the changed fields ({@code FeeSchemeRequestField.impactsPricing}), so the scenario changes a
 * genuinely pricing-impacting field ({@code fee_code}) for "true" and a non-pricing field for
 * "false" and the real gate produces the intended outcome. It records intent explicitly so the
 * scenario wiring stays honest rather than silently passing. DS1767_3 asserts only the two {@code
 * INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW} codes the production validators genuinely produce —
 * no fabricated cross-source message is injected (an earlier {@code INVALID_FIELD_VALUE}
 * expectation and its log-only spec-guard were removed as a false-green; see PR #478 Copilot
 * review).
 */
@Slf4j
public class AmendmentAssessedPricingAndAmendabilitySteps {

  private static final String FIELD_NOT_AMENDABLE_CODE =
      "INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW";

  // Valid amendment metadata so AmendmentReferenceValidationStep / AmendmentUserIdValidationStep do
  // not add unrelated metadata errors — every patch these scenarios build must carry it, otherwise
  // the intended field-amendability / assessed-pricing gate is not isolated and the FSP call is
  // skipped because an unrelated error already exists (PR #478 review). Mirrors the DSTEW-2301
  // harness values.
  private static final String AMENDMENT_USER_ID = "0190b6a0-9b7e-7c8a-9e2d-230100000001";

  @Autowired private AmendableClaimFixture fixture;
  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private BddScenarioContext scenarioContext;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // ---------------------------------------------------------------------------
  // Given — provisioning
  // ---------------------------------------------------------------------------

  /**
   * Seeds a fully-valid amendable Legal Help claim graph (Submission + Claim + ClaimSummaryFee +
   * baseline CalculatedFeeDetail + Client + ClaimCase) via {@link AmendableClaimFixture} and
   * records the pre-amendment baseline (claim version + CFD row count) onto {@link
   * SharedAmendmentPatchContext}. This is what the assessed-pricing scenarios (DS1767_4/_5/_6) rely
   * on so the harness's baseline-relative Thens — {@code no FSP-derived calculated_fee_detail row
   * was inserted} and {@code the claim persisted state matches the pre-amendment state} — compare
   * against a real captured baseline rather than a zero/absent default.
   *
   * <p>Deliberately distinct from the DSTEW-1757 {@code an original claim exists with a valid
   * pricing baseline} phrase (owned by {@code AmendmentFspPricingRuleSteps}), which seeds a thinner
   * graph for classifier spec-guards and records no baseline.
   */
  @Given("an original amendable claim exists with a valid pricing baseline")
  public void anOriginalAmendableClaimExistsWithValidPricingBaseline() {
    step(
        "Seeding a fully-valid amendable claim and recording its pre-amendment baseline",
        () -> {
          AmendableClaimFixture.Seeded seeded = fixture.legalHelpValid().seed();
          sharedPatchContext.setSubmissionId(seeded.submissionId());
          sharedPatchContext.setClaimId(seeded.claimId());
          sharedPatchContext.setBaselineClaimVersion(seeded.baselineVersion());
          sharedPatchContext.setBaselineCfdCount(countCfd(seeded.claimId()));
          log.info(
              "[DSTEW-1767] Seeded amendable claim {} on submission {} (baseline version={},"
                  + " cfdCount={})",
              seeded.claimId(),
              seeded.submissionId(),
              sharedPatchContext.getBaselineClaimVersion(),
              sharedPatchContext.getBaselineCfdCount());
        });
  }

  /** Seeds a fully-valid amendable claim graph for the given area of law. */
  @Given("an original claim exists with area of law {string}")
  public void anOriginalClaimExistsWithAreaOfLaw(String areaOfLaw) {
    step(
        "Seeding an amendable claim with area of law " + areaOfLaw,
        () -> {
          AreaOfLaw area = AreaOfLaw.valueOf(areaOfLaw);
          AmendableClaimFixture.Seeded seeded = fixture.legalHelpValid().withAreaOfLaw(area).seed();
          sharedPatchContext.setSubmissionId(seeded.submissionId());
          sharedPatchContext.setClaimId(seeded.claimId());
          log.info(
              "[DSTEW-1767] Seeded {} claim {} on submission {}",
              area,
              seeded.claimId(),
              seeded.submissionId());
        });
  }

  // ---------------------------------------------------------------------------
  // Given — AaBC amendable-fields registry premise (deterministic, strict)
  // ---------------------------------------------------------------------------

  /** Asserts the field is amendable for the given area of law per the current registry. */
  @Given("the field {string} is on the AaBC amendable-fields list for area of law {string}")
  public void fieldIsOnAmendableList(String field, String areaOfLaw) {
    step(
        "Asserting " + field + " IS amendable for " + areaOfLaw,
        () ->
            assertThat(
                    AmendableClaimFields.isAmendable(diffId(field), AreaOfLaw.valueOf(areaOfLaw)))
                .as("%s must be amendable for %s per the current AaBC registry", field, areaOfLaw)
                .isTrue());
  }

  @Given("the field {string} is NOT on the AaBC amendable-fields list for area of law {string}")
  public void fieldIsNotOnAmendableList(String field, String areaOfLaw) {
    step(
        "Asserting " + field + " is NOT amendable for " + areaOfLaw,
        () ->
            assertThat(
                    AmendableClaimFields.isAmendable(diffId(field), AreaOfLaw.valueOf(areaOfLaw)))
                .as(
                    "%s must NOT be amendable for %s per the current AaBC registry",
                    field, areaOfLaw)
                .isFalse());
  }

  @Given("the field {string} is on the AaBC amendable-fields list for the claim's area of law")
  public void fieldIsOnAmendableListForClaimArea(String field) {
    step(
        "Asserting " + field + " IS amendable for the seeded claim's area of law",
        () -> {
          AreaOfLaw area = resolveClaimAreaOfLaw();
          assertThat(AmendableClaimFields.isAmendable(diffId(field), area))
              .as("%s must be amendable for %s per the current AaBC registry", field, area)
              .isTrue();
        });
  }

  @Given(
      "the fields {string} and {string} are NOT on the AaBC amendable-fields list for area of law"
          + " {string}")
  public void fieldsAreNotOnAmendableList(String fieldA, String fieldB, String areaOfLaw) {
    step(
        "Asserting " + fieldA + " and " + fieldB + " are NOT amendable for " + areaOfLaw,
        () -> {
          AreaOfLaw area = AreaOfLaw.valueOf(areaOfLaw);
          assertThat(AmendableClaimFields.isAmendable(diffId(fieldA), area))
              .as("%s must NOT be amendable for %s", fieldA, areaOfLaw)
              .isFalse();
          assertThat(AmendableClaimFields.isAmendable(diffId(fieldB), area))
              .as("%s must NOT be amendable for %s", fieldB, areaOfLaw)
              .isFalse();
        });
  }

  // ---------------------------------------------------------------------------
  // Given — assessment state
  // ---------------------------------------------------------------------------

  @Given("the claim already has an assessment recorded")
  public void theClaimAlreadyHasAnAssessmentRecorded() {
    step(
        "Marking the seeded claim as assessed (has_assessment = true)",
        () -> {
          UUID claimId = requireClaimId();
          // Update via JDBC rather than a JPA save so the @Version column is NOT bumped — the
          // amendment version gate must still match the patch's version:0.
          int updated =
              jdbcTemplate.update(
                  "UPDATE claims.claim SET has_assessment = true WHERE id = ?", claimId);
          assertThat(updated)
              .as("assessment update should touch exactly one claim row for %s", claimId)
              .isEqualTo(1);
        });
  }

  @Given("the claim has no assessment recorded")
  public void theClaimHasNoAssessmentRecorded() {
    step(
        "Asserting the seeded claim is unassessed (has_assessment = false)",
        () -> {
          UUID claimId = requireClaimId();
          Boolean assessed =
              jdbcTemplate.queryForObject(
                  "SELECT has_assessment FROM claims.claim WHERE id = ?", Boolean.class, claimId);
          assertThat(assessed)
              .as("seeded claim %s must be unassessed for this scenario", claimId)
              .isFalse();
        });
  }

  // ---------------------------------------------------------------------------
  // Given — classifier / cross-source spec-guards
  // ---------------------------------------------------------------------------

  @Given("the classifier will mark the amendment {string} as {string}")
  public void theClassifierWillMarkTheAmendment(String flag, String value) {
    // Spec-guard: pricing impact is not a mock — it is derived from which fields the amendment
    // actually changes (FeeSchemeRequestField.impactsPricing). The scenario changes a genuinely
    // pricing-impacting field (fee_code) for "true" and a non-pricing field for "false", so the
    // real gate produces the intended outcome. This records the intent for traceability.
    log.info(
        "[spec-guard][DSTEW-1767] classifier {} intended = {} (derived from changed fields)",
        flag,
        value);
  }

  // ---------------------------------------------------------------------------
  // Given — amendment mutation
  // ---------------------------------------------------------------------------

  @Given("an amendment changes only the field {string} to {string}")
  public void anAmendmentChangesOnlyFieldTo(String field, String value) {
    step(
        "Building a patch that changes " + field + " to " + value,
        () -> {
          ObjectNode root = objectMapper.createObjectNode();
          root.put("ufn".equals(field) ? "unique_file_number" : field, value);
          root.put("version", 0);
          putValidAmendmentMetadata(root);
          sharedPatchContext.setPatchJson(root.toString());
        });
  }

  // ---------------------------------------------------------------------------
  // Then — outcome assertions
  // ---------------------------------------------------------------------------

  @Then("no field-amendability error is raised for the field {string}")
  public void noFieldAmendabilityErrorIsRaised(String field) {
    step(
        "Asserting no field-amendability error for " + field,
        () ->
            assertThat(extractErrorCodes())
                .as(
                    "no %s should be present for amendable field %s",
                    FIELD_NOT_AMENDABLE_CODE, field)
                .doesNotContain(FIELD_NOT_AMENDABLE_CODE));
  }

  @Then("the amendment is rejected with the following field-level errors")
  public void theAmendmentIsRejectedWithFieldLevelErrors(DataTable table) {
    step(
        "Asserting field-level rejection errors",
        () -> {
          Integer status = scenarioContext.getLastStatusCode();
          assertThat(status).as("expected an HTTP response").isNotNull();
          assertThat(status)
              .as("field-level rejection must be a 4xx (was %s)", status)
              .isBetween(400, 499);
          List<Map<String, String>> rows = table.asMaps(String.class, String.class);
          // Each table row is a (field, code) expectation. The `field` column is part of the
          // contract: the code must be attributed to that field, and repeated codes must appear
          // with matching multiplicity — a plain containsAll would let one error masquerade as two,
          // or accept a code attributed to the wrong field. INVALID_FIELD_NOT_AMENDABLE_* carries a
          // null fieldName in the production catalogue and names the offending field in the message
          // ("Field '<field>' is not amendable ..."), so field-association is verified against the
          // message; codes that populate fieldName are matched on that too.
          List<ErrorEntry> actual = extractErrorEntries();
          if (isUatMode()) {
            for (Map<String, String> row : rows) {
              String field = row.get("field");
              String code = row.get("Error Code");
              long expectedCount =
                  rows.stream()
                      .filter(r -> code.equals(r.get("Error Code")) && field.equals(r.get("field")))
                      .count();
              long actualCount = actual.stream().filter(e -> e.matches(code, field)).count();
              assertThat(actualCount)
                  .as(
                      "response must report code %s attributed to field %s at least %s time(s)"
                          + " (saw %s); actual errors=%s",
                      code, field, expectedCount, actualCount, actual)
                  .isGreaterThanOrEqualTo(expectedCount);
            }
          } else {
            log.info("[local mode] field-level rejection — expected {}, saw {}", rows, actual);
          }
        });
  }

  @Then("no amendment-related event was published for this attempt")
  public void noAmendmentRelatedEventWasPublished() {
    step(
        "Asserting no amendment event was published — verified against persisted amendment state",
        () -> {
          // A rejected amendment commits no claim_amendment row, and the AMENDMENT timeline event
          // is
          // derived from that persisted row — so zero rows proves no event could have been
          // published. We assert the real DB state rather than a status-only proxy that would pass
          // even if an event were emitted. The rejection status is also asserted so the reason for
          // "no event" (rejection, not a silent success) stays explicit.
          Integer status = scenarioContext.getLastStatusCode();
          assertThat(status).as("expected an HTTP response").isNotNull();
          assertThat(status)
              .as("a rejected amendment (>=400) is the precondition for no event (was %s)", status)
              .isGreaterThanOrEqualTo(400);
          UUID claimId = requireClaimId();
          Long amendmentRows =
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM claims.claim_amendment WHERE claim_id = ?",
                  Long.class,
                  claimId);
          assertThat(amendmentRows)
              .as(
                  "no claim_amendment row must exist for claim %s — the AMENDMENT event is derived"
                      + " from it, so zero rows proves no event was published",
                  claimId)
              .isZero();
        });
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private UUID requireClaimId() {
    UUID claimId = sharedPatchContext.getClaimId();
    assertThat(claimId).as("a claim must have been seeded before this step").isNotNull();
    return claimId;
  }

  /**
   * Adds the mandatory, valid amendment metadata every well-formed patch must carry so the metadata
   * validation steps do not raise unrelated errors that would mask the gate under test.
   */
  private static void putValidAmendmentMetadata(ObjectNode root) {
    root.put("amendment_requested_by", "PROVIDER");
    root.put("amendment_reason_code", "PROVIDER_ERROR");
    root.put("amendment_user_id", AMENDMENT_USER_ID);
  }

  /** Counts the {@code calculated_fee_detail} rows currently bound to the given claim. */
  private long countCfd(UUID claimId) {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM claims.calculated_fee_detail WHERE claim_id = ?",
            Long.class,
            claimId);
    return count == null ? 0L : count;
  }

  /**
   * Resolves the seeded claim's area of law. Reads it from the DB (claim → submission) so it works
   * whether the claim was provisioned by this class's area-of-law step or by a reused provisioning
   * step (e.g. the DSTEW-1757 "valid pricing baseline" step, which seeds LEGAL_HELP).
   */
  private AreaOfLaw resolveClaimAreaOfLaw() {
    UUID claimId = requireClaimId();
    String area =
        jdbcTemplate.queryForObject(
            "SELECT s.area_of_law FROM claims.claim c"
                + " JOIN claims.submission s ON c.submission_id = s.id"
                + " WHERE c.id = ?",
            String.class,
            claimId);
    assertThat(area).as("could not resolve area of law for claim %s", claimId).isNotNull();
    return AreaOfLaw.valueOf(area);
  }

  private List<String> extractErrorCodes() {
    JsonNode body = scenarioContext.getLastResponseBody();
    List<String> codes = new ArrayList<>();
    if (body == null) {
      return codes;
    }
    JsonNode errors = body.path("errors");
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

  /**
   * A single error entry from the response {@code errors} array, carrying its {@code code}, its
   * (possibly {@code null}) {@code fieldName} and its user-facing {@code message}.
   * Field-association is checked against both: some codes populate {@code fieldName}, while others
   * (e.g. {@code INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW}) name the offending field only in the
   * message.
   */
  private record ErrorEntry(String code, String fieldName, String message) {
    boolean matches(String expectedCode, String expectedField) {
      if (!expectedCode.equals(code)) {
        return false;
      }
      String normalisedField = normalise(expectedField);
      return normalisedField.equals(normalise(fieldName))
          || normalise(message).contains(normalisedField);
    }

    private static String normalise(String value) {
      return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
  }

  private List<ErrorEntry> extractErrorEntries() {
    JsonNode body = scenarioContext.getLastResponseBody();
    List<ErrorEntry> entries = new ArrayList<>();
    if (body == null) {
      return entries;
    }
    JsonNode errors = body.path("errors");
    if (errors.isArray()) {
      errors.forEach(
          node -> {
            String code = node.path("code").asText(null);
            if (code != null) {
              entries.add(
                  new ErrorEntry(
                      code, node.path("fieldName").asText(null), node.path("message").asText("")));
            }
          });
    }
    return entries;
  }

  /** Maps a feature-file wire field name to its amendment diff identifier. */
  private static String diffId(String wireField) {
    return switch (wireField) {
      case "fee_code" -> ClaimFields.FEE_CODE;
      case "case_start_date" -> ClaimFields.CASE_START_DATE;
      case "case_concluded_date" -> ClaimFields.CASE_CONCLUDED_DATE;
      case "representation_order_date" -> ClaimFields.REPRESENTATION_ORDER_DATE;
      case "ufn", "unique_file_number" -> ClaimFields.UNIQUE_FILE_NUMBER;
      case "client_surname" -> ClientFields.CLIENT_SURNAME;
      case "client_forename" -> ClientFields.CLIENT_FORENAME;
      default ->
          throw new IllegalArgumentException(
              "No diff-identifier mapping for wire field '" + wireField + "'");
    };
  }
}
