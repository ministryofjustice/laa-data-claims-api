package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.BddBeansConfiguration.BddServerInfo;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimHistoryRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;

/** Seeds amendment-path data and drives the real PATCH endpoint for amendment BDD scenarios. */
public class AmendmentBddSupport {

  private static final String PATCH_CLAIM_PATH =
      API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";
  private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final String REQUESTED_BY_PROVIDER = "PROVIDER";
  private static final String REASON_PROVIDER_ERROR = "PROVIDER_ERROR";
  private static final UUID VALID_USER_UUID =
      UUID.fromString("0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e");

  @Autowired private BddScenarioContext context;
  @Autowired private ClaimsApiProperties claimsApiProperties;
  @Autowired private RestTemplate restTemplate;
  @Autowired private BddServerInfo serverInfo;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClaimAmendmentRepository claimAmendmentRepository;
  @Autowired private ClaimHistoryRepository claimHistoryRepository;
  @Autowired private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Autowired private CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  @Autowired private ClientRepository clientRepository;
  @Autowired private ClaimCaseRepository claimCaseRepository;

  public void enableAmendmentsFlag() {
    claimsApiProperties.getAmendments().setEnabled("true");
  }

  public void markClassifierScenarioActive() {
    context.setClassifierScenarioActive(true);
  }

  public void markExpectNoPdaCall(boolean expected) {
    context.setClassifierExpectNoPdaCall(expected);
  }

  public void assumePositivePdaCacheEntry() {
    // Classifier scenarios assert trigger decisions against outbound-call observations. We keep the
    // setup deterministic by stubbing successful PDA responses per scenario and resetting state in
    // hooks, rather than pre-populating cross-scenario caches.
  }

  public void setFspRequestBodyRuleSourceAuthoritative() {
    // Documentation-only step for classifier scenarios; runtime classification uses
    // FeeSchemeRequestField mappings.
  }

  public void recordPreAmendmentState(String office, String effectiveDate) {
    context.setPreAmendmentOffice(office);
    context.setPreAmendmentEffectiveDate(effectiveDate);
  }

  public void recordPostAmendmentState(String office, String effectiveDate) {
    context.setAmendmentOffice(office);
    context.setAmendmentEffectiveDate(effectiveDate);
  }

  public void setAmendmentFeeCode(String feeCode) {
    context.setAmendmentFeeCode(translateFeeCode(feeCode));
  }

  public void injectEarlierValidationCode(String code) {
    context.setInjectedValidationCode(code);
  }

  public void forceAreaOfLawValidationError() {
    context.setForceAreaOfLawValidationError(true);
  }

  public void setExpectedObservedOutcome(String outcome) {
    context.setExpectedObservedOutcome(outcome);
  }

  public void failCommitAfterSuccess() {
    context.setFailCommitAfterSuccess(true);
  }

  public void makeClaimIneligible() {
    Claim claim =
        claimRepository
            .findById(require(context.getAmendmentClaimId(), "amendmentClaimId"))
            .orElseThrow();
    claim.setStatus(ClaimStatus.INVALID);
    claimRepository.saveAndFlush(claim);
    snapshotPersistedState();
  }

  public void makeClaimVersionStale() {
    Claim claim =
        claimRepository
            .findById(require(context.getAmendmentClaimId(), "amendmentClaimId"))
            .orElseThrow();
    context.setSubmittedVersionOverride(claim.getVersion() + 1L);
    snapshotPersistedState();
  }

  public void seedSingleAmendmentTarget(String office, String effectiveDate, String feeCode) {
    SeededTarget target = seedTarget(office, effectiveDate, feeCode);
    context.setAmendmentSubmissionId(target.submissionId());
    context.setAmendmentClaimId(target.claimId());
    context.getAmendmentSubmissionIds().clear();
    context.getAmendmentClaimIds().clear();
    context.getAmendmentSubmissionIds().add(target.submissionId());
    context.getAmendmentClaimIds().add(target.claimId());
    snapshotPersistedState();
  }

