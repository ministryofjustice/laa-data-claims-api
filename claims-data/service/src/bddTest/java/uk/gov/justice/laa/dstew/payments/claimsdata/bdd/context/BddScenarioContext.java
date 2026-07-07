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

  // ---------------------------------------------------------------------------
  // Paired-submission accessors
  // ---------------------------------------------------------------------------

  public Path getFirstGeneratedFilePath() {
    return firstGeneratedFilePath;
  }

  public void setFirstGeneratedFilePath(Path firstGeneratedFilePath) {
    this.firstGeneratedFilePath = firstGeneratedFilePath;
  }

  public Path getSecondGeneratedFilePath() {
    return secondGeneratedFilePath;
  }

  public void setSecondGeneratedFilePath(Path secondGeneratedFilePath) {
    this.secondGeneratedFilePath = secondGeneratedFilePath;
  }

  public String getFirstOffice() {
    return firstOffice;
  }

  public void setFirstOffice(String firstOffice) {
    this.firstOffice = firstOffice;
  }

  public String getSecondOffice() {
    return secondOffice;
  }

  public void setSecondOffice(String secondOffice) {
    this.secondOffice = secondOffice;
  }

  public String getFirstSubmissionPeriod() {
    return firstSubmissionPeriod;
  }

  public void setFirstSubmissionPeriod(String firstSubmissionPeriod) {
    this.firstSubmissionPeriod = firstSubmissionPeriod;
  }

  public String getSecondSubmissionPeriod() {
    return secondSubmissionPeriod;
  }

  public void setSecondSubmissionPeriod(String secondSubmissionPeriod) {
    this.secondSubmissionPeriod = secondSubmissionPeriod;
  }

  public UUID getFirstBulkSubmissionId() {
    return firstBulkSubmissionId;
  }

  public void setFirstBulkSubmissionId(UUID firstBulkSubmissionId) {
    this.firstBulkSubmissionId = firstBulkSubmissionId;
  }

  public UUID getSecondBulkSubmissionId() {
    return secondBulkSubmissionId;
  }

  public void setSecondBulkSubmissionId(UUID secondBulkSubmissionId) {
    this.secondBulkSubmissionId = secondBulkSubmissionId;
  }

  public List<UUID> getFirstSubmissionClaimIds() {
    return firstSubmissionClaimIds;
  }
}
