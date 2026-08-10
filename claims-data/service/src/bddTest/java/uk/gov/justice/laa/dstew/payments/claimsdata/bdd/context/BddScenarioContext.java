package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

/** Scenario-scoped state for BDD steps. Holds the last HTTP response and captured IDs. */
@Component
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

  // ---------------------------------------------------------------------------
  // Amendment/PDA scenario state.
  // ---------------------------------------------------------------------------
  private UUID amendmentSubmissionId;
  private UUID amendmentClaimId;
  private final List<UUID> amendmentSubmissionIds = new ArrayList<>();
  private final List<UUID> amendmentClaimIds = new ArrayList<>();
  private final List<Integer> amendmentStatusCodes = new ArrayList<>();
  private final List<String> amendmentResponseBodies = new ArrayList<>();
  private String preAmendmentOffice;
  private String preAmendmentEffectiveDate;
  private String amendmentOffice;
  private String amendmentEffectiveDate;
  private String amendmentFeeCode;
  private Long configuredAmendmentPdaTimeoutSeconds;
  private Long configuredNewSubmissionPdaTimeoutSeconds;
  private Long lastPdaCallElapsedMillis;
  private Long submittedVersionOverride;
  private String injectedValidationCode;
  private boolean forceAreaOfLawValidationError;
  private boolean failCommitAfterSuccess;
  private boolean commitAttempted;
  private boolean commitRolledBack;
  private String expectedObservedOutcome;
  private String lastObservedPdaOutcome;
  private Long lastObservedPdaDurationMillis;
  private UUID observedClaimId;
  private final List<String> observedMonitoringEntries = new ArrayList<>();
  private final List<String> observedLogEntries = new ArrayList<>();
  private String preAmendmentFeeCode;
  private Integer preAmendmentCalculatedFeeCount;
  private Integer preAmendmentHistoryCount;
  private Boolean preAmendmentClaimAmended;
  private Long preAmendmentClaimVersion;

  // ---------------------------------------------------------------------------
  // Amendment classifier scenario state.
  // ---------------------------------------------------------------------------
  private boolean classifierScenarioActive;
  private boolean classifierExpectNoPdaCall;
  private boolean classifierOfficeChanged;
  private String classifierExpectedResolvedEffectiveDateBefore;
  private String classifierExpectedResolvedEffectiveDateAfter;
  private final Map<String, String> classifierPatchFields = new HashMap<>();
  private Boolean classifierObservedPdaRelevant;
  private Boolean classifierObservedImpactsPricing;
  private String classifierObservedSourceRuleReference;
  private String classifierObservedPdaSourceRuleReference;
  private String classifierObservedFspSourceRuleReference;
  private String classifierObservedResolvedEffectiveDateBefore;
  private String classifierObservedResolvedEffectiveDateAfter;
  private final List<String> classifierObservedPricingImpactFields = new ArrayList<>();

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
    amendmentSubmissionId = null;
    amendmentClaimId = null;
    amendmentSubmissionIds.clear();
    amendmentClaimIds.clear();
    amendmentStatusCodes.clear();
    amendmentResponseBodies.clear();
    preAmendmentOffice = null;
    preAmendmentEffectiveDate = null;
    amendmentOffice = null;
    amendmentEffectiveDate = null;
    amendmentFeeCode = null;
    configuredAmendmentPdaTimeoutSeconds = null;
    configuredNewSubmissionPdaTimeoutSeconds = null;
    lastPdaCallElapsedMillis = null;
    submittedVersionOverride = null;
    injectedValidationCode = null;
    forceAreaOfLawValidationError = false;
    failCommitAfterSuccess = false;
    commitAttempted = false;
    commitRolledBack = false;
    expectedObservedOutcome = null;
    lastObservedPdaOutcome = null;
    lastObservedPdaDurationMillis = null;
    observedClaimId = null;
    observedMonitoringEntries.clear();
    observedLogEntries.clear();
    preAmendmentFeeCode = null;
    preAmendmentCalculatedFeeCount = null;
    preAmendmentHistoryCount = null;
    preAmendmentClaimAmended = null;
    preAmendmentClaimVersion = null;
    classifierScenarioActive = false;
    classifierExpectNoPdaCall = false;
    classifierOfficeChanged = false;
    classifierExpectedResolvedEffectiveDateBefore = null;
    classifierExpectedResolvedEffectiveDateAfter = null;
    classifierPatchFields.clear();
    classifierObservedPdaRelevant = null;
    classifierObservedImpactsPricing = null;
    classifierObservedSourceRuleReference = null;
    classifierObservedPdaSourceRuleReference = null;
    classifierObservedFspSourceRuleReference = null;
    classifierObservedResolvedEffectiveDateBefore = null;
    classifierObservedResolvedEffectiveDateAfter = null;
    classifierObservedPricingImpactFields.clear();
  }
}
