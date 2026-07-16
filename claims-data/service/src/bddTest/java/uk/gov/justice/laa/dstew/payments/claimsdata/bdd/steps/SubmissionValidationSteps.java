package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.DEFAULT_OFFICE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.EVENT_SERVICE_POLL_TIMEOUT;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.isUatMode;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.SubmissionPeriodHelper;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

/**
 * Step definitions for {@code submissionValidation.feature}.
 *
 * <p>These scenarios exercise <b>submission-level</b> (not claim-level) rejections triggered by an
 * invalid {@code submissionPeriod} header. The fixture is a minimal, zero-claim TXT — the
 * event-service parses the SCHEDULE header, validates the period, and posts a callback carrying one
 * of the {@code Submissions for …} error messages listed in the feature file.
 *
 * <p>The harness has two run modes controlled by the {@code -Dbdd.mode} system property:
 *
 * <ul>
 *   <li><b>local</b> (default) — event-service is not running. The submit-and-wait step drives the
 *       bulk submission into {@code VALIDATION_FAILED} directly via {@code PATCH
 *       /api/v1/bulk-submissions/{id}} so status assertions can pass; the error-text assertion is
 *       logged and skipped (see {@code assertRejectedWithErrors}).
 *   <li><b>uat</b> — event-service is present. No PATCH shortcut is applied; the harness waits for
 *       the real terminal status and asserts each expected message via {@code GET
 *       /api/v1/validation-messages}. The literal {@code <CURRENT_MONTH>} placeholder in the
 *       feature file is substituted with the actual month written into the fixture (see {@link
 *       BddScenarioContext#getResolvedSubmissionMonth()}).
 * </ul>
 */
@Slf4j
public class SubmissionValidationSteps {

  private static final Pattern SUBMISSION_PERIOD_PATTERN =
      Pattern.compile("submissionPeriod=([A-Z]{3}-\\d{4})");
  private static final Pattern OFFICE_ACCOUNT_PATTERN = Pattern.compile("account=([A-Za-z0-9]+)");

  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext context;

  // ---------------------------------------------------------------------------
  // Given — load fixture (with optional period mutation)
  // ---------------------------------------------------------------------------

  @Given("a submission fixture {string}")
  public void aSubmissionFixture(String classpathFile) throws IOException {
    loadFixture(classpathFile, null);
  }

  @Given("a submission fixture {string} with the submission period set to the {string}")
  public void aSubmissionFixtureWithPeriodOverride(String classpathFile, String periodKind)
      throws IOException {
    String replacement = resolvePeriodKind(periodKind);
    loadFixture(classpathFile, replacement);
    context.setResolvedSubmissionMonth(replacement);
  }

  // ---------------------------------------------------------------------------
  // When — submit + wait
  // ---------------------------------------------------------------------------

  @When("I submit it and wait for the event service to validate it")
  public void iSubmitItAndWaitForValidation() throws IOException {
    String office = requireOffice(context.getLastOffice());
    api.postBulkSubmissionFromPath(
        context.getGeneratedFilePath(), office, ClaimsDataTestUtil.API_USER_ID);
    UUID id = context.getBulkSubmissionId();
    assertThat(id).as("Bulk submission id must be captured after POST").isNotNull();

    if (isUatMode()) {
      String terminal = api.waitForBulkSubmissionTerminalStatus(id, EVENT_SERVICE_POLL_TIMEOUT);
      log.info("[uat mode] Bulk submission {} reached terminal status {}", id, terminal);
    }
    // In local mode the "Then the submission is rejected" step drives the outcome via PATCH.
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Copies the classpath fixture to a temp file, optionally rewriting the {@code submissionPeriod=}
   * header, and captures the fixture office in the scenario context so the subsequent POST uses the
   * right {@code offices=} query parameter.
   */
  private void loadFixture(String classpathFile, String periodOverride) throws IOException {
    ClassPathResource resource = new ClassPathResource(classpathFile);
    String content;
    try (var in = resource.getInputStream()) {
      content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    if (periodOverride != null) {
      Matcher matcher = SUBMISSION_PERIOD_PATTERN.matcher(content);
      if (!matcher.find()) {
        throw new IllegalStateException(
            "Fixture "
                + classpathFile
                + " does not contain a submissionPeriod=MMM-YYYY header to override.");
      }
      content = matcher.replaceFirst("submissionPeriod=" + periodOverride);
    }

    String filename = resource.getFilename() != null ? resource.getFilename() : "bdd-fixture";
    Path tempPath = Files.createTempFile("bdd-fixture-", "-" + filename);
    // Best-effort cleanup so long BDD runs don't leave copies of the classpath fixture in /tmp.
    tempPath.toFile().deleteOnExit();
    Files.writeString(tempPath, content, StandardCharsets.UTF_8);

    context.setGeneratedFilePath(tempPath);
    context.setLastOffice(extractOffice(content));
  }

  private static String extractOffice(String fixtureContent) {
    Matcher matcher = OFFICE_ACCOUNT_PATTERN.matcher(fixtureContent);
    return matcher.find() ? matcher.group(1) : DEFAULT_OFFICE;
  }

  private static String resolvePeriodKind(String periodKind) {
    String normalised = periodKind.toLowerCase(Locale.ROOT).trim();
    return switch (normalised) {
      case "current month" -> SubmissionPeriodHelper.monthLabel(0);
      case "future month" -> SubmissionPeriodHelper.monthLabel(1);
      default ->
          throw new IllegalArgumentException(
              "Unknown period kind '"
                  + periodKind
                  + "' — expected 'current month' or 'future month'.");
    };
  }

  private static String requireOffice(String office) {
    if (StringUtils.isBlank(office)) {
      throw new IllegalStateException(
          "No office captured for the current submission — check the fixture-loader step.");
    }
    return office;
  }
}
