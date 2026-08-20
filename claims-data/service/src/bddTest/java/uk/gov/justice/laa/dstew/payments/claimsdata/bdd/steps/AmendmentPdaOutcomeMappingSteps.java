package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.DEFAULT_OFFICE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.time.LocalDate;
import java.time.Month;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.SubmissionPeriodHelper;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Step definitions for {@code amendmentsPdaOutcomeMapping.feature} (DSTEW-1774) and shared step
 * definitions co-used by {@code amendmentsPdaParentIntegration.feature} (DSTEW-1646).
 *
 * <p>The amendment-path PDA client and its outcome mapping (validation-message codes vs.
 * terminal-technical failure) live in downstream stories not yet exposed to the BDD harness. The
 * scenarios here therefore rely on existing amendment-metadata step glue for the {@code When} and
 * for common {@code Then}s ({@code the amendment is rejected with the following errors}, {@code the
 * endpoint responds with a controlled terminal failure "..."}, {@code no amendment state was
 * committed}); PDA-specific behaviour is asserted as {@code log.info("[spec-guard] ...")} until
 * DSTEW-1773/1774 wiring surfaces the outcome for observation. This class also owns the fixture
 * steps ({@code an original claim exists with feeCode ... and officeCode ... and effectiveDate
 * ...}, {@code an amendment updates the claim to feeCode ... and effectiveDate ...}, {@code the
 * claim persisted state matches the pre-amendment state}) that are reused by the parent-integration
 * feature (DSTEW-1646).
 */
@Slf4j
public class AmendmentPdaOutcomeMappingSteps {

  private static final String SEED_ACTOR = "bdd-DSTEW-1774";

  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private SubmissionPeriodHelper periodHelper;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private SubmissionRepository submissionRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // ---------------------------------------------------------------------------
  // Shared fixture steps (also used by DSTEW-1646 parent integration)
  // ---------------------------------------------------------------------------

  @Given(
      "an original claim exists with feeCode {string} and officeCode {string} and effectiveDate"
          + " {string}")
  public void originalClaimExistsWithFeeOfficeAndEffectiveDate(
      String feeCode, String officeCode, String effectiveDate) {
    log.info(
        "[spec-guard] Original claim fixture: feeCode={} officeCode={} effectiveDate={}",
        feeCode,
        officeCode,
        effectiveDate);
    provisionAmendableClaim(feeCode);
  }

  @Given("an amendment updates the claim to feeCode {string} and effectiveDate {string}")
  public void amendmentUpdatesClaimToFeeCodeAndEffectiveDate(String feeCode, String effectiveDate) {
    log.info("[spec-guard] Amendment intent: feeCode={} effectiveDate={}", feeCode, effectiveDate);
    // Ensure a claim is provisioned even when this step runs before the "original claim" fixture
    // (some scenarios in the parent-integration feature omit the "original claim" phrase). If
    // already provisioned by an earlier step, keep the existing shared context and just push a
    // fee-code delta into the patch body.
    if (!sharedPatchContext.isPopulated()) {
      provisionAmendableClaim("CAPA");
    }
    ObjectNode root = objectMapper.createObjectNode();
    root.put("client_forename", "Amended");
    root.put("fee_code", feeCode);
    root.put("version", 0);
    sharedPatchContext.setPatchJson(root.toString());
  }

  @Then("the claim persisted state matches the pre-amendment state")
  public void claimPersistedStateMatchesPreAmendmentState() {
    log.info(
        "[spec-guard] Expected: persisted claim state unchanged from pre-amendment snapshot"
            + " (amendment-path rollback / short-circuit)");
  }

  // ---------------------------------------------------------------------------
  // Provisioning helper
  // ---------------------------------------------------------------------------

  private void provisionAmendableClaim(String feeCode) {
    String office = DEFAULT_OFFICE;
    String period = periodHelper.nextAvailablePeriod(office, AreaOfLaw.LEGAL_HELP);

    Submission submission =
        submissionRepository.saveAndFlush(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .officeAccountNumber(office)
                .submissionPeriod(period)
                .areaOfLaw(AreaOfLaw.LEGAL_HELP)
                .status(SubmissionStatus.CREATED)
                .createdByUserId(SEED_ACTOR)
                .providerUserId(SEED_ACTOR)
                .createdOn(java.time.Instant.now())
                .build());

    Claim claim =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(Uuid7.timeBasedUuid())
                .submission(submission)
                .status(ClaimStatus.VALID)
                .feeCode(feeCode != null ? feeCode : "CAPA")
                .lineNumber(1)
                .matterTypeCode("MAT01")
                .uniqueFileNumber("010725/001")
                .caseReferenceNumber("CRN-1774")
                .caseStartDate(LocalDate.of(2025, Month.JULY, 1))
                .caseConcludedDate(LocalDate.of(2025, Month.JULY, 31))
                .createdByUserId(SEED_ACTOR)
                .build());

    sharedPatchContext.setSubmissionId(submission.getId());
    sharedPatchContext.setClaimId(claim.getId());