  public void seedClassifierTarget(
      String office,
      String feeCode,
      String caseConcludedDate,
      String caseStartDate,
      String representationOrderDate,
      String ufn) {
    String resolvedDate =
        caseStartDate != null
            ? caseStartDate
            : (representationOrderDate != null ? representationOrderDate : "2026-04-01");
    SeededTarget target = seedTarget(office, resolvedDate, feeCode);
    context.setAmendmentSubmissionId(target.submissionId());
    context.setAmendmentClaimId(target.claimId());
    context.getAmendmentSubmissionIds().clear();
    context.getAmendmentClaimIds().clear();
    context.getAmendmentSubmissionIds().add(target.submissionId());
    context.getAmendmentClaimIds().add(target.claimId());

    Claim claim = claimRepository.findById(target.claimId()).orElseThrow();
    if (caseConcludedDate != null) {
      claim.setCaseConcludedDate(LocalDate.parse(caseConcludedDate));
    }
    if (caseStartDate != null) {
      claim.setCaseStartDate(LocalDate.parse(caseStartDate));
    } else {
      claim.setCaseStartDate(null);
    }
    if (representationOrderDate != null) {
      claim.setRepresentationOrderDate(LocalDate.parse(representationOrderDate));
    } else {
      claim.setRepresentationOrderDate(null);
    }
    if (ufn != null) {
      claim.setUniqueFileNumber(ufn);
    }
    claimRepository.saveAndFlush(claim);

    context.getClassifierPatchFields().clear();
    context.setPreAmendmentOffice(office);
    context.setPreAmendmentEffectiveDate(resolvedDate);
    snapshotPersistedState();
  }

  public void rememberResolvedEffectiveDateExpectations(String before, String after) {
    context.setClassifierExpectedResolvedEffectiveDateBefore(before);
    context.setClassifierExpectedResolvedEffectiveDateAfter(after);
  }

  public void setClassifierPatchField(String field, String value) {
    context.getClassifierPatchFields().put(field, value);
  }

  public void clearClassifierPatchField(String field) {
    context.getClassifierPatchFields().remove(field);
  }

  public void submitClassifierAmendment() {
    context.setCommitAttempted(false);
    context.setCommitRolledBack(false);
    if (context.getAmendmentOffice() != null
        && context.getPreAmendmentOffice() != null
        && !context.getAmendmentOffice().equalsIgnoreCase(context.getPreAmendmentOffice())) {
      moveSeededSubmissionToOffice(context.getAmendmentOffice());
      context.setClassifierOfficeChanged(true);
    }
    ClaimPatch patch =
        buildClassifierPatch(require(context.getAmendmentClaimId(), "amendmentClaimId"));
    executePatch(
        require(context.getAmendmentSubmissionId(), "amendmentSubmissionId"),
        require(context.getAmendmentClaimId(), "amendmentClaimId"),
        patch,
        true);
  }

  public void seedConcurrentAmendmentTargets(List<Map<String, String>> rows) {
    context.getAmendmentSubmissionIds().clear();
    context.getAmendmentClaimIds().clear();
    for (Map<String, String> row : rows) {
      SeededTarget target =
          seedTarget(row.get("office"), row.get("effectiveDate"), row.get("feeCode"));
      context.getAmendmentSubmissionIds().add(target.submissionId());
      context.getAmendmentClaimIds().add(target.claimId());
    }
  }

  public void moveSeededSubmissionToOffice(String office) {
    Submission submission =
        submissionRepository
            .findById(require(context.getAmendmentSubmissionId(), "amendmentSubmissionId"))
            .orElseThrow();
    submission.setOfficeAccountNumber(normaliseOfficeCode(office));
    submissionRepository.saveAndFlush(submission);
    context.setAmendmentOffice(office);
  }

  public void submitSingleAmendment(boolean mutateOfficeBeforePatch) {
    context.setCommitAttempted(false);
    context.setCommitRolledBack(false);
    if (mutateOfficeBeforePatch && context.getAmendmentOffice() != null) {
      moveSeededSubmissionToOffice(context.getAmendmentOffice());
    }
    ClaimPatch patch =
        buildPatch(context.getAmendmentClaimId(), context.getAmendmentEffectiveDate());
    executePatch(context.getAmendmentSubmissionId(), context.getAmendmentClaimId(), patch, true);
  }

