package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_5_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpError;
import org.mockserver.model.MediaType;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.helper.MockServerIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentExternalValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("Amendment Repricing Flow (DSTEW-1595) Integration Test")
class ClaimAmendmentRepricingIntegrationTest extends MockServerIntegrationTest {

  private static final String PATCH_A_CLAIM_ENDPOINT =
      API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";
  private static final String AMENDMENT_USER_ID = "00000000-0000-0000-0000-000000000001";

  @SuppressWarnings("java:S1075")
  private static final String FEE_CALCULATION_PATH = "/api/v1/fee-calculation";

  private static final String TECHNICAL_ERROR =
      "A technical error occurred while recalculating the fee";

  @Autowired private ClaimsApiProperties claimsApiProperties;

  // We still mock PDA validation so it doesn't block FSP repricing
  @MockitoBean private AmendmentExternalValidationStep externalValidationStep;

  private boolean originalAmendmentFlag;

  @BeforeEach
  void setUp() {
    originalAmendmentFlag = claimsApiProperties.getAmendments().isEnabled();
    claimsApiProperties.getAmendments().setEnabled("true");

    seedClaimsData();

    Mockito.when(externalValidationStep.validate(any())).thenReturn(List.of());

    Claim claim5 = claimRepository.findById(CLAIM_5_ID).orElseThrow();
    createCalculatedFeeDetail(claim5, false, OffsetDateTime.now().minusDays(1));

    // Clear any leftover mockserver expectations
    mockServerClient.clear(request().withPath(FEE_CALCULATION_PATH), ClearType.EXPECTATIONS);
  }

  @AfterEach
  void tearDown() {
    claimsApiProperties.getAmendments().setEnabled(String.valueOf(originalAmendmentFlag));
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - successfully invokes FSP repricing and saves CalculatedFeeDetail row")
  void shouldSuccessfullyRepriceAndCommitValidAmendment() throws Exception {
    ClaimPatch patchPayload = createBasePatch();
    patchPayload.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));
    patchPayload.setTravelTime(999);

