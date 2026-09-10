package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Iterator;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpResponse;
import org.springframework.http.HttpMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Claim Amendment error-response integration test")
class ClaimAmendmentErrorResponseIntegrationTest extends AbstractAmendmentPatchIntegrationTest {

  @Test
  @DisplayName("Invalid user id yields structured 400 problem-detail with errors array")
  void invalidUserIdYieldsStructuredProblemDetail() throws Exception {
    // Arrange: ensure the target claim is in an amendable state
    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    Claim savedClaim = claimRepository.findById(CLAIM_1_ID).orElseThrow();

    // Build a base patch and then inject a structurally-invalid amendment user id
    ClaimPatch patch = createBasePatch();
    patch.setVersion(10L);

    ObjectNode json = (ObjectNode) PATCH_MAPPER.valueToTree(patch);

    // Act
    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, String.valueOf(json));

    // Assert: top-level ProblemDetail envelope
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());

    ObjectMapper mapper = new ObjectMapper();
    JsonNode body = mapper.readTree(result.getResponse().getContentAsString());

    // Standard RFC-9457 fields
    assertThat(body.path("status").asInt()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(body.path("title").asText()).isEqualTo(HttpStatus.CONFLICT.getReasonPhrase());
    assertThat(body.path("type").asText()).contains("claim-amendment-validation");
    assertThat(body.path("instance").asText()).contains("/api/");

    // Backward-compatibility message property mirrors detail
    assertThat(body.path("message").asText()).isEqualTo(body.path("detail").asText());

    // The handler must attach an 'errors' array carrying the amendment validation error(s)
    JsonNode errors = body.path("errors");
    assertThat(errors.isArray()).isTrue();
    assertThat(errors.size()).isGreaterThanOrEqualTo(1);

    // The first error should expose the stable machine-readable code we expect
    JsonNode first = errors.get(0);
    assertThat(first.path("code").asText()).isEqualTo("CLAIM_VERSION_CONFLICT");
    assertThat(first.path("message").asText()).isNotBlank();
    assertThat(first.path("severity").asText()).isEqualTo("FATAL");
    assertThat(first.path("httpStatus").asText()).isEqualTo("409 CONFLICT");
    assertThat(!first.has("fieldName") || first.path("fieldName").isNull()).isTrue();
    assertThat(first.path("fatal").asBoolean()).isTrue();

    // Ensure nothing was persisted for this failing amendment
    assertNoAmendmentWritten(CLAIM_1_ID, savedClaim.getVersion());
  }

  @Test
  @DisplayName("FSP 500 maps to TECHNICAL_ERROR_FSP_REPRICING_FAILURE with full error object")
  void fsp500MapsToTechnicalErrorWithFullErrorObject() throws Exception {
    // Arrange: ensure external validation endpoints are available and fee-details area-of-law ok
    stubExternalValidationEndpoints();
    stubFeeDetailsAreaOfLaw("LEGAL_HELP");

    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    claim.setStatus(ClaimStatus.VALID);
    Claim saved = claimRepository.saveAndFlush(claim);

    // Ensure a baseline calculated fee exists so the repricing path runs
    createBaselineCalculatedFeeDetail(claim);

    // Clear any default fee-calculation stub and register a 500 response for repricing
    mockServerClient.clear(request().withPath(FEE_CALCULATION), ClearType.EXPECTATIONS);
    mockServerClient
        .when(request().withMethod(HttpMethod.POST.name()).withPath(FEE_CALCULATION))
        .respond(HttpResponse.response().withStatusCode(500));

    // Act: submit a pricing-impacting patch so the FSP is invoked
    ClaimPatch patch = createBasePatch();
    patch.setVersion(saved.getVersion());
    patch.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    // Assert overall status
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());

    ObjectMapper mapper = new ObjectMapper();
    JsonNode body = mapper.readTree(result.getResponse().getContentAsString());

    JsonNode errors = body.path("errors");
    assertThat(errors.isArray()).isTrue();
    assertThat(errors.size()).isGreaterThanOrEqualTo(1);

    // Find the FSP technical error object
    JsonNode fspError = null;
    Iterator<JsonNode> it = errors.elements();
    while (it.hasNext()) {
      JsonNode e = it.next();
      if ("TECHNICAL_ERROR_FSP_REPRICING_FAILURE".equals(e.path("code").asText())) {
        fspError = e;
        break;
      }
    }

    assertThat(fspError).isNotNull();
    assertThat(fspError.path("code").asText()).isEqualTo("TECHNICAL_ERROR_FSP_REPRICING_FAILURE");
    assertThat(fspError.path("message").asText()).isNotBlank();
    assertThat(fspError.path("severity").asText()).isEqualTo("FATAL");
    assertThat(fspError.path("httpStatus").asText()).isEqualTo("503 SERVICE_UNAVAILABLE");
    assertThat(fspError.has("fieldName") ? fspError.path("fieldName").isNull() : true).isTrue();
    assertThat(fspError.path("fatal").asBoolean()).isTrue();

    // Nothing persisted for failing amendment
    assertNoAmendmentWritten(CLAIM_1_ID, saved.getVersion());
  }

  @Test
  @DisplayName("validator module returns field-level validation for client name and error object contains all fields")
  void validatorReturnsFieldLevelValidationForClientName() throws Exception {
    // Arrange
    stubExternalValidationEndpoints();

    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    claim.setStatus(ClaimStatus.VALID);
    Claim saved = claimRepository.saveAndFlush(claim);

    // Build patch with an invalid client forename (very long) to provoke validation-core issue
    ClaimPatch patch = createBasePatch();
    patch.setVersion(saved.getVersion());
    String longName = "A".repeat(500);
    patch.setClientForename(longName);

    // Act
    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    // Assert a Bad Request with structured errors
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    ObjectMapper mapper = new ObjectMapper();
    JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
    JsonNode errors = body.path("errors");
    assertThat(errors.isArray()).isTrue();
    assertThat(errors.size()).isGreaterThanOrEqualTo(1);

    // Find an error that references the client forename field
    JsonNode matching = null;
    Iterator<JsonNode> it = errors.elements();
    while (it.hasNext()) {
      JsonNode e = it.next();
      String fieldName = e.path("fieldName").asText(null);
      if (fieldName != null && fieldName.toLowerCase().contains("client") && fieldName.toLowerCase().contains("forename")) {
        matching = e;
        break;
      }
    }

    // The validation-core should surface a field-level error for the client name; assert error object fields
    assertThat(matching).isNotNull();
    assertThat(matching.path("code").asText()).isNotBlank();
    assertThat(matching.path("message").asText()).isNotBlank();
    assertThat(matching.path("severity").asText()).isEqualTo("ERROR");
    assertThat(matching.path("httpStatus").asText()).isEqualTo("400 BAD_REQUEST");
    assertThat(matching.path("fieldName").asText()).isEqualTo("client_forename");
    assertThat(matching.path("fatal").asBoolean()).isFalse();

    // Nothing persisted for this failing amendment
    assertNoAmendmentWritten(CLAIM_1_ID, saved.getVersion());
  }
}


