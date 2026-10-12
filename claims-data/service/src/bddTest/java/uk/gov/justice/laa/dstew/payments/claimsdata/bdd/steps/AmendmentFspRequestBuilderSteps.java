package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddApiStepSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.BddMockServerSupport;

/**
 * Step glue for {@code amendmentsFspRequestBuilder.feature} — DSTEW-1759.
 *
 * <p>Proves {@code FeeSchemeRequestBuilder} builds the outbound FSP fee-calculation request from
 * the fully-merged post-amendment claim state: changed FSP-input fields carry the provider-entered
 * value, omitted fields carry the current stored value (sparse merge applied upstream).
 *
 * <p>Seeding, the well-formed pricing submit and the FSP-call verifications are reused from the
 * DSTEW-2301 harness. Local to this class:
 *
 * <ul>
 *   <li>a parameterised pricing submit that changes a single named claim field (so a second
 *       representative FSP-input field — case_concluded_date — can be exercised alongside the
 *       harness's case_start_date pricing patch);
 *   <li>the assertion over the real outbound fee-calculation request body captured by MockServer.
 * </ul>
 *
 * <p>Every step body is wrapped in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures#step}.
 */
@Slf4j
public class AmendmentFspRequestBuilderSteps {

  // Mirrors the DSTEW-2301 harness metadata so the only diff is the single changed field.
  private static final String AMENDMENT_USER_ID = "0190b6a0-9b7e-7c8a-9e2d-230100000001";

  @Autowired private BddApiStepSupport api;
  @Autowired private BddScenarioContext scenarioContext;
  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private BddMockServerSupport mock;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @When("I submit a pricing amendment changing the claim field {string} to {string}")
  public void iSubmitAPricingAmendmentChangingClaimField(String field, String value) {
    step(
        "PATCH the amendment endpoint changing only " + field + " to " + value,
        () -> {
          assertThat(sharedPatchContext.isPopulated())
              .as("an amendable claim must be seeded before submitting the pricing amendment")
              .isTrue();
          long version =
              sharedPatchContext.getBaselineClaimVersion() != null
                  ? sharedPatchContext.getBaselineClaimVersion()
                  : 0L;
          String payloadKey = "ufn".equals(field) ? "unique_file_number" : field;
          String patch =
              "{\"version\":"
                  + version
                  + ",\"amendment_requested_by\":\"PROVIDER\""
                  + ",\"amendment_reason_code\":\"PROVIDER_ERROR\""
                  + ",\"amendment_user_id\":\""
                  + AMENDMENT_USER_ID
                  + "\",\""
                  + payloadKey
                  + "\":\""
                  + value
                  + "\"}";
          sharedPatchContext.setPatchJson(patch);
          api.patchClaimAmendment(
              sharedPatchContext.getSubmissionId(), sharedPatchContext.getClaimId(), patch);
          log.info(
              "[DSTEW-1759] PATCH pricing amendment ({}={}) for claim {} → status={}",
              field,
              value,
              sharedPatchContext.getClaimId(),
              scenarioContext.getLastStatusCode());
        });
  }

  @Then("the outbound FSP request field {string} equals {string}")
  public void theOutboundFspRequestFieldEquals(String field, String expected) {
    step(
        "assert the captured FSP fee-calculation request body's \""
            + field
            + "\" equals \""
            + expected
            + "\"",
        () -> {
          String body = mock.firstFspCalculationRequestBody();
          assertThat(body)
              .as("an outbound FSP fee-calculation request body must have been recorded")
              .isNotNull();
          log.info("[DSTEW-1759] Captured FSP request body: {}", body);
          JsonNode root = objectMapper.readTree(body);
          JsonNode node = root.path(field);
          assertThat(node.isMissingNode())
              .as("FSP request body must carry the field \"%s\" (body=%s)", field, body)
              .isFalse();
          assertThat(node.asText())
              .as("FSP request field \"%s\" (body=%s)", field, body)
              .isEqualTo(expected);
        });
  }
}
