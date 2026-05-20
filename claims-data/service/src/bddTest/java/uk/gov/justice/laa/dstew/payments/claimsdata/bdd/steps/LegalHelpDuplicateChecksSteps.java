package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

/**
 * Step definitions for legacy scaffold wiring and placeholder checks.
 * Most duplicate-check logic has been migrated to:
 *   - LegalHelpDuplicateChecksApiSteps (setup/seed steps)
 *   - SubmissionValidationApiSteps (validation/error assertions)
 *   - BulkSubmissionApiSteps (upload/status steps)
 */
public class LegalHelpDuplicateChecksSteps {

  @Given("the Legal Help duplicate checks BDD scaffold is ready")
  public void theLegalHelpDuplicateChecksBddScaffoldIsReady() {
    // Base wiring step for incremental migration from JS Cucumber tests to Java Cucumber tests.
    // All substantial logic is now in concrete step definitions.
  }

  @Then("the scenario can be expanded with API duplicate-check assertions")
  public void theScenarioCanBeExpandedWithApiDuplicateCheckAssertions() {
    // Placeholder assertion point for upcoming API-focused checks.
    // This step is now superseded by migration scenarios in duplicateChecksLegalHelp.feature (@api tag).
  }
}

