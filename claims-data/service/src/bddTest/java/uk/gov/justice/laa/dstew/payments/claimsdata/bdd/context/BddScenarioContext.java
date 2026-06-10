package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context;

import io.cucumber.spring.ScenarioScope;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Scenario-scoped state for BDD steps. Holds the last HTTP response and captured IDs. */
@Component
@ScenarioScope
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


  public int getLastStatusCode() {
    return lastStatusCode;
  }

  public void setLastStatusCode(int lastStatusCode) {
    this.lastStatusCode = lastStatusCode;
  }

  public String getLastResponseBody() {
    return lastResponseBody;
  }

  public void setLastResponseBody(String lastResponseBody) {
    this.lastResponseBody = lastResponseBody;
  }

  public UUID getBulkSubmissionId() {
    return bulkSubmissionId;
  }

  public void setBulkSubmissionId(UUID bulkSubmissionId) {
    this.bulkSubmissionId = bulkSubmissionId;
  }

  public List<UUID> getBulkSubmissionIds() {
    return bulkSubmissionIds;
  }

  public List<UUID> getSubmissionIds() {
    return submissionIds;
  }

  public Path getGeneratedFilePath() {
    return generatedFilePath;
  }

  public void setGeneratedFilePath(Path generatedFilePath) {
    this.generatedFilePath = generatedFilePath;
    this.generatedFileName =
        generatedFilePath == null ? null : generatedFilePath.getFileName().toString();
  }

  public String getGeneratedFileName() {
    return generatedFileName;
  }

  public String getLastOffice() {
    return lastOffice;
  }

  public void setLastOffice(String lastOffice) {
    this.lastOffice = lastOffice;
  }

  public String getLastSubmissionPeriod() {
    return lastSubmissionPeriod;
  }

  public void setLastSubmissionPeriod(String lastSubmissionPeriod) {
    this.lastSubmissionPeriod = lastSubmissionPeriod;
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
  }
}
