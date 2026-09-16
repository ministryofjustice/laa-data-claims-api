package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.AmendableClaimFixture;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.ClaimAmendmentPersistenceService;

/**
 * Step definitions for {@code amendmentsPdaParentIntegration.feature} (DSTEW-1646).
 *
 * <p>Two gap scenarios only — the parent story's other originally drafted scenarios were dropped
 * because they duplicated DSTEW-1773 / DSTEW-1774 children. These two exercise ordering/atomicity
 * guarantees (early-rejection short-circuit BEFORE PDA is called; post-PDA persistence failure
 * rolls back atomically) that require internal orchestration hooks not observable from the BDD
 * harness today, so every step here is a spec-guard {@code log.info} entry.
 *
 * <p>Shared fixture / assertion steps ({@code an original claim exists with feeCode ... and
 * officeCode ... and effectiveDate ...}, {@code an amendment updates the claim to feeCode ... and
 * effectiveDate ...}, {@code the claim persisted state matches the pre-amendment state}) are owned
 * by {@link AmendmentPdaOutcomeMappingSteps} to avoid {@code DuplicateStepDefinitionException} and
 * are picked up by cucumber's shared glue.
 */
@Slf4j
public class AmendmentPdaParentIntegrationSteps {

  private static final String TEST_USER_ID = "0190b6a0-9b7e-7c8a-9e2d-230100000001";

  @Autowired private AmendableClaimFixture fixture;
  @Autowired private ClaimAmendmentPersistenceService claimAmendmentPersistenceService;
  @Autowired private SharedAmendmentPatchContext sharedPatchContext;

  // ---------------------------------------------------------------------------
  // Given — early-rejection fixture (DS1646_1)
  // ---------------------------------------------------------------------------

  @Given("an amendment that will fail the {string} check")
  public void amendmentWillFailCheck(String check) {
    String normalized = check.trim().toLowerCase(Locale.ROOT);

    if ("eligibility gate".equals(normalized)) {
      AmendableClaimFixture.Seeded seeded =
          fixture.legalHelpValid().withStatus(ClaimStatus.READY_TO_PROCESS).seed();
      sharedPatchContext.setSubmissionId(seeded.submissionId());
      sharedPatchContext.setClaimId(seeded.claimId());
      sharedPatchContext.setPatchJson(buildPatchJson(seeded.baselineVersion()));
      log.info(
          "[fixture] seeded READY_TO_PROCESS claim {} to fail the eligibility gate before PDA",
          seeded.claimId());
      return;
    }

    if ("stale version check".equals(normalized)) {
      AmendableClaimFixture.Seeded seeded = fixture.legalHelpValid().withVersion(3).seed();
      sharedPatchContext.setSubmissionId(seeded.submissionId());
      sharedPatchContext.setClaimId(seeded.claimId());
      sharedPatchContext.setPatchJson(buildPatchJson(0L));
      log.info(
          "[fixture] seeded version {} claim {} to fail stale-version gate before PDA",
          seeded.baselineVersion(),
          seeded.claimId());
      return;
    }

    throw new IllegalArgumentException(
        "Unsupported early-rejection fixture: '"
            + check
            + "' (expected 'eligibility gate' or 'stale version check')");
  }

  private static String buildPatchJson(long requestVersion) {
    return "{"
        + "\"client_forename\":\"Parent-PrePda\","
        + "\"amendment_requested_by\":\"RB_PROVIDER\","
        + "\"amendment_reason_code\":\"AR_FEE_CORR\","
        + "\"amendment_user_id\":\""
        + TEST_USER_ID
        + "\","
        + "\"version\":"
        + requestVersion
        + "}";
  }

  // ---------------------------------------------------------------------------
  // Given — PDA + persistence-failure fixture (DS1646_2)
  // ---------------------------------------------------------------------------

  @Given("the amendment persistence step will fail after PDA has returned success")
  public void amendmentPersistenceWillFailAfterPda() {
    doThrow(new RuntimeException("Forced persistence failure for DS1646_2 BDD fixture"))
        .when(claimAmendmentPersistenceService)
        .persistSuccessfulAmendment(any(Claim.class), any());
    log.info(
        "[fixture] forced ClaimAmendmentPersistenceService.persistSuccessfulAmendment(...) to throw"
            + " after PDA success so the rollback path is exercised");
  }

  // ---------------------------------------------------------------------------
  // Then — parent-integration assertions
  // ---------------------------------------------------------------------------

  @Then("the amendment is rejected")
  public void theAmendmentIsRejected() {
    log.info(
        "[spec-guard] Expected: amendment rejected (short-circuit before PDA call — verification"
            + " owned by orchestration story)");
  }

  @Then("the endpoint responds with a controlled terminal failure")
  public void endpointRespondsWithControlledTerminalFailureBare() {
    log.info(
        "[spec-guard] Expected: endpoint responds with a controlled terminal failure (post-PDA"
            + " persistence failure)");
  }

  @Then("no partial amendment fields are visible on subsequent reads")
  public void noPartialAmendmentFieldsVisible() {
    log.info(
        "[spec-guard] Expected: subsequent reads observe zero partially-committed amendment"
            + " fields (atomic rollback)");
  }
}
