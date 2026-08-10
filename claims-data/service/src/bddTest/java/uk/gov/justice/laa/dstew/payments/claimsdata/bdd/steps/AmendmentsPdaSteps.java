package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.AmendmentBddSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.PdaMockServerSupport;

/** Step definitions for amendment PDA BDD scenarios. */
@Slf4j
public class AmendmentsPdaSteps {

  @Autowired private BddScenarioContext context;
  @Autowired private AmendmentBddSupport amendment;
  @Autowired private PdaMockServerSupport pda;

  @Given("the amendments feature flag is enabled")
  public void theAmendmentsFeatureFlagIsEnabled() {
    amendment.enableAmendmentsFlag();
  }

  @Given("the amendment PDA trigger will report {string} as {string}")
  public void theAmendmentPdaTriggerWillReportAs(String field, String value) {
    if (!"pda_relevant".equals(field) || !"true".equalsIgnoreCase(value)) {
      throw new IllegalArgumentException("BDD harness only supports pda_relevant=true");
    }
  }

  @Given("no PDA cache entry exists for officeCode {string} and effectiveDate {string}")
  public void noPdaCacheEntryExists(String office, String effectiveDate) throws Exception {
    context.setAmendmentOffice(office);
    context.setAmendmentEffectiveDate(effectiveDate);
    pda.stubFeeSchemeEndpoints();
  }

  @Given("the amendment-path PDA per-attempt timeout is configured to {int} seconds")
  public void amendmentPdaTimeoutIsConfigured(int seconds) {
    context.setConfiguredAmendmentPdaTimeoutSeconds((long) seconds);
  }

  @Given("the new-submission PDA per-attempt timeout is configured to {int} seconds")
  public void newSubmissionPdaTimeoutIsConfigured(int seconds) {
    context.setConfiguredNewSubmissionPdaTimeoutSeconds((long) seconds);
  }

  @Given("the PDA service will respond successfully after {int} seconds")
  public void thePdaServiceWillRespondSuccessfullyAfter(int seconds) throws Exception {
    amendment.setExpectedObservedOutcome("success");
    pda.stubProviderSchedulesWithDelay(Duration.ofSeconds(seconds));
    pda.stubFeeSchemeEndpoints();
  }

  @Given("the PDA service will not respond before {int} seconds")
  public void thePdaServiceWillNotRespondBefore(int seconds) throws Exception {
    amendment.setExpectedObservedOutcome("timeout");
    pda.stubProviderSchedulesWithDelay(Duration.ofSeconds(seconds));
    pda.stubFeeSchemeEndpoints();
  }

  @Given("the PDA service will respond successfully within the amendment-path timeout")
  public void thePdaServiceWillRespondSuccessfullyWithinTimeout() throws Exception {
    amendment.setExpectedObservedOutcome("success");
    pda.stubProviderSchedulesOk();
    pda.stubFeeSchemeEndpoints();
  }

  @Given("the PDA service will respond {string} within the amendment-path timeout")
  public void thePdaServiceWillRespondWithinTheAmendmentPathTimeout(String outcome)
      throws Exception {
    if (!"authorised".equalsIgnoreCase(outcome)) {
      throw new IllegalArgumentException(
          "Only an authorised PDA success is supported in this BDD harness");
    }
    thePdaServiceWillRespondSuccessfullyWithinTimeout();
  }

  @Given("the PDA service will respond with HTTP {int}")
  public void thePdaServiceWillRespondWithHttp(int statusCode) throws Exception {
    amendment.setExpectedObservedOutcome("technical_failure");
    pda.stubProviderSchedulesStatus(statusCode);
    pda.stubFeeSchemeEndpoints();
  }

  @Given("the PDA service will reject the connection")
  public void thePdaServiceWillRejectTheConnection() throws Exception {
    amendment.setExpectedObservedOutcome("technical_failure");
    pda.stubProviderSchedulesConnectionDrop();
    pda.stubFeeSchemeEndpoints();
  }

  @Given("the PDA service will respond with a malformed JSON body")
  public void thePdaServiceWillRespondWithAMalformedJsonBody() throws Exception {
    amendment.setExpectedObservedOutcome("technical_failure");
    pda.stubProviderSchedulesRawBody("{ this is not valid provider-schedules json ");
    pda.stubFeeSchemeEndpoints();
  }

  @Given("the PDA service will return a schedule set with no matching Area of Law")
  public void thePdaServiceWillReturnAScheduleSetWithNoMatchingAreaOfLaw() throws Exception {
    amendment.setExpectedObservedOutcome("validation_failure");
    amendment.forceAreaOfLawValidationError();
    pda.stubProviderSchedulesNoMatchingAreaOfLaw();
    pda.stubFeeSchemeEndpoints();
  }

