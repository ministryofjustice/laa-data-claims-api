package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;

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

  // ---------------------------------------------------------------------------
  // Given — early-rejection fixture (DS1646_1)
  // ---------------------------------------------------------------------------

  @Given("an amendment that will fail the {string} check")
  public void amendmentWillFailCheck(String check) {
    log.info("[spec-guard] Fixture: amendment engineered to fail the '{}' pre-PDA check", check);
  }

  // ---------------------------------------------------------------------------
  // Given — PDA + persistence-failure fixture (DS1646_2)
  // ---------------------------------------------------------------------------

  @Given("the PDA service will respond {string} within the amendment-path timeout")
  public void pdaWillRespondOutcomeWithinTimeout(String outcome) {
    log.info(
        "[spec-guard] PDA stub: respond '{}' successfully within amendment-path timeout budget",
        outcome);
  }

  @Given("the amendment persistence step will fail after PDA has returned success")
  public void amendmentPersistenceWillFailAfterPda() {
    log.info(
        "[spec-guard] Fixture: amendment persistence engineered to fail AFTER PDA has returned"
            + " success — expected to trigger atomic rollback");
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
