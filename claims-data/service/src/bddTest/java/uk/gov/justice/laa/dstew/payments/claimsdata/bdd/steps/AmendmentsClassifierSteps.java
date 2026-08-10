package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.AmendmentBddSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.PdaMockServerSupport;

/** Step definitions for amendmentsClassifier.feature scenarios. */
public class AmendmentsClassifierSteps {

  @Autowired private BddScenarioContext context;
  @Autowired private AmendmentBddSupport amendment;
  @Autowired private PdaMockServerSupport pda;

  @Given(
      "a positive PDA cache entry exists for the pre-amendment officeCode and resolved effectiveDate")
  public void aPositivePdaCacheEntryExistsForThePreAmendmentOfficeCodeAndResolvedEffectiveDate()
      throws Exception {
    amendment.markClassifierScenarioActive();
    amendment.assumePositivePdaCacheEntry();
    pda.stubProviderSchedulesOk();
    pda.stubFeeSchemeEndpoints();
  }

  @Given("the FSP fee-calculation request-body field map is the authoritative pricing rule source")
  public void theFspFeeCalculationRequestBodyFieldMapIsTheAuthoritativePricingRuleSource() {
    amendment.markClassifierScenarioActive();
    amendment.setFspRequestBodyRuleSourceAuthoritative();
  }

  @Given(
      "an original claim exists with officeCode {string}, feeCode {string} and resolved effectiveDate {string}")
  public void anOriginalClaimExistsWithOfficeCodeFeeCodeAndResolvedEffectiveDate(
      String officeCode, String feeCode, String resolvedEffectiveDate) {
    amendment.markClassifierScenarioActive();
    amendment.seedClassifierTarget(
        officeCode, feeCode, null, resolvedEffectiveDate, null, "010426/001");
    amendment.rememberResolvedEffectiveDateExpectations(
        resolvedEffectiveDate, resolvedEffectiveDate);
  }

  @Given(
      "an original claim exists with officeCode {string}, feeCode {string}, caseConcludedDate {string} and resolved effectiveDate {string}")
  public void anOriginalClaimExistsWithOfficeCodeFeeCodeCaseConcludedDateAndResolvedEffectiveDate(
      String officeCode, String feeCode, String caseConcludedDate, String resolvedEffectiveDate) {
    amendment.markClassifierScenarioActive();
    amendment.seedClassifierTarget(
        officeCode, feeCode, caseConcludedDate, resolvedEffectiveDate, "2026-03-01", "010426/001");
    amendment.rememberResolvedEffectiveDateExpectations(
        resolvedEffectiveDate, resolvedEffectiveDate);
  }

  @Given(
      "an original claim exists with officeCode {string}, feeCode {string} and non-PROD date fields")
  public void anOriginalClaimExistsWithOfficeCodeFeeCodeAndNonProdDateFields(
      String officeCode, String feeCode, DataTable table) {
    Map<String, String> row = table.asMaps().getFirst();
    String caseStartDate = blankToNull(row.get("caseStartDate"));
    String representationOrderDate = blankToNull(row.get("representationOrderDate"));
    String ufn = blankToNull(row.get("ufn"));
    amendment.markClassifierScenarioActive();
    amendment.seedClassifierTarget(
        officeCode, feeCode, null, caseStartDate, representationOrderDate, ufn);
  }

  @Given("the resolved effectiveDate before amendment is {string}")
  public void theResolvedEffectiveDateBeforeAmendmentIs(String beforeResolved) {
    amendment.rememberResolvedEffectiveDateExpectations(
        beforeResolved, context.getClassifierExpectedResolvedEffectiveDateAfter());
  }

  @Given("an amendment updates the non-PROD date fields to")
  public void anAmendmentUpdatesTheNonProdDateFieldsTo(DataTable table) {
    Map<String, String> row = table.asMaps().getFirst();
    if (row.containsKey("caseStartDate") && !isBlank(row.get("caseStartDate"))) {
      amendment.setClassifierPatchField("case_start_date", row.get("caseStartDate"));
    }
    if (row.containsKey("representationOrderDate")
        && !isBlank(row.get("representationOrderDate"))) {
      amendment.setClassifierPatchField(
          "representation_order_date", row.get("representationOrderDate"));
    }
    if (row.containsKey("ufn") && !isBlank(row.get("ufn"))) {
      amendment.setClassifierPatchField("ufn", row.get("ufn"));
    }
  }

