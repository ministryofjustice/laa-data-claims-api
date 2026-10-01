package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.cucumber.java.en.Given;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.ClaimAmendmentPersistenceService;

/**
 * Step definitions for {@code amendmentsFspParentIntegration.feature} (DSTEW-1595).
 *
 * <p>This parent story owns only the single cross-cutting seam that no FSP child feature exercises:
 * forcing the amendment persistence step to throw <em>after</em> the FSP fee-calculation call has
 * already returned success, so the post-FSP rollback path (DS1595_1) is genuinely driven. Every
 * other step the feature uses is reused from the shared amendment harness:
 *
 * <ul>
 *   <li>seeding + pricing submit + no-write DB assertions → {@code AmendmentHarnessCommonSteps}
 *       (DSTEW-2301),
 *   <li>Background feature-flag / PDA-trigger / PDA-response → {@code AmendmentsFeatureFlagSteps} /
 *       {@code AmendmentPdaCallMechanicsSteps} / {@code AmendmentHarnessCommonSteps},
 *   <li>controlled-terminal-failure + no-partial-fields spec-guards → {@code
 *       AmendmentPdaParentIntegrationSteps} (DSTEW-1646 precedent),
 *   <li>assessed-claim provisioning + classifier + error-code assertion (DS1595_2) → {@code
 *       AmendmentAssessedPricingAndAmendabilitySteps} (DSTEW-1767) / {@code
 *       AmendmentsEligibilityGateSteps}.
 * </ul>
 *
 * <p>The {@code ClaimAmendmentPersistenceService} injected here is the {@code @MockitoSpyBean}
 * declared in {@code CucumberSpringConfiguration} (shared with the DSTEW-1646 PDA parent, which
 * forces the same method to throw after PDA success); the per-scenario reset hook clears the stub
 * between scenarios.
 */
@Slf4j
public class AmendmentFspParentIntegrationSteps {

  @Autowired private ClaimAmendmentPersistenceService claimAmendmentPersistenceService;

  @Given("the amendment persistence step will fail after FSP has returned success")
  public void amendmentPersistenceWillFailAfterFsp() {
    doThrow(new RuntimeException("Forced persistence failure for DS1595_1 BDD fixture (post-FSP)"))
        .when(claimAmendmentPersistenceService)
        .persistSuccessfulAmendment(any(Claim.class), any());
    log.info(
        "[fixture][DSTEW-1595] forced ClaimAmendmentPersistenceService.persistSuccessfulAmendment"
            + "(...) to throw after FSP success so the post-FSP rollback path is exercised");
  }
}
