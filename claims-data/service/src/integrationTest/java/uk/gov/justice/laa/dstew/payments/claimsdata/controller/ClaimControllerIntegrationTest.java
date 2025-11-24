package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ClaimControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String GET_A_CLAIM_ENDPOINT =
      ClaimsDataTestUtil.API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";

  private static final String POST_A_CLAIM_ENDPOINT =
      ClaimsDataTestUtil.API_URI_PREFIX + "/submissions/{submissionId}/claims";

  private static final String PATCH_A_CLAIM_ENDPOINT =
      ClaimsDataTestUtil.API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";

  private static final String GET_CLAIMS_ENDPOINT = ClaimsDataTestUtil.API_URI_PREFIX + "/claims";

  @Test
  void shouldReturnNotFoundWhenSubmissionIdAndClaimIdDoNotExist() throws Exception {
    mockMvc
        .perform(
            get(GET_A_CLAIM_ENDPOINT, Uuid7.timeBasedUuid(), Uuid7.timeBasedUuid())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnAClaimWhenASubmissionAndClaimExists() throws Exception {
    // given: required claims exist in the database
    createClaimsTestData();

    // when: calling the GET endpoint to retrieve a claim for a given submissionId and a claimId
    MvcResult result =
        mockMvc
            .perform(
                get(GET_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: response body contains the claim response
    String responseBody = result.getResponse().getContentAsString();
    var claimResponse = OBJECT_MAPPER.readValue(responseBody, ClaimResponse.class);
    assertThat(claimResponse.getId()).isEqualTo(CLAIM_1_ID.toString());
    assertThat(claimResponse.getClientForename()).isEqualTo("Alice");
    assertThat(claimResponse.getAdviceTime()).isEqualTo(120);
    assertThat(claimResponse.getTravelTime()).isEqualTo(45);
    assertThat(claimResponse.getIsLondonRate()).isTrue();
    assertThat(claimResponse.getCaseId()).isEqualTo("CASE_ID_1");
    assertThat(claimResponse.getUniqueCaseId()).isEqualTo("UC_ID_1");
    assertThat(claimResponse.getOutcomeCode()).isEqualTo("OUTCOME_CODE_1");

    var feeCalculationResponse = claimResponse.getFeeCalculationResponse();
    assertThat(feeCalculationResponse).isNotNull();
    assertThat(feeCalculationResponse.getFeeCode()).isEqualTo("CALC-FEE-1");
    assertThat(feeCalculationResponse.getTotalAmount()).isEqualByComparingTo("125");
    assertThat(feeCalculationResponse.getVatIndicator()).isTrue();
    assertThat(feeCalculationResponse.getBoltOnDetails()).isNotNull();
    assertThat(feeCalculationResponse.getBoltOnDetails().getBoltOnTotalFeeAmount())
        .isEqualByComparingTo("12");
  }

  @Test
  void shouldReturnUnauthorizedWhenAnInvalidAuthTokenIsSupplied() throws Exception {
    mockMvc
        .perform(
            get(GET_A_CLAIM_ENDPOINT, SUBMISSION_ID, CLAIM_1_ID)
                .header(AUTHORIZATION_HEADER, INVALID_AUTH_TOKEN))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldSaveAClaimToDatabase() throws Exception {
    // given: submission test data exists in the database
    getSubmissionTestData();
    final ClaimPost claimPost = getClaimPost();

    // when: calling the POST endpoint with the ClaimPost
    MvcResult result =
        mockMvc
            .perform(
                post(POST_A_CLAIM_ENDPOINT, SUBMISSION_ID)
                    .content(OBJECT_MAPPER.writeValueAsString(claimPost))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isCreated())
            .andReturn();

    // then: response body contains a claimId, and the claim is saved to the database.
    String responseBody = result.getResponse().getContentAsString();
    var createClaim201Response =
        OBJECT_MAPPER.readValue(responseBody, CreateClaim201Response.class);
    assertThat(createClaim201Response.getId()).isNotNull();

    Claim savedClaim =
        claimRepository
            .findById(createClaim201Response.getId())
            .orElseThrow(() -> new RuntimeException("Claim not found"));

    assertThat(savedClaim.getSubmission().getId()).isEqualTo(SUBMISSION_ID);
    assertThat(savedClaim.getCaseReferenceNumber()).isEqualTo(claimPost.getCaseReferenceNumber());
    assertThat(savedClaim.getUniqueFileNumber()).isEqualTo(claimPost.getUniqueFileNumber());
    assertThat(savedClaim.getFeeCode()).isEqualTo(claimPost.getFeeCode());
    assertThat(savedClaim.getCreatedByUserId()).isEqualTo(API_USER_ID);
  }

  @Test
  void shouldReturnBadRequestWhenPostIsCalledWithIncorrectBody() throws Exception {
    // when: calling the POST endpoint with an incorrect body, 400 should be returned
    mockMvc
        .perform(
            post(POST_A_CLAIM_ENDPOINT, SUBMISSION_ID)
                .content("INVALID_DATA")
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnUnAuthorisedWhenPostIsCalledWithInvalidToken() throws Exception {
    final ClaimPost claimPost = getClaimPost();
    // when: calling the POST endpoint with an invalid token, 401 should be returned
    mockMvc
        .perform(
            post(POST_A_CLAIM_ENDPOINT, SUBMISSION_ID)
                .content(OBJECT_MAPPER.writeValueAsString(claimPost))
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, INVALID_AUTH_TOKEN))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldUpdateAnExistingClaimForAGivenSubmissionAndClaimId() throws Exception {
    // given: required claims exist in the database
    createClaimsTestData();
    ClaimPatch claimPatch = new ClaimPatch();
    claimPatch.setFeeCode(FEE_CODE);
    claimPatch.setCaseReferenceNumber(CASE_REFERENCE);

    // when: calling the PATCH endpoint to update the claim for a given submissionId and claimId
    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(claimPatch))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    // then: the database contains the amended claim data
    Claim updatedClaim =
        claimRepository
            .findById(CLAIM_1_ID)
            .orElseThrow(() -> new RuntimeException("Claim not found"));

    assertThat(updatedClaim.getFeeCode()).isEqualTo(FEE_CODE);
    assertThat(updatedClaim.getCaseReferenceNumber()).isEqualTo(CASE_REFERENCE);
  }

  @Test
  void shouldReturnNotFoundWhenSubmissionOrClaimAreNotFound() throws Exception {
    // given: required claims exist in the database
    createClaimsTestData();
    ClaimPatch claimPatch = new ClaimPatch();

    // when: calling the PATCH endpoint to update the claim for an unknown claimId, 404 should be
    // returned.
    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_ID, Uuid7.timeBasedUuid())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(claimPatch))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnBadRequestWhenAnIncorrectBodyIsSupplied() throws Exception {
    // given: required claims exist in the database
    createClaimsTestData();

    // when: calling the PATCH endpoint to update the claim with an incorrect body, 400 should be
    // returned.
    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_ID, CLAIM_1_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content("")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnAllClaimsForAGivenOfficeCode() throws Exception {
    // given: required claims exist in the database
    createClaimsTestData();

    // when: calling the GET endpoint to retrieve all claims for an office_code
    MvcResult result =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT)
                    .param("office_code", "office1")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: response body contains the expected number of claims
    String responseBody = result.getResponse().getContentAsString();
    var claimResultSet = OBJECT_MAPPER.readValue(responseBody, ClaimResultSet.class);
    assertThat(claimResultSet.getTotalElements()).isEqualTo(3);
    assertThat(claimResultSet.getContent()).hasSize(3);
    assertThat(claimResultSet.getContent().getFirst().getId()).isEqualTo(CLAIM_1_ID.toString());
    assertThat(claimResultSet.getContent().get(1).getId()).isEqualTo(CLAIM_4_ID.toString());
    assertThat(claimResultSet.getContent().get(2).getId()).isEqualTo(CLAIM_2_ID.toString());
  }

  @Test
  void shouldReturnAllClaimsForAGivenOfficeCodeAndUniqueFileReference() throws Exception {
    // given: required claims exist in the database
    createClaimsTestData();

    // when: calling the GET endpoint to retrieve all claims for an office_code and a unique file
    // number
    MvcResult result =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT)
                    .param("office_code", "office1")
                    .param("unique_file_number", "UFN-002")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: response body contains the expected number of claims
    String responseBody = result.getResponse().getContentAsString();
    var claimResultSet = OBJECT_MAPPER.readValue(responseBody, ClaimResultSet.class);
    assertThat(claimResultSet.getTotalElements()).isEqualTo(1);
    assertThat(claimResultSet.getContent()).hasSize(1);
    assertThat(claimResultSet.getContent().getFirst().getId()).isEqualTo(CLAIM_2_ID.toString());
  }

  @Test
  void shouldReturnBadRequestWhenUnknownParametersAreSupplied() throws Exception {
    // given: required claims exist in the database
    createClaimsTestData();

    // when: calling the GET endpoint to retrieve all claims with an unknown parameter, 400 should
    // be returned.
    mockMvc
        .perform(
            get(GET_CLAIMS_ENDPOINT)
                .param("office_code_unknown", OFFICE_ACCOUNT_NUMBER)
                .param("unknown-parameter", "UFN-002")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnEmptyClaimsWhenOfficeCodeDoesNotMatch() throws Exception {
    // given: required claims exist in the database with OFFICE_ACCOUNT_NUMBER code
    createClaimsTestData();

    // when: calling the GET endpoint to retrieve all claims with an unexisting office_code
    MvcResult result =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT)
                    .param("office_code", "OFFICE-CODE-002")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: response body contains no claims.
    String responseBody = result.getResponse().getContentAsString();
    var claimResultSet = OBJECT_MAPPER.readValue(responseBody, ClaimResultSet.class);
    assertThat(claimResultSet.getTotalElements()).isEqualTo(0);
  }

  @Test
  void shouldReturnBadRequestWhenOfficeCodeIsNotSupplied() throws Exception {
    mockMvc
        .perform(get(GET_CLAIMS_ENDPOINT).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }
}