  public void submitConcurrentAmendments() {
    context.getAmendmentStatusCodes().clear();
    context.getAmendmentResponseBodies().clear();
    try (var executor =
        Executors.newFixedThreadPool(Math.max(2, context.getAmendmentClaimIds().size()))) {
      List<Callable<PatchResult>> calls = new ArrayList<>();
      for (int i = 0; i < context.getAmendmentClaimIds().size(); i++) {
        UUID submissionId = context.getAmendmentSubmissionIds().get(i);
        UUID claimId = context.getAmendmentClaimIds().get(i);
        ClaimPatch patch = buildPatch(claimId, context.getAmendmentEffectiveDate());
        calls.add(() -> executePatch(submissionId, claimId, patch, false));
      }
      List<Future<PatchResult>> futures = executor.invokeAll(calls);
      for (Future<PatchResult> future : futures) {
        PatchResult result = future.get();
        context.getAmendmentStatusCodes().add(result.statusCode());
        context.getAmendmentResponseBodies().add(result.responseBody());
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Concurrent amendment submission failed", ex);
    }
  }

  public void assertLastAmendmentSucceeded() {
    if (context.getLastStatusCode() != 204) {
      throw new AssertionError(
          "Expected amendment PATCH to succeed with 204 but was "
              + context.getLastStatusCode()
              + " body="
              + context.getLastResponseBody());
    }
  }

  public void assertAmendmentRejected() {
    assertThat(context.getLastStatusCode()).isGreaterThanOrEqualTo(400);
  }

  public void assertLastOutcomeTimeout() {
    String body = context.getLastResponseBody();
    if (context.getLastStatusCode() < 400
        || body == null
        || !body.contains("TECHNICAL_ERROR_PROVIDER_DETAILS_API")) {
      throw new AssertionError(
          "Expected amendment PATCH to surface TECHNICAL_ERROR_PROVIDER_DETAILS_API but status/body were "
              + context.getLastStatusCode()
              + " / "
              + body);
    }
  }

  public void assertConcurrentOutcomesMatch() {
    if (context.getAmendmentStatusCodes().isEmpty()) {
      throw new AssertionError("No concurrent amendment outcomes were captured");
    }
    int expectedStatus = context.getAmendmentStatusCodes().getFirst();
    String expectedBody = normalize(context.getAmendmentResponseBodies().getFirst());
    List<String> expectedErrorCodes =
        expectedStatus >= 400 && !expectedBody.isBlank() ? extractErrorCodes(expectedBody) : List.of();
    for (int i = 0; i < context.getAmendmentStatusCodes().size(); i++) {
      if (context.getAmendmentStatusCodes().get(i) != expectedStatus) {
        throw new AssertionError(
            "Concurrent amendment status codes differed: " + context.getAmendmentStatusCodes());
      }
      String actualBody = normalize(context.getAmendmentResponseBodies().get(i));
      if (expectedStatus < 400) {
        continue;
      }
      if (actualBody.isBlank() || !extractErrorCodes(actualBody).equals(expectedErrorCodes)) {
        throw new AssertionError("Concurrent amendment response bodies differed");
      }
    }
  }

  public void assertLastResponseContainsCodesInOrder(List<String> expectedCodes) {
    String body = require(context.getLastResponseBody(), "lastResponseBody");
    assertThat(extractErrorCodes(body))
        .as("response body=%s", body)
        .containsExactlyElementsOf(expectedCodes);
  }

  public void assertLastResponseContainsCodesInAnyOrder(List<String> expectedCodes) {
    String body = require(context.getLastResponseBody(), "lastResponseBody");
    assertThat(extractErrorCodes(body))
        .as("response body=%s", body)
        .containsExactlyInAnyOrderElementsOf(expectedCodes);
  }

  public void assertStep12ResponseContainsErrors() {
    assertThat(extractErrorCodes(require(context.getLastResponseBody(), "lastResponseBody")))
        .isNotEmpty();
  }

  public void assertControlledTerminalFailure(String code) {
    assertThat(context.getLastStatusCode()).isGreaterThanOrEqualTo(400);
    assertThat(context.getLastResponseBody()).contains(code);
  }

  public void assertGenericControlledTerminalFailure() {
    assertThat(context.getLastStatusCode()).isGreaterThanOrEqualTo(500);
    assertThat(context.getLastResponseBody()).contains("\"status\"");
  }

  public void assertLastResponseContainsOnlyCode(String code) {
    assertThat(extractErrorCodes(require(context.getLastResponseBody(), "lastResponseBody")))
        .containsExactly(code);
  }

  public void assertLastResponseDoesNotContainCode(String code) {
    assertThat(extractErrorCodes(require(context.getLastResponseBody(), "lastResponseBody")))
        .doesNotContain(code);
  }

  public void assertNoSaveOutcomeObserved() {
    assertThat(context.getLastObservedPdaOutcome())
        .isEqualTo(require(context.getExpectedObservedOutcome(), "expectedObservedOutcome"));
    assertNoAmendmentStateCommitted();
  }

  public void assertNoAmendmentStateCommitted() {
    UUID claimId = require(context.getAmendmentClaimId(), "amendmentClaimId");
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(claimId)).isEmpty();
    assertClaimStateMatchesPreAmendmentState();
    assertThat(countCalculatedFeeRows(claimId))
        .isEqualTo(
            require(context.getPreAmendmentCalculatedFeeCount(), "preAmendmentCalculatedFeeCount"));
    assertThat(claimHistoryRepository.findHistory(claimId, 20))
        .hasSize(require(context.getPreAmendmentHistoryCount(), "preAmendmentHistoryCount"));
  }