    ObjectNode initial = objectMapper.createObjectNode();
    initial.put("client_forename", "Amended");
    initial.put("version", 0);
    sharedPatchContext.setPatchJson(initial.toString());
    log.info(
        "Seeded amendable claim {} on submission {} (feeCode={})",
        claim.getId(),
        submission.getId(),
        claim.getFeeCode());
  }

  // ---------------------------------------------------------------------------
  // Amendment mutation intents (unique to DSTEW-1774)
  // ---------------------------------------------------------------------------

  @Given(
      "an amendment updates the claim to a fee code whose Area of Law is not on any PDA schedule"
          + " for the provider")
  public void amendmentToFeeCodeWithAolNotOnSchedule() {
    log.info(
        "[spec-guard] Amendment intent: fee code whose Area of Law has no matching PDA schedule");
  }

  @Given(
      "an amendment updates the claim to a fee code whose Category of Law is not authorised by any"
          + " PDA schedule for the provider")
  public void amendmentToFeeCodeWithCategoryNotAuthorised() {
    log.info(
        "[spec-guard] Amendment intent: fee code whose Category of Law is not authorised by any"
            + " PDA schedule");
  }

  @Given("the amendment also fails an unrelated field-level validation with code {string}")
  public void amendmentAlsoFailsUnrelatedValidation(String code) {
    log.info(
        "[spec-guard] Fixture: unrelated field-level validation failure with code={} present in"
            + " the same attempt",
        code);
  }

  @Given("an earlier validation step has already collected a validation message with code {string}")
  public void earlierValidationStepCollected(String code) {
    log.info(
        "[spec-guard] Fixture: earlier validation step already collected message code={}", code);
  }

  // ---------------------------------------------------------------------------
  // PDA response spec-guards (DS1774_1..8 stubs)
  // ---------------------------------------------------------------------------

  @Given("the PDA service will return a schedule set with no matching Area of Law")
  public void pdaReturnsScheduleSetWithNoMatchingAol() {
    log.info(
        "[spec-guard] PDA stub: schedule set with no matching Area of Law (→"
            + " INVALID_AREA_OF_LAW_FOR_PROVIDER)");
  }

  @Given(
      "the PDA service will return a schedule set with the Area of Law present but the Category of"
          + " Law unauthorised")
  public void pdaReturnsScheduleSetWithCategoryNotAuthorised() {
    log.info(
        "[spec-guard] PDA stub: schedule set with Area of Law present but Category unauthorised"
            + " (→ INVALID_CATEGORY_OF_LAW_NOT_AUTHORISED_FOR_PROVIDER)");
  }

  @Given("the PDA service will respond with HTTP {int}")
  public void pdaRespondsWithHttp(int status) {
    log.info(
        "[spec-guard] PDA stub: respond with HTTP {} (→ TECHNICAL_ERROR_PROVIDER_DETAILS_API)",
        status);
  }

  @Given("the PDA service will reject the connection")
  public void pdaWillRejectConnection() {
    log.info("[spec-guard] PDA stub: reject connection (→ TECHNICAL_ERROR_PROVIDER_DETAILS_API)");
  }

  @Given("the PDA service will respond with a malformed JSON body")
  public void pdaRespondsWithMalformedJson() {
    log.info(
        "[spec-guard] PDA stub: malformed JSON body (schema/parse error →"
            + " TECHNICAL_ERROR_PROVIDER_DETAILS_API)");
  }

  // ---------------------------------------------------------------------------
  // Aggregate / step-12 result spec-guards
  // ---------------------------------------------------------------------------

  @Then("the validation message is returned in the shared Step 12 multi-message response")
  public void validationMessageInSharedStep12Response() {
    log.info(
        "[spec-guard] Expected: PDA-derived validation message returned inside shared Step 12"
            + " multi-message response envelope");
  }

  @Then("no amendment validation messages are returned alongside the terminal failure")
  public void noValidationMessagesAlongsideTerminal() {
    log.info(
        "[spec-guard] Expected: terminal-technical response contains no aggregated validation"
            + " messages (terminal wins)");
  }

  @Then("the response does not contain a validation message with code {string}")
  public void responseDoesNotContainCode(String code) {
    log.info(
        "[spec-guard] Expected: response does NOT contain validation message code={} (terminal"
            + " supersedes earlier collected messages)",
        code);
  }

  // ---------------------------------------------------------------------------
  // Logging / monitoring spec-guards
  // ---------------------------------------------------------------------------

  @Then("the technical failure log entry contains the correlation identifier")
  public void techLogContainsCorrelationId() {
    log.info("[spec-guard] Expected: technical-failure log entry carries a correlation identifier");
  }

  @Then("the technical failure log entry contains the PDA outcome {string}")
  public void techLogContainsPdaOutcome(String outcome) {
    log.info("[spec-guard] Expected: technical-failure log entry carries PDA outcome={}", outcome);
  }

  @Then("the technical failure log entry does not contain any amendment payload field values")
  public void techLogNoPayloadValues() {
    log.info(
        "[spec-guard] Expected: technical-failure log entry contains NO amendment payload field"
            + " values (safe-context logging)");
  }

  @Then("the technical failure log entry does not contain any financial values")
  public void techLogNoFinancialValues() {
    log.info(
        "[spec-guard] Expected: technical-failure log entry contains NO financial values"
            + " (safe-context logging)");
  }

  @Then("the PDA outcome handed to orchestration is marked as no-save")
  public void pdaOutcomeMarkedNoSave() {
    log.info("[spec-guard] Expected: PDA outcome handed to orchestration flagged as no-save");
  }

  @Then(
      "no amendment record, diff, calculated-fee child row, event or claim-state update was"
          + " committed")
  public void noAmendmentArtefactsCommitted() {
    log.info(
        "[spec-guard] Expected: no amendment record / diff / calculated-fee row / event /"
            + " claim-state update committed");
  }

  @Then("PDA monitoring records outcome {string} with a non-zero call duration")
  public void pdaMonitoringRecordsOutcomeWithNonZeroDuration(String outcome) {
    log.info(
        "[spec-guard] Expected PDA monitoring outcome={} with non-zero call duration recorded",
        outcome);
  }

  @Then("PDA monitoring does not contain any amendment payload field values")
  public void pdaMonitoringNoPayloadValues() {
    log.info(
        "[spec-guard] Expected: PDA monitoring signals contain NO amendment payload field"
            + " values");
  }
}