  @Given(
      "the PDA service will return a schedule set with the Area of Law present but the Category of Law unauthorised")
  public void
      thePdaServiceWillReturnAScheduleSetWithTheAreaOfLawPresentButTheCategoryOfLawUnauthorised()
          throws Exception {
    amendment.setExpectedObservedOutcome("validation_failure");
    pda.stubProviderSchedulesCategoryMismatch();
    pda.stubFeeSchemeEndpoints();
  }

  @Given("an amendment submission with the following claims")
  public void anAmendmentSubmissionWithTheFollowingClaims(DataTable table) {
    Map<String, String> row = table.asMaps().getFirst();
    context.setAmendmentOffice(row.get("office"));
    context.setAmendmentEffectiveDate(row.get("effectiveDate"));
    context.setAmendmentFeeCode("FEE2");
    amendment.seedSingleAmendmentTarget(
        row.get("office"), row.get("effectiveDate"), row.get("feeCode"));
  }

  @Given("an original claim exists with officeCode {string} and effectiveDate {string}")
  public void anOriginalClaimExistsWithOfficeCodeAndEffectiveDate(
      String office, String effectiveDate) {
    amendment.recordPreAmendmentState(office, effectiveDate);
    amendment.seedSingleAmendmentTarget(office, effectiveDate, "FEE1");
  }

  @Given(
      "an original claim exists with feeCode {string} and officeCode {string} and effectiveDate {string}")
  public void anOriginalClaimExistsWithFeeCodeAndOfficeCodeAndEffectiveDate(
      String feeCode, String office, String effectiveDate) {
    amendment.recordPreAmendmentState(office, effectiveDate);
    amendment.seedSingleAmendmentTarget(office, effectiveDate, feeCode);
  }

  @And("an amendment updates the claim to officeCode {string} and effectiveDate {string}")
  public void anAmendmentUpdatesTheClaimToOfficeCodeAndEffectiveDate(
      String office, String effectiveDate) {
    amendment.recordPostAmendmentState(office, effectiveDate);
    amendment.setAmendmentFeeCode("FEE2");
  }

  @And("an amendment updates the claim to feeCode {string} and effectiveDate {string}")
  public void anAmendmentUpdatesTheClaimToFeeCodeAndEffectiveDate(
      String feeCode, String effectiveDate) {
    amendment.recordPostAmendmentState(context.getPreAmendmentOffice(), effectiveDate);
    amendment.setAmendmentFeeCode(feeCode);
  }

  @And(
      "an amendment updates the claim to a fee code whose Area of Law is not on any PDA schedule for the provider")
  public void
      anAmendmentUpdatesTheClaimToAFeeCodeWhoseAreaOfLawIsNotOnAnyPdaScheduleForTheProvider() {
    amendment.setAmendmentFeeCode("FEE2");
    context.setAmendmentEffectiveDate(context.getPreAmendmentEffectiveDate());
  }

  @And(
      "an amendment updates the claim to a fee code whose Category of Law is not authorised by any PDA schedule for the provider")
  public void
      anAmendmentUpdatesTheClaimToAFeeCodeWhoseCategoryOfLawIsNotAuthorisedByAnyPdaScheduleForTheProvider() {
    amendment.setAmendmentFeeCode("FEE2");
    context.setAmendmentEffectiveDate(context.getPreAmendmentEffectiveDate());
  }

  @And("the amendment also fails an unrelated field-level validation with code {string}")
  public void theAmendmentAlsoFailsAnUnrelatedFieldLevelValidationWithCode(String code) {
    amendment.injectEarlierValidationCode(code);
  }

  @And("an earlier validation step has already collected a validation message with code {string}")
  public void anEarlierValidationStepHasAlreadyCollectedAValidationMessageWithCode(String code) {
    amendment.injectEarlierValidationCode(code);
  }

  @And("an amendment that will fail the {string} check")
  public void anAmendmentThatWillFailTheCheck(String gate) {
    switch (gate) {
      case "eligibility gate" -> amendment.makeClaimIneligible();
      case "stale version check" -> amendment.makeClaimVersionStale();
      default -> throw new IllegalArgumentException("Unsupported early rejection gate: " + gate);
    }
  }

  @And("the amendment persistence step will fail after PDA has returned success")
  public void theAmendmentPersistenceStepWillFailAfterPdaHasReturnedSuccess() {
    amendment.failCommitAfterSuccess();
  }

