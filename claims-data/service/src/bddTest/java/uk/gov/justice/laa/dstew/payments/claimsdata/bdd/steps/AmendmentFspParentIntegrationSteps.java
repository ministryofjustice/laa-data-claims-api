package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
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
  @Autowired private BddScenarioContext scenarioContext;

  @Given("the amendment persistence step will fail after FSP has returned success")
  public void amendmentPersistenceWillFailAfterFsp() {
    doThrow(new RuntimeException("Forced persistence failure for DS1595_1 BDD fixture (post-FSP)"))
        .when(claimAmendmentPersistenceService)
        .persistSuccessfulAmendment(any(Claim.class), any());
    log.info(
        "[fixture][DSTEW-1595] forced ClaimAmendmentPersistenceService.persistSuccessfulAmendment"
            + "(...) to throw after FSP success so the post-FSP rollback path is exercised");
  }

  /**
   * Real status/body assertion for the post-FSP persistence-failure path (DS1595_1). The forced
   * {@link RuntimeException} thrown from {@code persistSuccessfulAmendment} is uncaught by the
   * amendment orchestrator, so it surfaces through {@code
   * DataClaimsExceptionHandler#handleGenericException} as an HTTP 500 carrying an RFC 9457
   * ProblemDetail. Unlike the DSTEW-1646 {@code "controlled terminal failure"} spec-guard (which
   * only logs), this inspects {@link BddScenarioContext} so the scenario fails if the exception
   * were exposed with the wrong status or swallowed.
   */
  @Then("the endpoint responds with a controlled post-FSP persistence failure")
  public void endpointRespondsWithControlledPostFspPersistenceFailure() {
    Integer status = scenarioContext.getLastStatusCode();
    JsonNode body = scenarioContext.getLastResponseBody();
    assertThat(status)
        .as("post-FSP persistence failure must produce an HTTP response (body=%s)", body)
        .isNotNull();
    assertThat(status)
        .as("an uncaught persistence failure must surface as a controlled 500 (body=%s)", body)
        .isEqualTo(500);
    assertThat(body).as("the 500 response must carry an RFC 9457 ProblemDetail body").isNotNull();
    assertThat(body.path("status").asInt())
        .as("the ProblemDetail status field must mirror the 500 HTTP status (body=%s)", body)
        .isEqualTo(500);
  }
}
