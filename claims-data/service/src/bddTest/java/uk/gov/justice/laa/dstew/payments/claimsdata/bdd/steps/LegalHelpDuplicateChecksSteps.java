package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import io.cucumber.java.en.Given;

/**
 * Glue for the {@code Background:} of {@code duplicateChecksLegalHelp.feature}. Holds a single
 * no-op scaffold step so each scenario starts from a clearly-named anchor that mirrors the
 * "given I am on the bulk import page" baseline of the upstream UI feature.
 */
public class LegalHelpDuplicateChecksSteps {

  @Given("the Legal Help duplicate checks BDD scaffold is ready")
  public void theLegalHelpDuplicateChecksBddScaffoldIsReady() {
    // No-op anchor for the feature Background. Concrete API wiring lives in
    // BulkSubmissionApiSteps and LegacyDuplicateChecksCompatibilitySteps.
  }
}