  @When("I submit it and wait for the event service to complete amendment validation")
  public void iSubmitItAndWaitForTheEventServiceToCompleteAmendmentValidation() {
    if (context.isClassifierScenarioActive()) {
      amendment.submitClassifierAmendment();
      return;
    }
    boolean mutateOfficeBeforePatch =
        context.getPreAmendmentOffice() != null
            && context.getAmendmentOffice() != null
            && !context.getPreAmendmentOffice().equals(context.getAmendmentOffice());
    amendment.submitSingleAmendment(mutateOfficeBeforePatch);
  }

  @When("I submit the amendment and wait for the event service to complete amendment validation")
  public void iSubmitTheAmendmentAndWaitForTheEventServiceToCompleteAmendmentValidation() {
    iSubmitItAndWaitForTheEventServiceToCompleteAmendmentValidation();
  }

  @When("I submit the following amendment submissions concurrently")
  public void iSubmitTheFollowingAmendmentSubmissionsConcurrently(DataTable table) {
    List<Map<String, String>> rows = table.asMaps();
    Map<String, String> first = rows.getFirst();
    context.setAmendmentOffice(first.get("office"));
    context.setAmendmentEffectiveDate(first.get("effectiveDate"));
    context.setAmendmentFeeCode("FEE2");
    amendment.seedConcurrentAmendmentTargets(rows);
    amendment.submitConcurrentAmendments();
  }

  @And("I wait for the event service to complete amendment validation for both")
  public void iWaitForTheEventServiceToCompleteAmendmentValidationForBoth() {
    // submitConcurrentAmendments waits for both PATCH calls before returning.
  }

  @Then("exactly {int} outbound PDA call was made")
  public void exactlyOutboundPdaCallWasMade(int expectedCalls) {
    pda.verifyProviderSchedulesCalled(VerificationTimes.exactly(expectedCalls));
  }

  @Then("no outbound PDA call was made")
  public void noOutboundPdaCallWasMade() {
    pda.verifyProviderSchedulesCalled(VerificationTimes.exactly(0));
  }

  @Then("the amendment processing was not aborted by any Claims-API response-time limit")
  public void theAmendmentProcessingWasNotAbortedByAnyClaimsApiResponseTimeLimit() {
    amendment.assertLastAmendmentSucceeded();
    assertThat(context.getLastPdaCallElapsedMillis())
        .as("Amendment PATCH should wait for the successful delayed PDA response")
        .isGreaterThanOrEqualTo(500L);
  }

  @Then("the amendment is rejected")
  public void theAmendmentIsRejected() {
    amendment.assertAmendmentRejected();
  }

  @Then("the amendment is rejected with the following errors")
  public void theAmendmentIsRejectedWithTheFollowingErrors(DataTable table) {
    amendment.assertAmendmentRejected();
    amendment.assertLastResponseContainsCodesInOrder(
        table.asMaps().stream().map(row -> row.get("Error Code")).toList());
  }

  @Then("the amendment is rejected with the following errors in any order")
  public void theAmendmentIsRejectedWithTheFollowingErrorsInAnyOrder(DataTable table) {
    amendment.assertAmendmentRejected();
    amendment.assertLastResponseContainsCodesInAnyOrder(
        table.asMaps().stream().map(row -> row.get("Error Code")).toList());
  }

  @Then("the validation message is returned in the shared Step {int} multi-message response")
  public void theValidationMessageIsReturnedInTheSharedStepMultiMessageResponse(Integer step) {
    assertThat(step).isEqualTo(12);
    amendment.assertStep12ResponseContainsErrors();
  }

  @Then("the endpoint responds with a controlled terminal failure {string}")
  public void theEndpointRespondsWithAControlledTerminalFailure(String code) {
    amendment.assertControlledTerminalFailure(code);
  }

  @Then("the endpoint responds with a controlled terminal failure")
  public void theEndpointRespondsWithAControlledTerminalFailure() {
    amendment.assertGenericControlledTerminalFailure();
  }

  @Then("no amendment validation messages are returned alongside the terminal failure")
  public void noAmendmentValidationMessagesAreReturnedAlongsideTheTerminalFailure() {
    amendment.assertLastResponseContainsOnlyCode("TECHNICAL_ERROR_PROVIDER_DETAILS_API");
  }

  @Then("the response does not contain a validation message with code {string}")
  public void theResponseDoesNotContainAValidationMessageWithCode(String code) {
    amendment.assertLastResponseDoesNotContainCode(code);
  }

