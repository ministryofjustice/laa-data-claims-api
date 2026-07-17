package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context;

import io.cucumber.spring.ScenarioScope;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

/** Scenario-scoped state for BDD steps. Holds the last HTTP response and captured IDs. */
@Component
@ScenarioScope
@Getter
@Setter
public class BddScenarioContext {

  private int lastStatusCode;
  private String lastResponseBody;
  private UUID bulkSubmissionId;
  private final List<UUID> bulkSubmissionIds = new ArrayList<>();
  private final List<UUID> submissionIds = new ArrayList<>();

  // ---------------------------------------------------------------------------
  // Generated-file state (filled in by the "I generate ... file" steps).
  // ---------------------------------------------------------------------------
  private Path generatedFilePath;
  private String generatedFileName;
  private String lastOffice;
  private String lastSubmissionPeriod;

  /**
   * Set by steps that mutate a fixture's {@code submissionPeriod=} header at upload time (e.g.
   * {@code SubmissionValidationSteps}). Used by rejection-message assertions to substitute the
   * literal {@code <CURRENT_MONTH>} placeholder in expected error text with the actual month label
   * written into the fixture.
   */
  private String resolvedSubmissionMonth;

  // ---------------------------------------------------------------------------
  // Paired-submission state (used by scenarios that upload two related files:
  // "first"/"second" naming mirrors the disbursement duplicate-check feature).
  // ---------------------------------------------------------------------------
  private Path firstGeneratedFilePath;
  private Path secondGeneratedFilePath;
  private String firstOffice;
  private String secondOffice;
  private String firstSubmissionPeriod;
  private String secondSubmissionPeriod;
  private UUID firstBulkSubmissionId;
  private UUID secondBulkSubmissionId;
  private final List<UUID> firstSubmissionClaimIds = new ArrayList<>();

  /**
   * Overrides the Lombok-generated setter to keep {@link #generatedFileName} in sync with the
   * derived filename. This is the only accessor with non-trivial behaviour.
   */
  public void setGeneratedFilePath(Path generatedFilePath) {
    this.generatedFilePath = generatedFilePath;
    this.generatedFileName =
        generatedFilePath == null ? null : generatedFilePath.getFileName().toString();
  }

  public void clear() {
    lastStatusCode = 0;
    lastResponseBody = null;
    bulkSubmissionId = null;
    bulkSubmissionIds.clear();
    submissionIds.clear();
    generatedFilePath = null;
    generatedFileName = null;
    lastOffice = null;
    lastSubmissionPeriod = null;
    resolvedSubmissionMonth = null;
    firstGeneratedFilePath = null;
    secondGeneratedFilePath = null;
    firstOffice = null;
    secondOffice = null;
    firstSubmissionPeriod = null;
    secondSubmissionPeriod = null;
    firstBulkSubmissionId = null;
    secondBulkSubmissionId = null;
    firstSubmissionClaimIds.clear();
  }
}