    // Real MockServer stub returning exactly what we want to assert
    String mockResponseBody =
        "{\"feeCode\":\"FEE-123\",\"schemeId\":\"SCHEME-TEST\",\"escapeCaseFlag\":false,\"feeCalculation\":{\"totalAmount\":650.00,\"netProfitCostsAmount\":450.00,\"vatIndicator\":true}}";

    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION_PATH))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(mockResponseBody));

    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    calculatedFeeDetailRepository.flush();
    List<CalculatedFeeDetail> savedFees =
        calculatedFeeDetailRepository.findAll().stream()
            .filter(cfd -> cfd.getClaim().getId().equals(CLAIM_5_ID))
            .sorted((f1, f2) -> f2.getCreatedOn().compareTo(f1.getCreatedOn()))
            .toList();

    assertThat(savedFees).isNotEmpty();
    assertThat(savedFees.get(0).getTotalAmount()).isEqualByComparingTo("650.00");
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - returns 400 Bad Request when FSP returns data validation failure")
  void shouldReturnBadRequestWhenFspValidationFails() throws Exception {
    ClaimPatch patchPayload = createBasePatch();
    patchPayload.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));

    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION_PATH))
        .respond(
            response().withStatusCode(400).withBody("Invalid profit cost configuration combo"));

    MvcResult mvcResult =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();

    String body = mvcResult.getResponse().getContentAsString();
    assertThat(body).contains("The fee calculation failed validation");
    assertThat(body).contains("Invalid profit cost configuration combo");
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - returns 503 Service Unavailable when FSP times out")
  void shouldReturnServiceUnavailableOnFspNetworkTimeout() throws Exception {
    ClaimPatch patchPayload = createBasePatch();
    patchPayload.setFeeCode("FEE-TIMEOUT");

    // Simulate network drop directly via MockServer
    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION_PATH))
        .error(HttpError.error().withDropConnection(true));

    MvcResult mvcResult =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andReturn();

    assertThat(mvcResult.getResponse().getContentAsString()).contains(TECHNICAL_ERROR);
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - skips FSP repricing when changes do not impact pricing")
  void shouldSkipRepricingWhenChangesDoNotImpactPricing() throws Exception {
    ClaimPatch patchPayload = createBasePatch();
    patchPayload.setClientForename("NewForename");

    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    // Verify MockServer never received a call to the calculation endpoint
    mockServerClient.verify(request().withPath(FEE_CALCULATION_PATH), VerificationTimes.exactly(0));
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - skips FSP repricing when baseline lacks calculated fee details")
  void shouldSkipRepricingWhenNoBaselineFeeDetails() throws Exception {
    calculatedFeeDetailRepository.deleteAll();
    calculatedFeeDetailRepository.flush();

    ClaimPatch patchPayload = createBasePatch();
    patchPayload.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));

    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    mockServerClient.verify(request().withPath(FEE_CALCULATION_PATH), VerificationTimes.exactly(0));
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - returns 503 Service Unavailable when FSP returns 500")
  void shouldReturnServiceUnavailableOnFsp500Error() throws Exception {
    ClaimPatch patchPayload = createBasePatch();
    patchPayload.setFeeCode("FEE-500");

    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION_PATH))
        .respond(response().withStatusCode(500));

    MvcResult mvcResult =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andReturn();

    assertThat(mvcResult.getResponse().getContentAsString()).contains(TECHNICAL_ERROR);
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - returns 503 Service Unavailable when FSP body is null")
  void shouldReturnServiceUnavailableWhenFspBodyIsNull() throws Exception {
    ClaimPatch patchPayload = createBasePatch();
    patchPayload.setFeeCode("FEE-NULL");

    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION_PATH))
        .respond(response().withStatusCode(200)); // 200 OK, but no body provided

    MvcResult mvcResult =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andReturn();

    assertThat(mvcResult.getResponse().getContentAsString()).contains(TECHNICAL_ERROR);
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - successfully updates escapeCaseFlag to true when threshold is exceeded")
  void shouldSuccessfullySaveEscapeCaseFlagTrue() throws Exception {
    ClaimPatch patchPayload = createBasePatch();
    // High costs to simulate pushing the claim over the escape threshold
    patchPayload.setNetProfitCostsAmount(BigDecimal.valueOf(15000.00));
    patchPayload.setAdviceTime(999);

    // Real MockServer stub returning escapeCaseFlag: true
    String mockResponseBody =
        "{\"feeCode\":\"FEE-ESCAPE\",\"schemeId\":\"SCHEME-TEST\",\"escapeCaseFlag\":true,\"feeCalculation\":{\"totalAmount\":15000.00,\"netProfitCostsAmount\":15000.00,\"vatIndicator\":true}}";

    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION_PATH))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(mockResponseBody));

    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    calculatedFeeDetailRepository.flush();
    List<CalculatedFeeDetail> savedFees =
        calculatedFeeDetailRepository.findAll().stream()
            .filter(cfd -> cfd.getClaim().getId().equals(CLAIM_5_ID))
            .sorted((f1, f2) -> f2.getCreatedOn().compareTo(f1.getCreatedOn()))
            .toList();

    assertThat(savedFees).isNotEmpty();
    CalculatedFeeDetail latestFeeRecord = savedFees.get(0);
    assertThat(latestFeeRecord.getTotalAmount()).isEqualByComparingTo("15000.00");
    assertThat(latestFeeRecord.getEscapeCaseFlag()).isTrue();
  }

  private void createCalculatedFeeDetail(
      Claim claim, boolean escapeCaseFlag, OffsetDateTime createdOn) {
    ClaimSummaryFee summaryFee =
        ClaimSummaryFee.builder()
            .claim(claim)
            .id(Uuid7.timeBasedUuid())
            .createdByUserId("Test")
            .build();

    claimSummaryFeeRepository.saveAndFlush(summaryFee);

    CalculatedFeeDetail cfd = new CalculatedFeeDetail();
    cfd.setId(Uuid7.timeBasedUuid());
    cfd.setClaim(claim);
    cfd.setEscapeCaseFlag(escapeCaseFlag);
    cfd.setCreatedOn(createdOn);
    cfd.setFeeCode("FEE-123");
    cfd.setCreatedByUserId("Test");
    cfd.setClaimSummaryFee(summaryFee);
    cfd.setTotalAmount(BigDecimal.valueOf(100.00));

    calculatedFeeDetailRepository.saveAndFlush(cfd);
  }

  // ---------------------------------------------------------------------------
  // Helper to ensure all tests send a schema-valid ClaimPatch
  // ---------------------------------------------------------------------------
  private ClaimPatch createBasePatch() {
    ClaimPatch patchPayload = new ClaimPatch();
    patchPayload.setVersion(1L);
    patchPayload.setAmendmentUserId(UUID.fromString(AMENDMENT_USER_ID));
    patchPayload.setAmendmentRequestedBy("PROVIDER");
    patchPayload.setAmendmentReasonCode("PROVIDER_ERROR");
    return patchPayload;
  }
}