  public void assertClaimStateMatchesPreAmendmentState() {
    UUID claimId = require(context.getAmendmentClaimId(), "amendmentClaimId");
    Claim claim = claimRepository.findById(claimId).orElseThrow();
    Submission submission =
        submissionRepository
            .findById(require(context.getAmendmentSubmissionId(), "amendmentSubmissionId"))
            .orElseThrow();
    assertThat(claim.getFeeCode()).isEqualTo(context.getPreAmendmentFeeCode());
    assertThat(claim.getCaseStartDate())
        .isEqualTo(LocalDate.parse(context.getPreAmendmentEffectiveDate()));
    assertThat(claim.isAmended()).isEqualTo(context.getPreAmendmentClaimAmended());
    assertThat(claim.getVersion()).isEqualTo(context.getPreAmendmentClaimVersion());
    assertThat(submission.getOfficeAccountNumber())
        .isEqualTo(normaliseOfficeCode(context.getPreAmendmentOffice()));
  }

  public void assertNoPartialAmendmentFieldsVisible() {
    assertClaimStateMatchesPreAmendmentState();
  }

  public void assertTechnicalFailureLogContains(String expectedFragment) {
    assertThat(context.getObservedLogEntries()).isNotEmpty();
    assertThat(context.getObservedLogEntries().getLast()).contains(expectedFragment);
  }

  public void assertTechnicalFailureLogExcludesSensitiveValues() {
    assertThat(context.getObservedLogEntries()).isNotEmpty();
    String entry = context.getObservedLogEntries().getLast();
    assertThat(entry)
        .doesNotContain(context.getPreAmendmentOffice())
        .doesNotContain(context.getAmendmentOffice())
        .doesNotContain(context.getPreAmendmentEffectiveDate())
        .doesNotContain(context.getAmendmentEffectiveDate())
        .doesNotContain(context.getPreAmendmentFeeCode())
        .doesNotContain(context.getAmendmentFeeCode());
  }

  public void assertTechnicalFailureLogExcludesFinancialValues() {
    assertThat(context.getObservedLogEntries()).isNotEmpty();
    String entry = context.getObservedLogEntries().getLast();
    assertThat(entry)
        .doesNotContain("250.00")
        .doesNotContain("40.00")
        .doesNotContain("35.00")
        .doesNotContain("100.00");
  }

  public void assertMonitoringOutcome(String expectedOutcome) {
    assertThat(context.getLastObservedPdaOutcome()).isEqualTo(expectedOutcome);
    assertThat(context.getLastObservedPdaDurationMillis()).isNotNull().isGreaterThan(0L);
  }

