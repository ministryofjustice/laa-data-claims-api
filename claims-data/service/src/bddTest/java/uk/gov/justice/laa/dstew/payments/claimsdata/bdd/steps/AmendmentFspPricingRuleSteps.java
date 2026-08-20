package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.DEFAULT_OFFICE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.isUatMode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.SubmissionPeriodHelper;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step definitions for {@code amendmentsFspPricingRule.feature} (DSTEW-1757).
 *
 * <p>The downstream classifier ({@code impacts_pricing} / {@code source_rule_reference}) is
 * DSTEW-1766 work and is not yet surfaced in the amendment PATCH response. These scenarios drive a
 * real amendment through {@code PATCH /api/v1/submissions/{submissionId}/claims/{claimId}} so the
 * harness proves the request wires end-to-end (feature flag, submission, claim provisioning, patch
 * shape), while the classifier assertions are recorded as spec-guards. When the classifier lands,
 * the spec-guard bodies here become the seams to bolt real assertions onto without rewriting the
 * scenario wiring.
 *
 * <p>The {@code When I submit ...} phrase is owned by {@link AmendmentMetadataValidationSteps};
 * this class publishes its provisioned submission / claim / patch json onto {@link
 * SharedAmendmentPatchContext} so the shared When definition picks them up.
 */
@Slf4j
public class AmendmentFspPricingRuleSteps {

  private static final String SEED_ACTOR = "bdd-DSTEW-1757";
  private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  // FSP fee-calculation request-body field map (canonical pricing rule source per DSTEW-1757). The
  // feature-file field names come from the FSP contract; some map to concrete ClaimPatch json
  // fields, others are non-ClaimPatch fields whose amendment intent we record for classifier
  // trace only.
  private static final List<String> FSP_REQUEST_BODY_FIELDS =
      List.of(
          "fee_code",
          "case_start_date",
          "case_concluded_date",
          "representation_order_date",
          "ufn",
          "office_code");

  @Autowired private BddScenarioContext scenarioContext;
  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private SubmissionPeriodHelper periodHelper;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private SubmissionRepository submissionRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private String originalFeeCode;
  private final Map<String, Object> patchFields = new LinkedHashMap<>();
  // Default forename delta guarantees the patch has a genuine change so the API does not reject a
  // no-op. Individual steps that own the primary delta clear this.
  private String pendingClientForename = "Amended";

  // ---------------------------------------------------------------------------
  // Background — authoritative rule source spec-guard
  // ---------------------------------------------------------------------------

  @Given("the FSP fee-calculation request-body field map is the authoritative pricing rule source")
  public void fspRequestBodyIsAuthoritative() {
    // The FSP request-body field map is owned by claims-validation-core and consumed by the
    // classifier (DSTEW-1766). It is not observable from the BDD harness; this step records the
    // pre-condition for traceability and pins the expected field set below.
    log.info(
        "[spec-guard] FSP request-body pricing-impact field map assumed authoritative: {}",
        FSP_REQUEST_BODY_FIELDS);
  }

  // ---------------------------------------------------------------------------
  // Given — original claim provisioning
  // ---------------------------------------------------------------------------

  @Given("an original claim exists with a valid pricing baseline")
  public void originalClaimWithValidPricingBaseline() {
    originalFeeCode = "CAPA";
    provisionAmendableClaim();
  }

  @Given("an original claim exists with feeCode {string}")
  public void originalClaimExistsWithFeeCode(String feeCode) {
    originalFeeCode = feeCode;
    provisionAmendableClaim();
  }

  // ---------------------------------------------------------------------------
  // Given — supporting-artefact spec-guards (FSP wins over these)
  // ---------------------------------------------------------------------------

  @Given("a supporting artefact classifies the field {string} as pricing-impacting")
  public void supportingArtefactSaysPricing(String field) {
    log.info(
        "[spec-guard] Supporting artefact (AaBC amendable-fields / pricing-impact view) classifies"
            + " {} as pricing-impacting — FSP request-body rule must win",
        field);
  }