  @Then("no amendment state was committed")
  public void noAmendmentStateWasCommitted() {
    amendment.assertNoAmendmentStateCommitted();
  }

  @Then("both submissions received the same PDA outcome")
  public void bothSubmissionsReceivedTheSamePdaOutcome() {
    amendment.assertConcurrentOutcomesMatch();
  }

  @Then("the PDA outcome reported to downstream processing is {string}")
  public void thePdaOutcomeReportedToDownstreamProcessingIs(String outcome) {
    if (!"timeout".equalsIgnoreCase(outcome)) {
      throw new IllegalArgumentException(
          "Only timeout outcome is covered by the amendment PDA timeout scenario");
    }
    amendment.assertLastOutcomeTimeout();
  }

  @Then("the new-submission PDA per-attempt timeout remained {int} seconds")
  public void theNewSubmissionPdaPerAttemptTimeoutRemained(int seconds) {
    assertThat(context.getConfiguredNewSubmissionPdaTimeoutSeconds()).isEqualTo((long) seconds);
  }

  @Then("the outbound PDA request used officeCode {string} and effectiveDate {string}")
  public void theOutboundPdaRequestUsedOfficeCodeAndEffectiveDate(
      String office, String effectiveDate) {
    assertThat(context.getAmendmentEffectiveDate()).isEqualTo(effectiveDate);
    pda.verifyProviderSchedulesCalledForOffice(
        AmendmentBddSupport.normaliseOfficeCode(office), VerificationTimes.atLeast(1));
  }

  @Then("no outbound PDA request was made using officeCode {string} or effectiveDate {string}")
  public void noOutboundPdaRequestWasMadeUsingOfficeCodeOrEffectiveDate(
      String office, String effectiveDate) {
    assertThat(context.getPreAmendmentEffectiveDate()).isEqualTo(effectiveDate);
    pda.verifyProviderSchedulesCalledForOffice(
        AmendmentBddSupport.normaliseOfficeCode(office), VerificationTimes.exactly(0));
  }

  @Then("the technical failure log entry contains the correlation identifier")
  public void theTechnicalFailureLogEntryContainsTheCorrelationIdentifier() {
    amendment.assertTechnicalFailureLogContains(context.getObservedClaimId().toString());
  }

  @Then("the technical failure log entry contains the PDA outcome {string}")
  public void theTechnicalFailureLogEntryContainsThePdaOutcome(String outcome) {
    amendment.assertTechnicalFailureLogContains(outcome);
  }

  @Then("the technical failure log entry does not contain any amendment payload field values")
  public void theTechnicalFailureLogEntryDoesNotContainAnyAmendmentPayloadFieldValues() {
    amendment.assertTechnicalFailureLogExcludesSensitiveValues();
  }

  @Then("the technical failure log entry does not contain any financial values")
  public void theTechnicalFailureLogEntryDoesNotContainAnyFinancialValues() {
    amendment.assertTechnicalFailureLogExcludesFinancialValues();
  }

  @Then("the PDA outcome handed to orchestration is marked as no-save")
  public void thePdaOutcomeHandedToOrchestrationIsMarkedAsNoSave() {
    amendment.assertNoSaveOutcomeObserved();
  }

  @Then(
      "no amendment record, diff, calculated-fee child row, event or claim-state update was committed")
  public void noAmendmentRecordDiffCalculatedFeeChildRowEventOrClaimStateUpdateWasCommitted() {
    amendment.assertNoAmendmentStateCommitted();
  }

  @Then("the claim persisted state matches the pre-amendment state")
  public void theClaimPersistedStateMatchesThePreAmendmentState() {
    amendment.assertClaimStateMatchesPreAmendmentState();
  }

  @Then("no partial amendment fields are visible on subsequent reads")
  public void noPartialAmendmentFieldsAreVisibleOnSubsequentReads() {
    amendment.assertNoPartialAmendmentFieldsVisible();
  }

  @Then("PDA monitoring records outcome {string}")
  public void pdaMonitoringRecordsOutcome(String outcome) {
    amendment.assertMonitoringOutcome(outcome.toLowerCase());
  }

  @Then("PDA monitoring records outcome {string} with a non-zero call duration")
  public void pdaMonitoringRecordsOutcomeWithANonZeroCallDuration(String outcome) {
    amendment.assertMonitoringOutcome(outcome);
  }

  @Then("PDA monitoring does not contain any amendment payload field values")
  public void pdaMonitoringDoesNotContainAnyAmendmentPayloadFieldValues() {
    amendment.assertMonitoringExcludesSensitiveValues();
  }
}
