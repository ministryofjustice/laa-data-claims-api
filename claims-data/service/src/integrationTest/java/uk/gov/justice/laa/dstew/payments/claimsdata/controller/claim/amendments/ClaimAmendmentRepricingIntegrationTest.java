package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_5_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import uk.gov.justice.laa.dstew.payments.claimsdata.client.FeeSchemePlatformRestClient;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.helper.MockServerIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentExternalValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculation;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("Amendment Repricing Flow (DSTEW-1595) Integration Test")
class ClaimAmendmentRepricingIntegrationTest extends MockServerIntegrationTest {

  private static final String PATCH_A_CLAIM_ENDPOINT =
      API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";
  private static final String AMENDMENT_USER_ID = "00000000-0000-0000-0000-000000000001";

  @Autowired private ClaimsApiProperties claimsApiProperties;

  // We mock his PDA step to isolate your FSP repricing logic
  @MockitoBean private AmendmentExternalValidationStep externalValidationStep;

  // Keeping your RestClient mock here so we don't have to guess the MockServer URL paths for FSP
  @MockitoBean private FeeSchemePlatformRestClient fspRestClient;

  private boolean originalAmendmentFlag;

  @BeforeEach
  void setUp() {
    originalAmendmentFlag = claimsApiProperties.getAmendments().isEnabled();
    claimsApiProperties.getAmendments().setEnabled("true");

    // Use the base class seeding
    seedClaimsData();

    // Isolate from PDA validation failures
    Mockito.when(externalValidationStep.validate(any())).thenReturn(List.of());

    // Seed a baseline CalculatedFeeDetail so the validation step knows this claim is eligible for
    // repricing
    Claim claim5 = claimRepository.findById(CLAIM_5_ID).orElseThrow();
    createCalculatedFeeDetail(claim5, false, OffsetDateTime.now().minusDays(1));
  }

  @AfterEach
  void tearDown() {
    claimsApiProperties.getAmendments().setEnabled(String.valueOf(originalAmendmentFlag));
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - successfully invokes FSP repricing and saves CalculatedFeeDetail row")
  void shouldSuccessfullyRepriceAndCommitValidAmendment() throws Exception {
    ClaimPatch patchPayload = new ClaimPatch();
    patchPayload.setVersion(1L);

    patchPayload.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));
    patchPayload.setTravelTime(999);
    patchPayload.setWaitingTime(888);

    patchPayload.setAmendmentUserId(UUID.fromString(AMENDMENT_USER_ID));
    patchPayload.setAmendmentRequestedBy("PROVIDER");
    patchPayload.setAmendmentReasonCode("PROVIDER_ERROR");

    FeeCalculation calc =
        new FeeCalculation().totalAmount(650.00).netProfitCostsAmount(450.00).vatIndicator(true);

    FeeCalculationResponse mockFspResponse =
        new FeeCalculationResponse()
            .feeCode("FEE-123")
            .schemeId("SCHEME-TEST")
            .escapeCaseFlag(false)
            .feeCalculation(calc);

    Mockito.when(fspRestClient.calculateFee(any())).thenReturn(ResponseEntity.ok(mockFspResponse));

    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    calculatedFeeDetailRepository.flush();

    List<CalculatedFeeDetail> savedFees =
        calculatedFeeDetailRepository.findAll().stream()
            .filter(cfd -> cfd.getClaim().getId().equals(CLAIM_5_ID))
            .sorted((f1, f2) -> f2.getCreatedOn().compareTo(f1.getCreatedOn()))
            .toList();