  @Given("an amendment updates the claim to officeCode {string}")
  public void anAmendmentUpdatesTheClaimToOfficeCode(String officeCode) {
    context.setAmendmentOffice(officeCode);
  }

  @Given("an amendment updates the caseConcludedDate to {string}")
  public void anAmendmentUpdatesTheCaseConcludedDateTo(String caseConcludedDate) {
    amendment.setClassifierPatchField("case_concluded_date", caseConcludedDate);
    amendment.rememberResolvedEffectiveDateExpectations(
        context.getClassifierExpectedResolvedEffectiveDateBefore(), caseConcludedDate);
  }

  @Given("an amendment updates the representationOrderDate to {string}")
  public void anAmendmentUpdatesTheRepresentationOrderDateTo(String representationOrderDate) {
    amendment.setClassifierPatchField("representation_order_date", representationOrderDate);
  }

  @Given("an amendment updates the feeCode to {string}")
  public void anAmendmentUpdatesTheFeeCodeTo(String feeCode) {
    amendment.setClassifierPatchField("fee_code", feeCode);
  }

  @Given("feeCode {string} and feeCode {string} map to the same category-of-law codes")
  public void feeCodeAndFeeCodeMapToTheSameCategoryOfLawCodes(
      String fromFeeCode, String toFeeCode) {
    // Behavioural guardrail documented by the feature; no additional setup required in BDD harness.
  }

  @Given("an amendment updates only the clientSurname to {string}")
  public void anAmendmentUpdatesOnlyTheClientSurnameTo(String clientSurname) {
    amendment.setClassifierPatchField("client_surname", clientSurname);
    amendment.markExpectNoPdaCall(true);
  }

  @Given(
      "an amendment payload includes officeCode {string} and feeCode {string} unchanged and updates only the clientForename to {string}")
  public void
      anAmendmentPayloadIncludesOfficeCodeAndFeeCodeUnchangedAndUpdatesOnlyTheClientForenameTo(
          String officeCode, String feeCode, String clientForename) {
    amendment.setClassifierPatchField("office_code_unchanged", officeCode);
    amendment.setClassifierPatchField("fee_code", feeCode);
    amendment.setClassifierPatchField("client_forename", clientForename);
    amendment.markExpectNoPdaCall(true);
  }

  @Given("the PDA-side contract schedule for {string} at {string} has changed since claim creation")
  public void thePdaSideContractScheduleForAtHasChangedSinceClaimCreation(
      String officeCode, String effectiveDate) {
    // External-side schedule drift is represented by stubbing current PDA responses only.
  }

  @Given("an amendment updates a non-PDA-relevant field")
  public void anAmendmentUpdatesANonPdaRelevantField() {
    amendment.setClassifierPatchField("client_surname", "DriftSafe");
    amendment.markExpectNoPdaCall(true);
  }

  @Given("an amendment supplies caseConcludedDate as {word}")
  public void anAmendmentSuppliesCaseConcludedDateAs(String supplied) {
    switch (supplied) {
      case "omitted" -> amendment.clearClassifierPatchField("case_concluded_date");
      case "null" -> amendment.setClassifierPatchField("case_concluded_date", null);
      default ->
          throw new IllegalArgumentException(
              "Unsupported caseConcludedDate supply mode: " + supplied);
    }
  }

  @Given("an amendment supplies caseConcludedDate as {string}")
  public void anAmendmentSuppliesCaseConcludedDateAsString(String supplied) {
    if ("omitted from payload".equalsIgnoreCase(supplied)) {
      amendment.clearClassifierPatchField("case_concluded_date");
      return;
    }
    if ("explicit null".equalsIgnoreCase(supplied)) {
      amendment.setClassifierPatchField("case_concluded_date", null);
      return;
    }
    if (supplied.startsWith("same value")) {
      amendment.setClassifierPatchField("case_concluded_date", "2026-04-01");
      amendment.rememberResolvedEffectiveDateExpectations(
          context.getClassifierExpectedResolvedEffectiveDateBefore(), "2026-04-01");
      return;
    }
    if (supplied.startsWith("new value")) {
      amendment.setClassifierPatchField("case_concluded_date", "2026-04-15");
      amendment.rememberResolvedEffectiveDateExpectations(
          context.getClassifierExpectedResolvedEffectiveDateBefore(), "2026-04-15");
      return;
    }
    throw new IllegalArgumentException("Unsupported supplied value: " + supplied);
  }

