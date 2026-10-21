package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;

/**
 * Step glue for {@code amendmentsFspTriggerDecision.feature} — DSTEW-1758.
 *
 * <p>Backs the Step 13 ({@code AmendmentFspValidationStep}) FSP skip/continue decision. Almost
 * every phrase is reused:
 *
 * <ul>
 *   <li>seeding + the pricing / non-pricing submit + the FSP-call verifications come from the
 *       DSTEW-2301 harness ({@code AmendmentHarnessCommonSteps});
 *   <li>the assessed-claim + field-amendability provisioning and the {@code the response (does not
 *       )contain(s) error code ...} assertions come from the DSTEW-1767 steps.
 * </ul>
 *
 * <p>Only the "no unchanged input repricing" safeguard needs a bespoke submit: a payload that
 * restates an FSP request-body field (case_start_date) at its CURRENT seeded value — so it is not
 * an effective change — alongside a genuine non-pricing change (client_forename). The diff
 * therefore carries no pricing-impacting change, and Step 13 must skip FSP rather than reprice to
 * refresh historical pricing.
 *
 * <p>Every step body is wrapped in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures#step}.
 */
@Slf4j
public class AmendmentFspTriggerDecisionSteps {

  // Mirrors the DSTEW-2301 harness metadata so the amendment is well-formed and the only diff is
  // the
  // non-pricing change (plus the restated, unchanged FSP-input field).
  private static final String AMENDMENT_USER_ID = "0190b6a0-9b7e-7c8a-9e2d-230100000001";
  // The fixture seeds caseStartDate = 01/07/2025 (AmendableClaimFixture.seedClaim). Restating this
  // exact value means case_start_date is present in the payload but is NOT an effective change.
  private static final String SEEDED_CASE_START_DATE = "01/07/2025";

  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext scenarioContext;
  @Autowired private SharedAmendmentPatchContext sharedPatchContext;

  @When(
      "I submit an amendment that restates an FSP-input field at its current value and changes only"
          + " a non-pricing field")
  public void iSubmitAnAmendmentRestatingAnFspInputFieldUnchanged() {
    step(
        "PATCH the amendment endpoint with a payload that restates case_start_date at its seeded"
            + " value (no effective change) and changes only client_forename — so no"
            + " pricing-impacting field actually changes and Step 13 must skip FSP",
        () -> {
          assertThat(sharedPatchContext.isPopulated())
              .as("an amendable claim must be seeded before submitting the unchanged-input payload")
              .isTrue();
          long version =
              sharedPatchContext.getBaselineClaimVersion() != null
                  ? sharedPatchContext.getBaselineClaimVersion()
                  : 0L;
          String patch =
              "{\"version\":"
                  + version
                  + ",\"amendment_requested_by\":\"PROVIDER\""
                  + ",\"amendment_reason_code\":\"PROVIDER_ERROR\""
                  + ",\"amendment_user_id\":\""
                  + AMENDMENT_USER_ID
                  + "\""
                  // Restated unchanged FSP-input field — present but not an effective change.
                  + ",\"case_start_date\":\""
                  + SEEDED_CASE_START_DATE
                  + "\""
                  // The only effective change — a non-pricing field.
                  + ",\"client_forename\":\"Restated-Canary\"}";
          sharedPatchContext.setPatchJson(patch);
          api.patchClaimAmendment(
              sharedPatchContext.getSubmissionId(), sharedPatchContext.getClaimId(), patch);
          log.info(
              "[DSTEW-1758] PATCH unchanged-FSP-input amendment for claim {} → status={}",
              sharedPatchContext.getClaimId(),
              scenarioContext.getLastStatusCode());
        });
  }
}
