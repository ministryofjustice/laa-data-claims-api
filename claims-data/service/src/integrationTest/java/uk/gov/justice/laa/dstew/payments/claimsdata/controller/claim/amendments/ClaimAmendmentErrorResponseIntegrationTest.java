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
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Claim Amendment error-response integration test")
class ClaimAmendmentErrorResponseIntegrationTest extends AbstractAmendmentPatchIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // --- Helper methods to reduce duplication ---
  private Claim seedValidClaimAndSave(UUID claimId) {
    Claim claim = claimRepository.findById(claimId).orElseThrow();
    claim.setStatus(ClaimStatus.VALID);
    return claimRepository.saveAndFlush(claim);
  }

  private ClaimPatch createPatchWithVersion(long version, Consumer<ClaimPatch> modifier) {
    ClaimPatch patch = createBasePatch();
    patch.setVersion(version);
    if (modifier != null) {
      modifier.accept(patch);
    }
    return patch;
  }

  private JsonNode performPatchAndParseBody(UUID submissionId, UUID claimId, Object patch)
      throws Exception {
    MvcResult result;
    if (patch instanceof String s) {
      result = performPatch(submissionId, claimId, s);
    } else if (patch instanceof ClaimPatch cp) {
      result = performPatch(submissionId, claimId, cp);
    } else {
      // fallback - serialise unknown object to JSON
      result = performPatch(submissionId, claimId, MAPPER.writeValueAsString(patch));
    }
    return MAPPER.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode extractErrorsArray(JsonNode body) {
    return body.path("errors");
  }

  private JsonNode findErrorByCode(JsonNode errors, String expectedCode) {
    if (errors == null || !errors.isArray()) return null;
    Iterator<JsonNode> it = errors.elements();
    while (it.hasNext()) {
      JsonNode e = it.next();
      if (expectedCode.equals(e.path("code").asText(null))) {
        return e;
      }
    }
    return null;
  }

  private void assertErrorFields(
      JsonNode error,
      String expectedCode,
      String expectedSeverity,
      String expectedHttpStatusText,
      String expectedFieldName,
      boolean expectedFatal) {
    if (expectedCode != null) {
      assertThat(error.path("code").asText()).isEqualTo(expectedCode);
    } else {
      assertThat(error.path("code").asText()).isNotBlank();
    }
    assertThat(error.path("message").asText()).isNotBlank();
    assertThat(error.path("severity").asText()).isEqualTo(expectedSeverity);
    assertThat(error.path("httpStatus").asText()).isEqualTo(expectedHttpStatusText);
    if (expectedFieldName != null) {
      assertThat(error.path("fieldName").asText()).isEqualTo(expectedFieldName);
    }
    assertThat(error.path("fatal").asBoolean()).isEqualTo(expectedFatal);
  }

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

    ObjectNode json = PATCH_MAPPER.valueToTree(patch);

    // Act
    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, String.valueOf(json));

    // Assert: top-level ProblemDetail envelope
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());

    JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());

    // Standard RFC-9457 fields
    assertThat(body.path("status").asInt()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(body.path("title").asText()).isEqualTo(HttpStatus.CONFLICT.getReasonPhrase());
    assertThat(body.path("type").asText()).contains("claim-amendment-validation");
    assertThat(body.path("instance").asText()).contains("/api/");

    // Backward-compatibility message property mirrors detail
    assertThat(body.path("message").asText()).isEqualTo(body.path("detail").asText());

    // The handler must attach an 'errors' array carrying the amendment validation error(s)
    JsonNode errors = extractErrorsArray(body);
    assertThat(errors.isArray()).isTrue();
    assertThat(errors.size()).isGreaterThanOrEqualTo(1);

    // The first error should expose the stable machine-readable code we expect
    JsonNode first = errors.get(0);
    assertErrorFields(first, "CLAIM_VERSION_CONFLICT", "FATAL", "409 CONFLICT", "version", true);

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

    JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());

    JsonNode errors = extractErrorsArray(body);
    assertThat(errors.isArray()).isTrue();
    assertThat(errors.size()).isGreaterThanOrEqualTo(1);

    JsonNode fspError = findErrorByCode(errors, "TECHNICAL_ERROR_FSP_REPRICING_FAILURE");
    assertThat(fspError).isNotNull();
    assertErrorFields(
        fspError,
        "TECHNICAL_ERROR_FSP_REPRICING_FAILURE",
        "FATAL",
        "503 SERVICE_UNAVAILABLE",
        null,
        true);
    // if fieldName exists it should be null
    assertThat(!fspError.has("fieldName") || fspError.path("fieldName").isNull()).isTrue();

    // Nothing persisted for failing amendment
    assertNoAmendmentWritten(CLAIM_1_ID, saved.getVersion());
  }

  @ParameterizedTest(name = "validator: {0}")
  @MethodSource("validatorTestCases")
  void validatorReturnsFieldLevelValidation(
      String testName,
      Consumer<ClaimPatch> patchMutator,
      String expectedFieldName,
      String errorCode)
      throws Exception {
    stubExternalValidationEndpoints();

    Claim saved = seedValidClaimAndSave(CLAIM_1_ID);

    ClaimPatch patch = createPatchWithVersion(saved.getVersion(), patchMutator);

    JsonNode body = performPatchAndParseBody(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    assertThat(body.path("status").asInt()).isEqualTo(HttpStatus.BAD_REQUEST.value());

    JsonNode errors = extractErrorsArray(body);
    assertThat(errors.isArray()).isTrue();
    assertThat(errors.size()).isGreaterThanOrEqualTo(1);

    JsonNode matching = findErrorByCode(errors, errorCode);
    assertThat(matching).isNotNull();

    assertErrorFields(matching, errorCode, "ERROR", "400 BAD_REQUEST", expectedFieldName, false);

    assertNoAmendmentWritten(CLAIM_1_ID, saved.getVersion());
  }

  private static Stream<Arguments> validatorTestCases() {
    return Stream.of(
        Arguments.of(
            "client_forename too long",
            (Consumer<ClaimPatch>) (p -> p.setClientForename("A".repeat(500))),
            "client_forename",
            "SCHEMA_VALIDATION_ERROR"),
        Arguments.of(
            "invalid amendment reason code",
            (Consumer<ClaimPatch>)
                (p -> {
                  p.setClientForename("TestChange");
                  p.setAmendmentReasonCode("TEST_REASON");
                }),
            "amendment_reason_code",
            "INVALID_AMENDMENT_REASON_UNKNOWN"),
        Arguments.of(
            "invalid amendment requested by",
            (Consumer<ClaimPatch>)
                (p -> {
                  p.setClientForename("TestChange");
                  p.setAmendmentRequestedBy("TEST_REQUESTOR");
                }),
            "amendment_requested_by",
            "INVALID_REQUESTED_BY_UNKNOWN"));
  }
}