    assertThat(savedFees).isNotEmpty();
    CalculatedFeeDetail latestFeeRecord = savedFees.get(0);
    assertThat(latestFeeRecord.getTotalAmount()).isEqualByComparingTo("650.00");
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - returns 400 Bad Request when FSP returns data validation failure")
  void shouldReturnBadRequestWhenFspValidationFails() throws Exception {
    ClaimPatch patchPayload = new ClaimPatch();
    patchPayload.setVersion(1L);

    patchPayload.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));
    patchPayload.setTravelTime(999);

    patchPayload.setAmendmentUserId(UUID.fromString(AMENDMENT_USER_ID));
    patchPayload.setAmendmentRequestedBy("PROVIDER");
    patchPayload.setAmendmentReasonCode("PROVIDER_ERROR");

    WebClientResponseException fspRejection =
        WebClientResponseException.create(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            null,
            "Invalid profit cost configuration combo".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);

    Mockito.when(fspRestClient.calculateFee(any())).thenThrow(fspRejection);

    MvcResult mvcResult =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                    .contentType(MediaType.APPLICATION_JSON))
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
    ClaimPatch patchPayload = new ClaimPatch();
    patchPayload.setVersion(1L);
    patchPayload.setFeeCode("FEE-TIMEOUT");
    patchPayload.setAmendmentUserId(UUID.fromString(AMENDMENT_USER_ID));
    patchPayload.setAmendmentRequestedBy("PROVIDER");
    patchPayload.setAmendmentReasonCode("PROVIDER_ERROR");

    Mockito.when(fspRestClient.calculateFee(any()))
        .thenThrow(new ResourceAccessException("Read timed out"));

    MvcResult mvcResult =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andReturn();

    String body = mvcResult.getResponse().getContentAsString();
    assertThat(body).contains("A technical error occurred while recalculating the fee");
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - skips FSP repricing when changes do not impact pricing")
  void shouldSkipRepricingWhenChangesDoNotImpactPricing() throws Exception {
    // given: A patch that only updates non-pricing fields (e.g., client name)
    ClaimPatch patchPayload = new ClaimPatch();
    patchPayload.setVersion(1L);
    patchPayload.setClientForename("NewForename");
    patchPayload.setClientSurname("NewSurname");

    patchPayload.setAmendmentUserId(UUID.fromString(AMENDMENT_USER_ID));
    patchPayload.setAmendmentRequestedBy("PROVIDER");
    patchPayload.setAmendmentReasonCode("PROVIDER_ERROR");

    // when: the patch is submitted
    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    // then: FSP was never called because pricing fields didn't change
    Mockito.verifyNoInteractions(fspRestClient);
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - skips FSP repricing when baseline claim lacks calculated fee details")
  void shouldSkipRepricingWhenNoBaselineFeeDetails() throws Exception {
    // given: Remove the baseline fee record created in @BeforeEach
    calculatedFeeDetailRepository.deleteAll();
    calculatedFeeDetailRepository.flush();

    // A patch with pricing-impacting changes
    ClaimPatch patchPayload = new ClaimPatch();
    patchPayload.setVersion(1L);
    patchPayload.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));
    patchPayload.setAmendmentUserId(UUID.fromString(AMENDMENT_USER_ID));
    patchPayload.setAmendmentRequestedBy("PROVIDER");
    patchPayload.setAmendmentReasonCode("PROVIDER_ERROR");

    // when: the patch is submitted
    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    // then: FSP was skipped because there is no prior baseline to compare against
    Mockito.verifyNoInteractions(fspRestClient);
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - returns 503 Service Unavailable when FSP returns 500 Internal Server Error")
  void shouldReturnServiceUnavailableOnFsp500Error() throws Exception {
    // given: A patch triggering repricing
    ClaimPatch patchPayload = new ClaimPatch();
    patchPayload.setVersion(1L);
    patchPayload.setFeeCode("FEE-500");
    patchPayload.setAmendmentUserId(UUID.fromString(AMENDMENT_USER_ID));
    patchPayload.setAmendmentRequestedBy("PROVIDER");
    patchPayload.setAmendmentReasonCode("PROVIDER_ERROR");

    // Mock FSP returning a 500 Server Error
    WebClientResponseException serverError =
        WebClientResponseException.create(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            null,
            "FSP is down".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);

    Mockito.when(fspRestClient.calculateFee(any())).thenThrow(serverError);

    // when: the patch is submitted
    MvcResult mvcResult =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andReturn();

    // then: we get the safe fallback tech-error message
    String body = mvcResult.getResponse().getContentAsString();
    assertThat(body).contains("A technical error occurred while recalculating the fee");
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - returns 503 Service Unavailable when FSP response body is null")
  void shouldReturnServiceUnavailableWhenFspBodyIsNull() throws Exception {
    // given: A patch triggering repricing
    ClaimPatch patchPayload = new ClaimPatch();
    patchPayload.setVersion(1L);
    patchPayload.setFeeCode("FEE-NULL");
    patchPayload.setAmendmentUserId(UUID.fromString(AMENDMENT_USER_ID));
    patchPayload.setAmendmentRequestedBy("PROVIDER");
    patchPayload.setAmendmentReasonCode("PROVIDER_ERROR");

    // Mock an HTTP 200 OK, but with an entirely missing/null body
    Mockito.when(fspRestClient.calculateFee(any())).thenReturn(ResponseEntity.ok(null));

    // when: the patch is submitted
    MvcResult mvcResult =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_5_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andReturn();

    // then: Objects.requireNonNull triggered the general catch block, yielding a technical error
    String body = mvcResult.getResponse().getContentAsString();
    assertThat(body).contains("A technical error occurred while recalculating the fee");
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
}