  public void assertMonitoringExcludesSensitiveValues() {
    assertThat(context.getObservedMonitoringEntries()).isNotEmpty();
    String entry = context.getObservedMonitoringEntries().getLast();
    assertThat(entry)
        .doesNotContain(context.getPreAmendmentOffice())
        .doesNotContain(context.getAmendmentOffice())
        .doesNotContain(context.getPreAmendmentEffectiveDate())
        .doesNotContain(context.getAmendmentEffectiveDate())
        .doesNotContain(context.getPreAmendmentFeeCode())
        .doesNotContain(context.getAmendmentFeeCode());
  }

  public void assertClassifierPdaRelevant(String expected) {
    assertThat(require(context.getClassifierObservedPdaRelevant(), "classifierObservedPdaRelevant"))
        .isEqualTo(Boolean.parseBoolean(expected));
  }

  public void assertClassifierImpactsPricing(String expected) {
    assertThat(
            require(
                context.getClassifierObservedImpactsPricing(), "classifierObservedImpactsPricing"))
        .isEqualTo(Boolean.parseBoolean(expected));
  }

  public void assertClassifierSourceRuleReference(String expected) {
    String observed =
        expected.startsWith("FSP_") || expected.startsWith("NO_FSP_")
            ? require(
                context.getClassifierObservedFspSourceRuleReference(),
                "classifierObservedFspSourceRuleReference")
            : require(
                context.getClassifierObservedPdaSourceRuleReference(),
                "classifierObservedPdaSourceRuleReference");
    assertThat(observed).isEqualTo(expected);
  }

  public void assertClassifierSourceRuleIncludesField(String expectedField) {
    assertThat(context.getClassifierObservedPricingImpactFields()).contains(expectedField);
  }

  public void assertClassifierSourceRuleIsFspBodyDriven() {
    assertThat(
            require(
                context.getClassifierObservedFspSourceRuleReference(),
                "classifierObservedFspSourceRuleReference"))
        .contains("FSP_REQUEST_BODY");
  }

  public void assertResolvedEffectiveDateBefore(String expected) {
    assertThat(
            require(
                context.getClassifierObservedResolvedEffectiveDateBefore(),
                "classifierObservedResolvedEffectiveDateBefore"))
        .isEqualTo(expected);
  }

  public void assertResolvedEffectiveDateAfter(String expected) {
    assertThat(
            require(
                context.getClassifierObservedResolvedEffectiveDateAfter(),
                "classifierObservedResolvedEffectiveDateAfter"))
        .isEqualTo(expected);
  }

  private void snapshotPersistedState() {
    UUID claimId = require(context.getAmendmentClaimId(), "amendmentClaimId");
    Claim claim = claimRepository.findById(claimId).orElseThrow();
    context.setPreAmendmentFeeCode(claim.getFeeCode());
    context.setPreAmendmentClaimVersion(claim.getVersion());
    context.setPreAmendmentClaimAmended(claim.isAmended());
    context.setPreAmendmentCalculatedFeeCount(countCalculatedFeeRows(claimId));
    context.setPreAmendmentHistoryCount(claimHistoryRepository.findHistory(claimId, 20).size());
  }

  private int countCalculatedFeeRows(UUID claimId) {
    return (int)
        calculatedFeeDetailRepository.findAll().stream()
            .map(CalculatedFeeDetail::getClaim)
            .filter(claim -> claim != null && claimId.equals(claim.getId()))
            .count();
  }

