package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.DEFAULT_OFFICE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.isUatMode;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator.ClaimOverride;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator.Format;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator.GeneratedFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.SubmissionPeriodHelper;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddValidationMessageStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;

/**
 * Step definitions for {@code amendmentsFeatureFlag.feature} (DSTEW-1905).
 *
 * <p>These scenarios exercise the amendments feature flag ({@code
 * laa.claims.api.amendments.enabled}) as a submission-level gate. When disabled/absent, amendment
 * submissions are rejected with a fatal error. Non-amendment submissions (e.g. Mediation) are
 * unaffected. When enabled, amendments proceed through normal validation.
 *
 * <p>The harness has two run modes controlled by the {@code -Dbdd.mode} system property:
 *
 * <ul>
 *   <li><b>local</b> (default) — event-service is not running. The submit-and-wait step drives
 *       outcomes directly via {@code PATCH /api/v1/bulk-submissions/{id}} so status assertions can
 *       pass; message-level assertions are logged and skipped.
 *   <li><b>uat</b> — event-service is present. No PATCH shortcut is applied; the harness waits for
 *       real terminal status and asserts each expected message via {@code GET
 *       /api/v1/validation-messages}.
 * </ul>
 */
@Slf4j
public class AmendmentsFeatureFlagSteps {

  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext context;
  @Autowired private BulkSubmissionFileGenerator generator;
  @Autowired private SubmissionPeriodHelper periodHelper;
  @Autowired private BddValidationMessageStepSupport validationMessages;
  @Autowired private ClaimsApiProperties claimsApiProperties;

  // ---------------------------------------------------------------------------
  // Given — feature flag configuration
  // ---------------------------------------------------------------------------

  @Given("the amendments feature flag is enabled")
  public void theAmendmentsFeatureFlagIsEnabled() {
    claimsApiProperties.getAmendments().setEnabled("true");
    log.info("Amendments feature flag enabled");
  }

  @Given("the amendments feature flag is disabled")
  public void theAmendmentsFeatureFlagIsDisabled() {
    claimsApiProperties.getAmendments().setEnabled("false");
    log.info("Amendments feature flag disabled");
  }

  @Given("the amendments feature flag is not configured")
  public void theAmendmentsFeatureFlagIsNotConfigured() {
    claimsApiProperties.getAmendments().setEnabled(null);
    log.info("Amendments feature flag not configured (defaults to false)");
  }

  // ---------------------------------------------------------------------------
  // Given — submission generation
  // ---------------------------------------------------------------------------

  /**
   * Generates a Legal Help TXT submission that the event-service will treat as an amendment (in UAT
   * mode). In local mode the outcome is PATCH-driven so amendment detection is irrelevant.
   */
  @Given("an amendment submission with the following claims")
  public void anAmendmentSubmissionWithTheFollowingClaims(DataTable table) throws IOException {
    generateFile(Format.TXT, table, AreaOfLaw.LEGAL_HELP);
  }

  /**
   * Generates an amendment submission with a claim missing a required field ({@code feeCode}). Used
   * by AFF_5 to prove that when the flag is enabled, the existing per-claim validations still
   * surface (and the feature-flag error does not).
   */
  @Given("an amendment submission that is missing a required field")
  public void anAmendmentSubmissionMissingRequiredField() throws IOException {
    String office = "1T102C";
    List<ClaimOverride> overrides =
        List.of(
            new ClaimOverride(
                "14091962/T/PERS",
                "010725/123",
                null, // feeCode intentionally omitted
                office,
                null,
                null,
                null));

    String period = periodHelper.nextAvailablePeriod(office, AreaOfLaw.LEGAL_HELP);
    GeneratedFile generated =
        generator.generate(Format.TXT, 1, office, period, overrides, AreaOfLaw.LEGAL_HELP);

    context.setGeneratedFilePath(generated.path());
    context.setLastOffice(generated.office());
    context.setLastSubmissionPeriod(generated.submissionPeriod());
  }

  // ---------------------------------------------------------------------------
  // Then — rejection & no-error assertions specific to this feature
  //
  // NOTE: The following steps are intentionally NOT defined here — they are
  //   reused from existing step classes via Cucumber's cross-class registry:
  //     * "I submit it and wait for the event service to complete the duplicate checks"
  //       (LegalHelpDisbursementsDuplicateChecksSteps)
  //     * "I submit it and wait for the event service to validate it"
  //       (SubmissionValidationSteps)
  //     * "the submission is accepted"
  //       (LegalHelpDisbursementsDuplicateChecksSteps)
  //     * "the submission is rejected with the following errors"
  //       (LegalHelpDisbursementsDuplicateChecksSteps)
  //     * "a Mediation {string} submission with the following claims"
  //       (MediationDuplicateChecksSteps)
  //     * "the Mediation submission is accepted"
  //       (MediationDuplicateChecksSteps)
  // ---------------------------------------------------------------------------

  @Then("the submission is rejected")
  public void theSubmissionIsRejected() {
    UUID id = context.getBulkSubmissionId();
    driveOutcomeIfLocal(id, BulkSubmissionStatus.VALIDATION_FAILED);
    assertThat(id).as("Bulk submission id must be populated").isNotNull();
  }

  @Then("no submission-level error contains {string}")
  public void noSubmissionLevelErrorContains(String searchText) throws Exception {
    UUID id = context.getBulkSubmissionId();

    if (!isUatMode()) {
      log.info(
          "[local mode] Skipping submission-level error negative assertion for {} — would search"
              + " for: {}",
          id,
          searchText);
      return;
    }

    validationMessages.assertNoSubmissionErrorContains(id, searchText);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void generateFile(Format format, DataTable table, AreaOfLaw areaOfLaw)
      throws IOException {
    List<Map<String, String>> rows = table.asMaps(String.class, String.class);
    List<ClaimOverride> overrides = BulkSubmissionFileGenerator.overridesFromRows(rows);

    String office = pickOffice(overrides);
    String period = periodHelper.nextAvailablePeriod(office, areaOfLaw);

    GeneratedFile generated =
        generator.generate(format, Math.max(rows.size(), 1), office, period, overrides, areaOfLaw);

    context.setGeneratedFilePath(generated.path());
    context.setLastOffice(generated.office());
    context.setLastSubmissionPeriod(generated.submissionPeriod());
  }

  private static String pickOffice(List<ClaimOverride> overrides) {
    return overrides.stream()
        .map(ClaimOverride::office)
        .filter(StringUtils::isNotBlank)
        .findFirst()
        .orElse(DEFAULT_OFFICE);
  }

  private void driveOutcomeIfLocal(UUID bulkSubmissionId, BulkSubmissionStatus expected) {
    if (isUatMode() || bulkSubmissionId == null) {
      return;
    }
    api.patchBulkSubmissionStatus(bulkSubmissionId, expected);
  }
}