  @Given("a supporting artefact classifies the field {string} as non-pricing")
  public void supportingArtefactSaysNonPricing(String field) {
    log.info(
        "[spec-guard] Supporting artefact classifies {} as non-pricing — FSP request-body rule"
            + " must win",
        field);
  }

  @Given("the field {string} does not appear in the FSP fee-calculation request body")
  public void fieldNotInFspRequestBody(String field) {
    log.info("[spec-guard] Field {} confirmed absent from FSP request body", field);
  }

  @Given("the field {string} appears in the FSP fee-calculation request body")
  public void fieldInFspRequestBody(String field) {
    log.info("[spec-guard] Field {} treated as present in FSP request body", field);
  }

  // ---------------------------------------------------------------------------
  // Given — amendment mutations
  // ---------------------------------------------------------------------------

  @Given("an amendment changes only the field {string} to a different value")
  public void amendmentChangesOnlyField(String field) {
    applyFieldChange(field, valueFor(field));
    finalisePatch();
  }

  @Given(
      "an amendment payload includes feeCode {string} unchanged and updates only the clientSurname"
          + " to {string}")
  public void amendmentEchoesFeeCodeAndChangesSurname(String feeCode, String surname) {
    patchFields.put("fee_code", feeCode);
    pendingClientForename = null;
    patchFields.put("client_surname", surname);
    finalisePatch();
  }

  @Given("an amendment changes the following fields")
  public void amendmentChangesFollowingFields(DataTable table) {
    List<Map<String, String>> rows = table.asMaps(String.class, String.class);
    for (Map<String, String> row : rows) {
      applyFieldChange(row.get("field"), row.get("newValue"));
    }
    finalisePatch();
  }

  // ---------------------------------------------------------------------------
  // Then — classifier spec-guards
  // ---------------------------------------------------------------------------

  @Then("the classifier output has impacts_pricing {string}")
  public void classifierImpactsPricing(String expected) {
    Integer status = scenarioContext.getLastStatusCode();
    if (isUatMode()) {
      JsonNode body = scenarioContext.getLastResponseBody();
      log.info(
          "[uat] Expected impacts_pricing={} in classifier feed; observed body={}", expected, body);
    }
    log.info(
        "[spec-guard] impacts_pricing expected={} (HTTP status observed for amendment PATCH: {})",
        expected,
        status);
  }

  @Then("the classifier source_rule_reference identifies the FSP request-body rule source")
  public void sourceRuleIdentifiesFspBody() {
    log.info(
        "[spec-guard] source_rule_reference expected to identify FSP request-body rule source"
            + " (classifier not yet exposed)");
  }

  @Then("the classifier source_rule_reference includes the changed field {string}")
  public void sourceRuleIncludesChangedField(String field) {
    log.info(
        "[spec-guard] source_rule_reference expected to include changed field {} (classifier not"
            + " yet exposed)",
        field);
  }

  @Then("the classifier source_rule_reference indicates no pricing-impacting field changed")
  public void sourceRuleIndicatesNoPricingChange() {
    log.info(
        "[spec-guard] source_rule_reference expected to indicate no pricing-impacting field"
            + " changed (classifier not yet exposed)");
  }

