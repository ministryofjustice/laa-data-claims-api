package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.CucumberSpringConfiguration;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.BddMockServerSupport;

/**
 * Step definitions for {@code amendmentsFspOutcomeMapping.feature} (DSTEW-1761).
 *
 * <p>Arms the shared MockServer fee-calculation endpoint to elicit each FSP outcome under test
 * (business validation failure, connection drop, unparseable body, configured read-timeout, opaque
 * 5xx) and asserts the mapped amendment response text. The HTTP status + error-code assertions and
 * the no-persistence / call-count assertions reuse the DSTEW-2301 harness steps ({@code
 * AmendmentHarnessCommonSteps}, {@code AmendmentsFinalSaveGuardSteps}); only the FSP-arming givens
 * and the response-text assertions are new here.
 */
@Slf4j
public class AmendmentFspOutcomeMappingSteps {

  @Autowired private BddScenarioContext scenarioContext;
  @Autowired private BddMockServerSupport mock;

  // ---------------------------------------------------------------------------
  // Given — arm the FSP fee-calculation outcome
  // ---------------------------------------------------------------------------

  @Given("the FSP service will return a business validation failure")
  public void theFspServiceWillReturnAValidationFailure() {
    step(
        "arm FSP fee-calculation to return 200 with a single business validation ERROR message",
        () -> mock.stubAmendmentFspCalculationValidationError());
  }

  @Given("the FSP service will return multiple business validation failures")
  public void theFspServiceWillReturnMultipleValidationFailures() {
    step(
        "arm FSP fee-calculation to return 200 with multiple business validation ERROR messages",
        () -> mock.stubAmendmentFspCalculationMultipleErrors());
  }

  @Given("the FSP service will drop the connection")
  public void theFspServiceWillDropTheConnection() {
    step(
        "arm FSP fee-calculation to drop the connection",
        () -> mock.stubAmendmentFspCalculationConnectionDrop());
  }

  @Given("the FSP service will return a malformed response body")
  public void theFspServiceWillReturnAMalformedResponseBody() {
    step(
        "arm FSP fee-calculation to return 200 with an unparseable body",
        () -> mock.stubAmendmentFspCalculationMalformedBody());
  }

  @Given("the FSP service will not respond within the amendment-path timeout")
  public void theFspServiceWillNotRespondWithinTimeout() {
    step(
        "arm FSP fee-calculation to respond only after the amendment-path read timeout elapses",
        () ->
            // Delay well beyond the shortened amendment-path read timeout so the blocking call
            // aborts with a client-side timeout; the actual scenario wall-clock is the timeout, not
            // this delay (the client gives up first).
            mock.stubAmendmentFspCalculationWithDelay(
                Duration.ofMillis(
                    CucumberSpringConfiguration.AMENDMENT_FSP_READ_TIMEOUT_MS + 2000L)));
  }

  @Given("the FSP service will fail with HTTP {int} carrying the body {string}")
  public void theFspServiceWillFailWithHttpCarryingBody(int status, String body) {
    step(
        "arm FSP fee-calculation to return HTTP " + status + " carrying an opaque payload",
        () -> mock.stubAmendmentFspCalculationStatusWithBody(status, body));
  }

  // ---------------------------------------------------------------------------
  // Then — response-text assertions on the mapped amendment response
  // ---------------------------------------------------------------------------

  @Then("the FSP outcome response contains the text {string}")
  public void theFspOutcomeResponseContainsTheText(String expected) {
    step(
        "assert the amendment response body contains \"" + expected + "\"",
        () -> {
          JsonNode body = scenarioContext.getLastResponseBody();
          assertThat(body).as("amendment response body").isNotNull();
          assertThat(body.toString())
              .as("mapped FSP outcome response body must surface the expected text")
              .contains(expected);
        });
  }

  @Then("the FSP outcome response does not contain the text {string}")
  public void theFspOutcomeResponseDoesNotContainTheText(String forbidden) {
    step(
        "assert the amendment response body does NOT contain \"" + forbidden + "\"",
        () -> {
          JsonNode body = scenarioContext.getLastResponseBody();
          assertThat(body).as("amendment response body").isNotNull();
          assertThat(body.toString())
              .as("controlled technical response must not leak the raw FSP payload")
              .doesNotContain(forbidden);
        });
  }
}
