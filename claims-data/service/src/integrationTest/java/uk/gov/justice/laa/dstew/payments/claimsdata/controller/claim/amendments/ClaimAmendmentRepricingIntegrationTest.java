package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpError;
import org.mockserver.model.MediaType;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.helper.MockServerIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
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

  @Autowired private JdbcTemplate jdbcTemplate;

  private boolean originalAmendmentFlag;

  @BeforeEach
  void setUp() throws Exception {
    originalAmendmentFlag = claimsApiProperties.getAmendments().isEnabled();
    claimsApiProperties.getAmendments().setEnabled("true");

    seedClaimsData();

    // Satisfy the AmendmentExternalValidationStep using the real network layer
    stubExternalValidationEndpoints();

    // Ensure any fee code changes in our tests pass the external Area of Law validation gate
    // (CLAIM_1 belongs to a LEGAL_HELP submission)
    stubFeeDetailsAreaOfLaw("LEGAL_HELP");

    // Put CLAIM_1 into an amendable state and add the baseline fee
    Claim claim1 = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    claim1.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(claim1);

    createCalculatedFeeDetail(claim1, false, Instant.now().minus(1, ChronoUnit.DAYS));

    // Clear the fee-calculation stub set by stubExternalValidationEndpoints
    // because we want each test to strictly control and verify this specific call.
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
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    calculatedFeeDetailRepository.flush();
    List<CalculatedFeeDetail> savedFees =
        calculatedFeeDetailRepository.findAll().stream()
            .filter(cfd -> cfd.getClaim().getId().equals(CLAIM_1_ID))
            .sorted((f1, f2) -> f2.getCreatedOn().compareTo(f1.getCreatedOn()))
            .toList();

    assertThat(savedFees).isNotEmpty();
    assertThat(savedFees.get(0).getTotalAmount()).isEqualByComparingTo("650.00");

    // DSTEW-1762: the freshly-priced row is physically linked to the committed amendment via
    // calculated_fee_detail.claim_amendment_id. Read the FK as a scalar straight from the DB (a
    // test-only concern) so we neither initialise the lazy association outside a session nor add a
    // production query just for this assertion. The history-endpoint FSP flags depend on this link.
    List<ClaimAmendment> amendments =
        claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID);
    assertThat(amendments).hasSize(1);

    CalculatedFeeDetail latestFee = savedFees.get(0);
    assertThat(latestFee.getIsPriceChanged()).isTrue();
    assertThat(readLinkedAmendmentId(latestFee.getId())).isEqualTo(amendments.getFirst().getId());

    // The pre-existing baseline row remains unlinked (claim_amendment_id stays null).
    CalculatedFeeDetail baselineFee = savedFees.get(savedFees.size() - 1);
    assertThat(readLinkedAmendmentId(baselineFee.getId())).isNull();
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
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
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
    // A clean pricing-impacting change (no fee-code change) so no other validation message is
    // collected before the FSP step - isolating the FSP technical (timeout) failure under test.
    patchPayload.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));

    // Simulate network drop directly via MockServer
    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION_PATH))
        .error(HttpError.error().withDropConnection(true));

    MvcResult mvcResult =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
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
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    // Verify MockServer never received a call to the calculation endpoint
    mockServerClient.verify(request().withPath(FEE_CALCULATION_PATH), VerificationTimes.exactly(0));
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - returns 400 Bad Request when baseline lacks calculated fee details")
  void shouldReturnBadRequestWhenNoBaselineFeeDetails() throws Exception {
    calculatedFeeDetailRepository.deleteAll();
    calculatedFeeDetailRepository.flush();

    ClaimPatch patchPayload = createBasePatch();
    patchPayload.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));

    MvcResult mvcResult =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();

    String body = mvcResult.getResponse().getContentAsString();
    assertThat(body).contains("INVALID_CLAIM_BEFORE_STATE_CFD_MISSING");
    mockServerClient.verify(request().withPath(FEE_CALCULATION_PATH), VerificationTimes.exactly(0));
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - returns 503 Service Unavailable when FSP returns 500")
  void shouldReturnServiceUnavailableOnFsp500Error() throws Exception {
    ClaimPatch patchPayload = createBasePatch();
    // A clean pricing-impacting change (no fee-code change) so no other validation message is
    // collected before the FSP step - isolating the FSP technical (500) failure under test.
    patchPayload.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));

    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION_PATH))
        .respond(response().withStatusCode(500));

    MvcResult mvcResult =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
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
    // A clean pricing-impacting change (no fee-code change) so no other validation message is
    // collected before the FSP step - isolating the FSP technical (null body) failure under test.
    patchPayload.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));

    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION_PATH))
        .respond(response().withStatusCode(200)); // 200 OK, but no body provided

    MvcResult mvcResult =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
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
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    calculatedFeeDetailRepository.flush();
    List<CalculatedFeeDetail> savedFees =
        calculatedFeeDetailRepository.findAll().stream()
            .filter(cfd -> cfd.getClaim().getId().equals(CLAIM_1_ID))
            .sorted((f1, f2) -> f2.getCreatedOn().compareTo(f1.getCreatedOn()))
            .toList();

    assertThat(savedFees).isNotEmpty();
    CalculatedFeeDetail latestFeeRecord = savedFees.get(0);
    assertThat(latestFeeRecord.getTotalAmount()).isEqualByComparingTo("15000.00");
    assertThat(latestFeeRecord.getEscapeCaseFlag()).isTrue();
  }

  @Test
  @DisplayName(
      "PATCH /submissions/{id}/claims/{id} - outcome-check gate: a pricing-impacting change "
          + "combined with a non-fatal validation error rejects (400) WITHOUT calling FSP repricing")
  void shouldNotCallFspWhenNonFatalValidationErrorCoexistsWithPricingChange() throws Exception {
    // A genuinely pricing-impacting change that would otherwise trigger the FSP repricing call...
    ClaimPatch patchPayload = createBasePatch();
    patchPayload.setNetProfitCostsAmount(BigDecimal.valueOf(9999.00));
    // ...but the amendment also carries an unknown amendment-reason code, which the metadata
    // reference step (running before the FSP step) collects as a NON-FATAL validation error.
    patchPayload.setAmendmentReasonCode("NOT_A_REAL_REASON_CODE");

    // Stub the FSP repricing endpoint so that, if it were (wrongly) called, the call would succeed
    // and be recorded - making a missed skip visible as a non-zero verification count.
    mockServerClient
        .when(request().withMethod("POST").withPath(FEE_CALCULATION_PATH))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(
                    "{\"feeCode\":\"FEE-123\",\"schemeId\":\"SCHEME-TEST\",\"escapeCaseFlag\":false,"
                        + "\"feeCalculation\":{\"totalAmount\":650.00,\"netProfitCostsAmount\":450.00,"
                        + "\"vatIndicator\":true}}"));

    // Capture the calculated-fee row count for this claim before the request so we can prove no
    // new row is written (these integration tests share DB state, so absolute counts are unsafe).
    calculatedFeeDetailRepository.flush();
    long feesBefore =
        calculatedFeeDetailRepository.findAll().stream()
            .filter(cfd -> cfd.getClaim().getId().equals(CLAIM_1_ID))
            .count();

    MvcResult mvcResult =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .content(OBJECT_MAPPER.writeValueAsString(patchPayload))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();

    // The collected non-fatal validation error is returned in the structured envelope...
    assertThat(mvcResult.getResponse().getContentAsString())
        .contains("INVALID_AMENDMENT_REASON_UNKNOWN");
    // ...and, crucially, the FSP repricing endpoint was never called.
    mockServerClient.verify(request().withPath(FEE_CALCULATION_PATH), VerificationTimes.exactly(0));

    // Nothing was persisted: no new calculated-fee row and the claim is not marked amended.
    calculatedFeeDetailRepository.flush();
    long feesAfter =
        calculatedFeeDetailRepository.findAll().stream()
            .filter(cfd -> cfd.getClaim().getId().equals(CLAIM_1_ID))
            .count();
    // No new repricing row was written by the rejected amendment.
    assertThat(feesAfter).isEqualTo(feesBefore);
    Claim reloaded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(reloaded.isAmended()).isFalse();
  }

  private void createCalculatedFeeDetail(Claim claim, boolean escapeCaseFlag, Instant createdOn) {

    ClaimSummaryFee summaryFee =
        claimSummaryFeeRepository
            .findByClaimId(claim.getId())
            .orElseGet(
                () -> {
                  ClaimSummaryFee newFee =
                      ClaimSummaryFee.builder()
                          .claim(claim)
                          .id(Uuid7.timeBasedUuid())
                          .createdByUserId("Test")
                          .build();
                  return claimSummaryFeeRepository.saveAndFlush(newFee);
                });

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

  /**
   * Reads {@code calculated_fee_detail.claim_amendment_id} for the given row directly from the
   * database (test-only), returning {@code null} when the row is unlinked. Avoids initialising the
   * lazy {@code claimAmendment} association outside a persistence session.
   */
  private UUID readLinkedAmendmentId(UUID calculatedFeeDetailId) {
    return jdbcTemplate.queryForObject(
        "SELECT claim_amendment_id FROM claims.calculated_fee_detail WHERE id = ?",
        UUID.class,
        calculatedFeeDetailId);
  }
}