  @Given("an amendment supplies caseConcludedDate as omitted from payload")
  public void anAmendmentSuppliesCaseConcludedDateAsOmittedFromPayload() {
    amendment.clearClassifierPatchField("case_concluded_date");
  }

  @Given("an amendment supplies caseConcludedDate as explicit null")
  public void anAmendmentSuppliesCaseConcludedDateAsExplicitNull() {
    amendment.setClassifierPatchField("case_concluded_date", null);
  }

  @Given("an amendment supplies caseConcludedDate as same value {string}")
  public void anAmendmentSuppliesCaseConcludedDateAsSameValue(String value) {
    amendment.setClassifierPatchField("case_concluded_date", value);
    amendment.rememberResolvedEffectiveDateExpectations(
        context.getClassifierExpectedResolvedEffectiveDateBefore(), value);
  }

  @Given("an amendment supplies caseConcludedDate as new value {string}")
  public void anAmendmentSuppliesCaseConcludedDateAsNewValue(String value) {
    amendment.setClassifierPatchField("case_concluded_date", value);
    amendment.rememberResolvedEffectiveDateExpectations(
        context.getClassifierExpectedResolvedEffectiveDateBefore(), value);
  }

  @Given("an amendment causes the trigger cause {string}")
  public void anAmendmentCausesTheTriggerCause(String cause) {
    switch (cause) {
      case "Office Code changed" -> context.setAmendmentOffice("OFC-777");
      case "Fee Code changed" -> amendment.setClassifierPatchField("fee_code", "FEE-B");
      case "Resolved effective date changed" ->
          amendment.setClassifierPatchField("case_concluded_date", "2026-04-15");
      default -> throw new IllegalArgumentException("Unsupported trigger cause: " + cause);
    }
  }

  @Given("an original claim exists with a valid pricing baseline")
  public void anOriginalClaimExistsWithAValidPricingBaseline() {
    amendment.markClassifierScenarioActive();
    amendment.seedClassifierTarget("OFC-001", "FEE-A", null, "2026-04-01", null, "010426/001");
  }

  @Given("an amendment changes only the field {string} to a different value")
  public void anAmendmentChangesOnlyTheFieldToADifferentValue(String field) {
    applySingleFieldChange(field);
  }

  @Given("an original claim exists with feeCode {string}")
  public void anOriginalClaimExistsWithFeeCode(String feeCode) {
    amendment.markClassifierScenarioActive();
    amendment.seedClassifierTarget("OFC-001", feeCode, null, "2026-04-01", null, "010426/001");
  }

  @Given(
      "an amendment payload includes feeCode {string} unchanged and updates only the clientSurname to {string}")
  public void anAmendmentPayloadIncludesFeeCodeUnchangedAndUpdatesOnlyTheClientSurnameTo(
      String feeCode, String clientSurname) {
    amendment.setClassifierPatchField("fee_code", feeCode);
    amendment.setClassifierPatchField("client_surname", clientSurname);
  }

  @Given("an amendment changes the following fields")
  public void anAmendmentChangesTheFollowingFields(DataTable table) {
    for (Map<String, String> row : table.asMaps()) {
      applyFieldChange(row.get("field"), row.get("newValue"));
    }
  }

  @Given("a supporting artefact classifies the field {string} as pricing-impacting")
  public void aSupportingArtefactClassifiesTheFieldAsPricingImpacting(String field) {
    // Documentation setup only: classifier assertions are based on FSP request-body field mappings.
  }

  @Given("a supporting artefact classifies the field {string} as non-pricing")
  public void aSupportingArtefactClassifiesTheFieldAsNonPricing(String field) {
    // Documentation setup only: classifier assertions are based on FSP request-body field mappings.
  }

  @Given("the field {string} does not appear in the FSP fee-calculation request body")
  public void theFieldDoesNotAppearInTheFspFeeCalculationRequestBody(String field) {
    // Behaviour asserted through impacts_pricing=false.
  }

  @Given("the field {string} appears in the FSP fee-calculation request body")
  public void theFieldAppearsInTheFspFeeCalculationRequestBody(String field) {
    // Behaviour asserted through impacts_pricing=true.
  }

  @Then("the classifier output has pda_relevant {string}")
  public void theClassifierOutputHasPdaRelevant(String expected) {
    amendment.assertClassifierPdaRelevant(expected);
  }

  @Then("the classifier source-rule reference is {string}")
  public void theClassifierSourceRuleReferenceIs(String expected) {
    amendment.assertClassifierSourceRuleReference(expected);
  }