  private List<String> extractErrorCodes(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode errors = root.path("errors");
      if (errors.isArray()) {
        List<String> codes = new ArrayList<>();
        for (JsonNode error : errors) {
          if (error.hasNonNull("code")) {
            codes.add(error.get("code").asText());
          }
        }
        return codes;
      }
      JsonNode issues = root.path("issues");
      if (issues.isArray()) {
        List<String> codes = new ArrayList<>();
        for (JsonNode issue : issues) {
          if (issue.hasNonNull("messageCode")) {
            codes.add(issue.get("messageCode").asText());
          } else if (issue.hasNonNull("code")) {
            codes.add(issue.get("code").asText());
          }
        }
        return codes;
      }
      return List.of();
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Unable to parse amendment response body: " + responseBody, ex);
    }
  }

  private SeededTarget seedTarget(String office, String effectiveDate, String feeCode) {
    String internalOffice = normaliseOfficeCode(office);
    LocalDate effectiveLocalDate = LocalDate.parse(effectiveDate);
    Instant now = Instant.now();
    Submission submission =
        Submission.builder()
            .id(UUID.randomUUID())
            .officeAccountNumber(internalOffice)
            .submissionPeriod(
                effectiveLocalDate.getMonth().name().substring(0, 3)
                    + "-"
                    + effectiveLocalDate.getYear())
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId("bdd-amendment-test")
            .providerUserId("bdd-amendment-test")
            .numberOfClaims(0)
            .createdOn(now)
            .build();
    submissionRepository.saveAndFlush(submission);

    Claim claim =
        Claim.builder()
            .id(UUID.randomUUID())
            .submission(submission)
            .status(ClaimStatus.VALID)
            .scheduleReference("SCH-123")
            .lineNumber(1)
            .caseReferenceNumber("CASE-123")
            .feeCode(translateFeeCode(feeCode))
            .uniqueFileNumber("010125/001")
            .caseStartDate(effectiveLocalDate)
            .caseConcludedDate(effectiveLocalDate.plusDays(9))
            .matterTypeCode("MATT:111")
            .createdByUserId("12345")
            .createdOn(now)
            .build();
    claimRepository.saveAndFlush(claim);

    clientRepository.saveAndFlush(
        Client.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .clientForename("Alice")
            .clientSurname("Smith")
            .clientDateOfBirth(LocalDate.of(1990, 1, 1))
            .clientPostcode("SW1H 9HE")
            .genderCode("F")
            .ethnicityCode("99")
            .disabilityCode("COG")
            .uniqueClientNumber("01011990/A/BCDE")
            .createdByUserId("12345")
            .createdOn(now)
            .build());

    claimCaseRepository.saveAndFlush(
        ClaimCase.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .caseId("123")
            .uniqueCaseId("UC_ID_1")
            .caseStageCode("FPL01")
            .stageReachedCode("AB")
            .standardFeeCategoryCode("1A")
            .outcomeCode("AB")
            .designatedAccreditedRepresentativeCode("1")
            .isPostalApplicationAccepted(true)
            .isClient2PostalApplicationAccepted(true)
            .mentalHealthTribunalReference("AA/1234/56789")
            .isNrmAdvice(true)
            .followOnWork("FOLLOW_1")
            .transferDate(effectiveLocalDate.plusDays(20))
            .exemptionCriteriaSatisfied("AB123")
            .exceptionalCaseFundingReference("1234567AB")
            .isLegacyCase(true)
            .createdByUserId("12345")
            .createdOn(now)
            .build());

    ClaimSummaryFee summaryFee =
        claimSummaryFeeRepository.saveAndFlush(
            ClaimSummaryFee.builder()
                .id(UUID.randomUUID())
                .claim(claim)
                .adviceTime(120)
                .travelTime(45)
                .waitingTime(30)
                .netProfitCostsAmount(BigDecimal.valueOf(250))
                .netDisbursementAmount(BigDecimal.valueOf(40))
                .netCounselCostsAmount(BigDecimal.valueOf(35))
                .disbursementsVatAmount(BigDecimal.valueOf(8))
                .travelWaitingCostsAmount(BigDecimal.valueOf(15))
                .netWaitingCostsAmount(BigDecimal.valueOf(12))
                .isVatApplicable(true)
                .isToleranceApplicable(false)
                .priorAuthorityReference("PAR0001")
                .isLondonRate(true)
                .adjournedHearingFeeAmount(2)
                .isAdditionalTravelPayment(true)
                .costsDamagesRecoveredAmount(BigDecimal.valueOf(75))
                .meetingsAttendedCode("MTGA01")
                .detentionTravelWaitingCostsAmount(BigDecimal.valueOf(11))
                .jrFormFillingAmount(BigDecimal.valueOf(9))
                .isEligibleClient(true)
                .courtLocationCode("CRT-001")
                .adviceTypeCode("FTF")
                .medicalReportsCount(2)
                .isIrcSurgery(false)
                .surgeryDate(effectiveLocalDate.plusDays(14))
                .surgeryClientsCount(3)
                .surgeryMattersCount(1)
                .cmrhOralCount(1)
                .cmrhTelephoneCount(0)
                .aitHearingCentreCode("01")
                .isSubstantiveHearing(true)
                .hoInterview(1)
                .localAuthorityNumber("LA001")
                .createdByUserId("12345")
                .createdOn(now)
                .build());

    calculatedFeeDetailRepository.saveAndFlush(
        CalculatedFeeDetail.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .claimSummaryFee(summaryFee)
            .feeCode("CALC-FEE-1")
            .feeType(FeeCalculationType.DISB_ONLY)
            .feeCodeDescription("Calculated fee for claim 1")
            .categoryOfLaw("IMMIGRATION")
            .totalAmount(new BigDecimal("100.00"))
            .vatIndicator(true)
            .vatRateApplied(new BigDecimal("0.20"))
            .calculatedVatAmount(BigDecimal.valueOf(25))
            .disbursementAmount(BigDecimal.valueOf(15))
            .requestedNetDisbursementAmount(BigDecimal.valueOf(13))
            .disbursementVatAmount(BigDecimal.valueOf(2))
            .hourlyTotalAmount(BigDecimal.valueOf(60))
            .fixedFeeAmount(BigDecimal.valueOf(40))
            .netProfitCostsAmount(BigDecimal.valueOf(80))
            .requestedNetProfitCostsAmount(BigDecimal.valueOf(70))
            .netCostOfCounselAmount(BigDecimal.valueOf(35))
            .netTravelCostsAmount(BigDecimal.valueOf(20))
            .netWaitingCostsAmount(BigDecimal.valueOf(10))
            .detentionTravelAndWaitingCostsAmount(BigDecimal.valueOf(5))
            .jrFormFillingAmount(BigDecimal.valueOf(3))
            .travelAndWaitingCostsAmount(BigDecimal.valueOf(7))
            .boltOnTotalFeeAmount(BigDecimal.valueOf(12))
            .boltOnAdjournedHearingCount(1)
            .boltOnAdjournedHearingFee(new BigDecimal("2.5"))
            .boltOnCmrhTelephoneCount(2)
            .boltOnCmrhTelephoneFee(new BigDecimal("3.5"))
            .boltOnCmrhOralCount(1)
            .boltOnCmrhOralFee(new BigDecimal("4.5"))
            .boltOnHomeOfficeInterviewCount(1)
            .boltOnHomeOfficeInterviewFee(new BigDecimal("6.5"))
            .boltOnSubstantiveHearingFee(new BigDecimal("8.5"))
            .escapeCaseFlag(false)
            .schemeId("SCHEME1")
            .createdOn(now)
            .createdByUserId("12345")
            .build());

    return new SeededTarget(submission.getId(), claim.getId());
  }

  private ClaimPatch buildPatch(UUID claimId, String effectiveDate) {
    Claim claim = claimRepository.findById(require(claimId, "claimId")).orElseThrow();
    ClaimPatch patch = new ClaimPatch();
    patch.setVersion(
        context.getSubmittedVersionOverride() != null
            ? context.getSubmittedVersionOverride()
            : claim.getVersion());
    patch.setAmendmentRequestedBy(REQUESTED_BY_PROVIDER);
    patch.setAmendmentReasonCode(REASON_PROVIDER_ERROR);
    patch.setAmendmentUserId(VALID_USER_UUID);
    if (context.getAmendmentFeeCode() != null) {
      patch.setFeeCode(context.getAmendmentFeeCode());
    } else if (!context.isClassifierScenarioActive()) {
      patch.setFeeCode("FEE2");
    }
    if (effectiveDate != null) {
      patch.setCaseStartDate(LocalDate.parse(effectiveDate).format(API_DATE));
    }
    return patch;
  }

  private ClaimPatch buildClassifierPatch(UUID claimId) {
    Claim claim = claimRepository.findById(require(claimId, "claimId")).orElseThrow();
    ClaimPatch patch = new ClaimPatch();
    patch.setVersion(
        context.getSubmittedVersionOverride() != null
            ? context.getSubmittedVersionOverride()
            : claim.getVersion());
    patch.setAmendmentRequestedBy(REQUESTED_BY_PROVIDER);
    patch.setAmendmentReasonCode(REASON_PROVIDER_ERROR);
    patch.setAmendmentUserId(VALID_USER_UUID);

    Map<String, String> fields = new HashMap<>(context.getClassifierPatchFields());
    if (fields.containsKey("fee_code")) {
      patch.setFeeCode(translateFeeCode(fields.get("fee_code")));
    }
    if (fields.containsKey("case_start_date")) {
      String value = fields.get("case_start_date");
      patch.setCaseStartDate(value == null ? null : LocalDate.parse(value).format(API_DATE));
    }
    if (fields.containsKey("case_concluded_date")) {
      String value = fields.get("case_concluded_date");
      patch.setCaseConcludedDate(value == null ? null : LocalDate.parse(value).format(API_DATE));
    }
    if (fields.containsKey("representation_order_date")) {
      String value = fields.get("representation_order_date");
      patch.setRepresentationOrderDate(
          value == null ? null : LocalDate.parse(value).format(API_DATE));
    }
    if (fields.containsKey("ufn")) {
      patch.setUniqueFileNumber(fields.get("ufn"));
    }
    if (fields.containsKey("matter_type_code")) {
      patch.setMatterTypeCode(fields.get("matter_type_code"));
    }
    if (fields.containsKey("client_surname")) {
      patch.setClientSurname(fields.get("client_surname"));
    }
    if (fields.containsKey("client_forename")) {
      patch.setClientForename(fields.get("client_forename"));
    }
    if (fields.containsKey("client_reference")) {
      patch.setCaseReferenceNumber(fields.get("client_reference"));
    }
    return patch;
  }

  private PatchResult executePatch(
      UUID submissionId, UUID claimId, ClaimPatch patch, boolean updateLastResponse) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);

    long startNanos = System.nanoTime();
    int statusCode;
    String responseBody;
    try {
      ResponseEntity<String> response =
          restTemplate.exchange(
              serverInfo.baseUrl() + PATCH_CLAIM_PATH,
              HttpMethod.PATCH,
              new HttpEntity<>(patch, headers),
              String.class,
              submissionId,
              claimId);
      statusCode = response.getStatusCode().value();
      responseBody = response.getBody();
    } catch (HttpStatusCodeException ex) {
      statusCode = ex.getStatusCode().value();
      responseBody = ex.getResponseBodyAsString();
    }

    long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    if (updateLastResponse) {
      context.setLastStatusCode(statusCode);
      context.setLastResponseBody(responseBody);
      context.setLastPdaCallElapsedMillis(elapsedMs);
    }
    return new PatchResult(statusCode, responseBody, elapsedMs);
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }

  private static <T> T require(T value, String name) {
    if (value == null) {
      throw new IllegalStateException("Missing scenario state: " + name);
    }
    return value;
  }

  public static String normaliseOfficeCode(String office) {
    return office == null ? null : office.replaceAll("[^A-Za-z0-9]", "");
  }

  private static String translateFeeCode(String feeCode) {
    return switch (feeCode) {
      case "ASSA" -> "FEE1";
      case "IMCA" -> "FEE2";
      case "PROD-1" -> "PROD";
      case "NONPROD-1", "FEE-A" -> "FEE1";
      case "FEE-B", "FEE-C" -> "FEE2";
      default -> feeCode;
    };
  }

  private record SeededTarget(UUID submissionId, UUID claimId) {}

  private static final class PatchResult {
    private final int statusCode;
    private final String responseBody;

    @SuppressWarnings("unused")
    private final long elapsedMs;

    private PatchResult(int statusCode, String responseBody, long elapsedMs) {
      this.statusCode = statusCode;
      this.responseBody = responseBody;
      this.elapsedMs = elapsedMs;
    }

    private int statusCode() {
      return statusCode;
    }

    private String responseBody() {
      return responseBody;
    }
  }
}