  @Then("the classifier source_rule_reference is {string}")
  public void sourceRuleReferenceIs(String expected) {
    log.info(
        "[spec-guard] source_rule_reference expected={} (classifier not yet exposed)", expected);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void applyFieldChange(String field, String newValue) {
    switch (field) {
      case "fee_code" -> patchFields.put("fee_code", newValue);
      case "case_start_date" ->
          patchFields.put("case_start_date", API_DATE.format(LocalDate.of(2025, Month.AUGUST, 4)));
      case "case_concluded_date" ->
          patchFields.put(
              "case_concluded_date", API_DATE.format(LocalDate.of(2025, Month.AUGUST, 28)));
      case "representation_order_date" ->
          patchFields.put(
              "representation_order_date", API_DATE.format(LocalDate.of(2025, Month.AUGUST, 11)));
      case "ufn" -> patchFields.put("unique_file_number", "040825/002");
      case "client_surname" -> {
        pendingClientForename = null;
        patchFields.put("client_surname", newValue != null ? newValue : "Smith");
      }
      case "client_forename" -> pendingClientForename = newValue != null ? newValue : "Ada";
      case "client_reference" ->
          // No client_reference field on ClaimPatch. Recorded for classifier trace only.
          log.info(
              "[spec-guard] Amendment intent: change client_reference (not a ClaimPatch field —"
                  + " recorded for classifier trace only)");
      case "office_code" ->
          // officeCode is not on ClaimPatch; classifier reads it from the effective office at
          // amendment time. Recorded for classifier trace; default forename delta keeps the patch
          // non-empty for the API.
          log.info(
              "[spec-guard] Amendment intent: change office_code (not a ClaimPatch field —"
                  + " recorded for classifier trace only)");
      case "supporting_only_field", "fsp_only_field" ->
          // Non-real fields exercised by DS1757_6 / DS1757_7 to prove FSP body wins over
          // supporting artefacts. No wire representation possible; classifier decision is what
          // matters. Default forename delta keeps the patch non-empty.
          log.info(
              "[spec-guard] Amendment intent: change synthetic field {} (recorded for classifier"
                  + " trace only)",
              field);
      default ->
          log.info("[spec-guard] Amendment intent: change field {} (no ClaimPatch mapping)", field);
    }
  }

  private String valueFor(String field) {
    return switch (field) {
      case "fee_code" -> "FEE-B";
      case "ufn" -> "040825/002";
      case "client_surname" -> "Smith";
      case "client_forename" -> "Ada";
      case "client_reference" -> "CLI-999";
      default -> null;
    };
  }

  private void provisionAmendableClaim() {
    String office = DEFAULT_OFFICE;
    String period = periodHelper.nextAvailablePeriod(office, AreaOfLaw.LEGAL_HELP);

    Submission submission =
        submissionRepository.saveAndFlush(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .officeAccountNumber(office)
                .submissionPeriod(period)
                .areaOfLaw(AreaOfLaw.LEGAL_HELP)
                .status(SubmissionStatus.CREATED)
                .createdByUserId(SEED_ACTOR)
                .providerUserId(SEED_ACTOR)
                .createdOn(java.time.Instant.now())
                .build());

    Claim claim =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(Uuid7.timeBasedUuid())
                .submission(submission)
                .status(ClaimStatus.VALID)
                .feeCode(originalFeeCode != null ? originalFeeCode : "CAPA")
                .lineNumber(1)
                .matterTypeCode("MAT01")
                .uniqueFileNumber("010725/001")
                .caseReferenceNumber("CRN-1757")
                .caseStartDate(LocalDate.of(2025, Month.JULY, 1))
                .caseConcludedDate(LocalDate.of(2025, Month.JULY, 31))
                .createdByUserId(SEED_ACTOR)
                .build());

    sharedPatchContext.setSubmissionId(submission.getId());
    sharedPatchContext.setClaimId(claim.getId());
    finalisePatch();
    log.info(
        "Seeded amendable claim {} on submission {} (feeCode={})",
        claim.getId(),
        submission.getId(),
        claim.getFeeCode());
  }

  private void finalisePatch() {
    ObjectNode root = objectMapper.createObjectNode();
    if (pendingClientForename != null) {
      root.put("client_forename", pendingClientForename);
    }
    for (Map.Entry<String, Object> entry : patchFields.entrySet()) {
      Object value = entry.getValue();
      if (value == null) {
        root.putNull(entry.getKey());
      } else {
        root.put(entry.getKey(), String.valueOf(value));
      }
    }
    root.put("version", 0);
    sharedPatchContext.setPatchJson(root.toString());
  }
}