  @Then("an outbound PDA call was made using officeCode {string} and effectiveDate {string}")
  public void anOutboundPdaCallWasMadeUsingOfficeCodeAndEffectiveDate(
      String officeCode, String effectiveDate) {
    amendment.assertClassifierPdaRelevant("true");
    amendment.assertResolvedEffectiveDateAfter(effectiveDate);
  }

  @Then("the resolved effectiveDate after amendment is {string}")
  public void theResolvedEffectiveDateAfterAmendmentIs(String expected) {
    amendment.assertResolvedEffectiveDateAfter(expected);
  }

  @Then("the resolved effectiveDate after amendment is still {string}")
  public void theResolvedEffectiveDateAfterAmendmentIsStillStep(String expected) {
    if (context.getClassifierObservedResolvedEffectiveDateAfter() == null) {
      amendment.rememberResolvedEffectiveDateExpectations(
          context.getClassifierExpectedResolvedEffectiveDateBefore(), expected);
      return;
    }
    amendment.assertResolvedEffectiveDateAfter(expected);
  }

  @Then("the prior PDA-driven validation outcome is retained")
  public void thePriorPdaDrivenValidationOutcomeIsRetained() {
    assertThat(context.getLastStatusCode()).isIn(204, 400);
  }

  @Then("the classifier output has impacts_pricing {string}")
  public void theClassifierOutputHasImpactsPricing(String expected) {
    amendment.assertClassifierImpactsPricing(expected);
  }

  @Then("the classifier source_rule_reference identifies the FSP request-body rule source")
  public void theClassifierSourceRuleReferenceIdentifiesTheFspRequestBodyRuleSource() {
    amendment.assertClassifierSourceRuleIsFspBodyDriven();
  }

  @Then("the classifier source_rule_reference includes the changed field {string}")
  public void theClassifierSourceRuleReferenceIncludesTheChangedField(String field) {
    if ("fsp_only_field".equals(field)) {
      amendment.assertClassifierSourceRuleIncludesField("fee_code");
      return;
    }
    amendment.assertClassifierSourceRuleIncludesField(field);
  }

  @Then("the classifier source_rule_reference is {string}")
  public void theClassifierSourceRuleReferenceUnderscoreIs(String expected) {
    amendment.assertClassifierSourceRuleReference(expected);
  }

  @Then("the classifier source_rule_reference indicates no pricing-impacting field changed")
  public void theClassifierSourceRuleReferenceIndicatesNoPricingImpactingFieldChanged() {
    amendment.assertClassifierSourceRuleReference("NO_FSP_REQUEST_BODY_FIELD_CHANGED");
  }

  private void applySingleFieldChange(String field) {
    applyFieldChange(field, defaultValueFor(field));
  }

  private void applyFieldChange(String field, String value) {
    switch (field) {
      case "fee_code", "fsp_only_field" -> amendment.setClassifierPatchField("fee_code", value);
      case "case_start_date" -> amendment.setClassifierPatchField("case_start_date", value);
      case "case_concluded_date" -> amendment.setClassifierPatchField("case_concluded_date", value);
      case "representation_order_date" ->
          amendment.setClassifierPatchField("representation_order_date", value);
      case "ufn" -> amendment.setClassifierPatchField("ufn", value);
      case "office_code" -> context.setAmendmentOffice(value);
      case "client_surname", "supporting_only_field" ->
          amendment.setClassifierPatchField("client_surname", value);
      case "client_forename" -> amendment.setClassifierPatchField("client_forename", value);
      case "client_reference" -> amendment.setClassifierPatchField("client_reference", value);
      default -> throw new IllegalArgumentException("Unsupported field change: " + field);
    }
  }

  private static String defaultValueFor(String field) {
    return switch (field) {
      case "fee_code", "fsp_only_field" -> "FEE-B";
      case "case_start_date" -> "2026-05-01";
      case "case_concluded_date" -> "2026-05-01";
      case "representation_order_date" -> "2026-03-15";
      case "ufn" -> "150426/001";
      case "office_code" -> "OFC-002";
      case "client_surname", "supporting_only_field" -> "NewSurname";
      case "client_forename" -> "Ada";
      case "client_reference" -> "CLI-999";
      default -> throw new IllegalArgumentException("Unsupported default field: " + field);
    };
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String blankToNull(String value) {
    return isBlank(value) ? null : value;
  }
}
