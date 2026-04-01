package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimValidationService.INVALID_CLAIM_STATUS_UPDATE_MESSAGE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CASE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_2_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_4_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.FEE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.OFFICE_ACCOUNT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getClaimPost;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponseV2;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSetV2;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateClaim201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.VoidClaim201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.dstew.payments.claimsdata.validator.ClaimSearchRequestValidator;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ClaimControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String GET_A_CLAIM_ENDPOINT =
      ClaimsDataTestUtil.API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";

  private static final String POST_A_CLAIM_ENDPOINT =
      ClaimsDataTestUtil.API_URI_PREFIX + "/submissions/{submissionId}/claims";

  private static final String PATCH_A_CLAIM_ENDPOINT =
      ClaimsDataTestUtil.API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";

  private static final String GET_CLAIMS_ENDPOINT = ClaimsDataTestUtil.API_URI_PREFIX + "/claims";

  private static final String GET_CLAIMS_ENDPOINT_V2 = "/api/v2/claims";

  @BeforeEach
  void setUp() {
    seedClaimsData();
  }

  @Test
  @DisplayName("GET submission/claims - returns 404 when submission and claim IDs do not exist")
  void shouldReturnNotFoundWhenSubmissionIdAndClaimIdDoNotExist() throws Exception {
    mockMvc
        .perform(
            get(GET_A_CLAIM_ENDPOINT, Uuid7.timeBasedUuid(), Uuid7.timeBasedUuid())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET submission/claims - returns claim when submission and claim exist")
  void shouldReturnAClaimWhenASubmissionAndClaimExists() throws Exception {
    // given: required claims exist in the database

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
  @DisplayName("GET submission/claims - unauthorized when invalid auth token supplied")
  void shouldReturnUnauthorizedWhenAnInvalidAuthTokenIsSupplied() throws Exception {
    mockMvc
        .perform(
            get(GET_A_CLAIM_ENDPOINT, SUBMISSION_ID, CLAIM_1_ID)
                .header(AUTHORIZATION_HEADER, INVALID_AUTH_TOKEN))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  @DisplayName("POST submissions/{id}/claims - saves a claim to the database for each area of law")
  void shouldSaveAClaimToDatabase(AreaOfLaw areaOfLaw) throws Exception {
    // given: submission test data exists in the database
    createSubmissionTestData(areaOfLaw);
    final ClaimPost claimPost = getClaimPost(CASE_REFERENCE);

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
  @DisplayName(
      "POST submissions/{id}/claims - logs warning for suspicious SQL-like patterns but creates claim")
  void shouldLogAWarningWhenSqlLikePatternIsDetectedInStringFields() throws Exception {
    // given: submission test data exists in the database
    createSubmissionTestData(AreaOfLaw.LEGAL_HELP);
    final ClaimPost claimPost = getClaimPost("'; DROP TABLE claims; --");
    // Get the logger used by the class under test
    ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

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

    // then: claim is correctly created with a warning logged
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

    assertThat(
            listAppender.list.stream()
                .filter(
                    event -> event.getFormattedMessage().contains("Suspicious SQL-like pattern"))
                .count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("POST submissions/{id}/claims - 400 Bad Request for incorrect body")
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
  @DisplayName("POST submissions/{id}/claims - 401 Unauthorized for invalid token")
  void shouldReturnUnAuthorisedWhenPostIsCalledWithInvalidToken() throws Exception {
    final ClaimPost claimPost = getClaimPost(CASE_REFERENCE);
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
  @DisplayName("PATCH submissions/{id}/claims/{id} - updates an existing claim")
  void shouldUpdateAnExistingClaimForAGivenSubmissionAndClaimId() throws Exception {
    // given: required claims exist in the database

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
  @DisplayName("PATCH submissions/{id}/claims/{id} - 400 when attempting invalid void update")
  void shouldReturnBadRequestWhenClaimPatchIsCalledToVoidAClaim() throws Exception {
    // given: required claims exist in the database
    ClaimPatch claimPatch = new ClaimPatch();
    claimPatch.setFeeCode(FEE_CODE);
    claimPatch.setCaseReferenceNumber(CASE_REFERENCE);
    claimPatch.setStatus(ClaimStatus.VOID);

    // when: calling the PATCH endpoint to update the claim to VOID status, 400 should be returned
    MvcResult result =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_2_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .content(OBJECT_MAPPER.writeValueAsString(claimPatch))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();

    // then: assert error message in response body
    String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody)
        .contains(INVALID_CLAIM_STATUS_UPDATE_MESSAGE.formatted("update claim"));
  }

  @Test
  @DisplayName(
      "PATCH submissions/{id}/claims/{id} - detects SQL-like patterns in patch and logs warning")
  void shouldDetectSqlInjectionInClaimPatchOperation() throws Exception {
    // given: required claims exist in the database

    String caseReference = "' OR name LIKE '%'";
    ClaimPatch claimPatch = new ClaimPatch();
    claimPatch.setFeeCode(FEE_CODE);
    claimPatch.setCaseReferenceNumber(caseReference);
    claimPatch.setValidationMessages(
        List.of(
            new ValidationMessagePatch()
                .displayMessage(caseReference + "is not allowed")
                .source("test")
                .type(ValidationMessageType.ERROR)));
    // Get the logger used by the class under test
    ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

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
    assertThat(updatedClaim.getCaseReferenceNumber()).isEqualTo(caseReference);

    assertThat(
            listAppender.list.stream()
                .filter(
                    event -> event.getFormattedMessage().contains("Suspicious SQL-like pattern"))
                .count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("PATCH submissions/{id}/claims/{id} - 404 when submission or claim not found")
  void shouldReturnNotFoundWhenSubmissionOrClaimAreNotFound() throws Exception {
    // given: required claims exist in the database

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
  @DisplayName("PATCH submissions/{id}/claims/{id} - 400 for incorrect request body")
  void shouldReturnBadRequestWhenAnIncorrectBodyIsSupplied() throws Exception {
    // given: required claims exist in the database

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
  @DisplayName("GET /claims - returns all claims for the given office code")
  void shouldReturnAllClaimsForAGivenOfficeCode() throws Exception {
    // given: required claims exist in the database

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
    assertThat(claimResultSet.getContent().stream().map(ClaimResponse::getId))
        .containsExactlyInAnyOrder(
            CLAIM_1_ID.toString(), CLAIM_2_ID.toString(), CLAIM_4_ID.toString());
  }

  @Test
  @DisplayName("GET /claims - returns claims for office code and unique file reference")
  void shouldReturnAllClaimsForAGivenOfficeCodeAndUniqueFileReference() throws Exception {
    // given: required claims exist in the database

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
  @DisplayName("GET /claims - bad request for unknown parameters")
  void shouldReturnBadRequestWhenUnknownParametersAreSupplied() throws Exception {
    // given: required claims exist in the database

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
  @DisplayName("GET /claims - returns empty when office code does not match")
  void shouldReturnEmptyClaimsWhenOfficeCodeDoesNotMatch() throws Exception {
    // given: required claims exist in the database with OFFICE_ACCOUNT_NUMBER code

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
  @DisplayName("GET /claims - bad request when office code is not supplied")
  void shouldReturnBadRequestWhenOfficeCodeIsNotSupplied() throws Exception {
    mockMvc
        .perform(get(GET_CLAIMS_ENDPOINT).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /api/v2/claims - returns all claims for the given office code (v2)")
  void shouldReturnAllClaimsForAGivenOfficeCodeV2() throws Exception {
    // given: required claims exist in the database

    // when: calling the GET endpoint to retrieve all claims for an office_code
    MvcResult result =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", "office1")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: response body contains the expected number of claims
    String responseBody = result.getResponse().getContentAsString();
    var claimResultSet = OBJECT_MAPPER.readValue(responseBody, ClaimResultSetV2.class);
    assertThat(claimResultSet.getTotalElements()).isEqualTo(3);
    assertThat(claimResultSet.getContent()).hasSize(3);
    assertThat(claimResultSet.getContent().stream().map(ClaimResponseV2::getId))
        .containsExactlyInAnyOrder(
            CLAIM_1_ID.toString(), CLAIM_2_ID.toString(), CLAIM_4_ID.toString());
  }

  @Test
  @DisplayName("GET /api/v2/claims - returns claims for office code and unique file reference (v2)")
  void shouldReturnAllClaimsForAGivenOfficeCodeAndUniqueFileReferenceV2() throws Exception {
    // given: required claims exist in the database

    // when: calling the GET endpoint to retrieve all claims for an office_code and a unique file
    // number
    MvcResult result =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", "office1")
                    .param("unique_file_number", "UFN-002")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: response body contains the expected number of claims
    String responseBody = result.getResponse().getContentAsString();
    var claimResultSet = OBJECT_MAPPER.readValue(responseBody, ClaimResultSetV2.class);
    assertThat(claimResultSet.getTotalElements()).isEqualTo(1);
    assertThat(claimResultSet.getContent()).hasSize(1);
    assertThat(claimResultSet.getContent().getFirst().getId()).isEqualTo(CLAIM_2_ID.toString());
  }

  @Test
  @DisplayName("GET /api/v2/claims - bad request for unknown parameters (v2)")
  void shouldReturnBadRequestWhenUnknownParametersAreSuppliedV2() throws Exception {
    // given: required claims exist in the database

    // when: calling the GET endpoint to retrieve all claims with an unknown parameter, 400 should
    // be returned.
    mockMvc
        .perform(
            get(GET_CLAIMS_ENDPOINT_V2)
                .param("office_code_unknown", OFFICE_ACCOUNT_NUMBER)
                .param("unknown-parameter", "UFN-002")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /api/v2/claims - returns empty when office code does not match (v2)")
  void shouldReturnEmptyClaimsWhenOfficeCodeDoesNotMatchV2() throws Exception {
    // given: required claims exist in the database with OFFICE_ACCOUNT_NUMBER code

    // when: calling the GET endpoint to retrieve all claims with an unexisting office_code
    MvcResult result =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
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
  @DisplayName("GET /api/v2/claims - bad request when office code not supplied (v2)")
  void shouldReturnBadRequestWhenOfficeCodeIsNotSuppliedV2() throws Exception {
    mockMvc
        .perform(get(GET_CLAIMS_ENDPOINT_V2).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }

  /*
   * Additional tests covering /api/v2/claims case_reference_number matching behavior
   */

  @ParameterizedTest
  @CsvSource(
      value = {
        // existingClaimCrn, searchFilter, expectedFound
        "ABC-1234,ABC,true",
        "RAC ATE2/1,ATE2/1,true",
        "RAC ATE2/1,ate2/1,true",
        "RAC ATE2/1,RAC ATE2/1,true",
        "RAC ATE2/1,ATE2,true",
        "RAC ATE2/1,  ,true",
        "RAC ATE2/1,ATE3,false",
        "RAC ATE2/1,2/1,true"
      })
  @DisplayName(
      "GET /api/v2/claims - case_reference_number matching behaviour (partial/contains/case-insensitive/exact)")
  void shouldMatchCaseReferenceVariantsV2(
      String existingCrn, String searchFilter, boolean expectedFound) throws Exception {

    UUID newClaimId = createAndValidateClaimWithCRN(existingCrn);

    // when: calling the v2 claims endpoint with the case_reference_number filter
    MvcResult getResult =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("case_reference_number", searchFilter)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    String resultBody = getResult.getResponse().getContentAsString();
    var claimResultSet = OBJECT_MAPPER.readValue(resultBody, ClaimResultSetV2.class);

    if (expectedFound) {
      assertThat(claimResultSet.getTotalElements()).isEqualTo(1);
      assertThat(claimResultSet.getContent().getFirst().getId()).isEqualTo(newClaimId.toString());
    } else {
      assertThat(claimResultSet.getTotalElements()).isEqualTo(0);
    }
  }

  @Test
  @DisplayName("GET /api/v2/claims - rejects short case_reference_number (min length)")
  void shouldRejectShortCaseReferenceFiltersV2() throws Exception {
    // when: calling v2 with a short (trimmed length < 3) case_reference_number
    MvcResult result =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("case_reference_number", "AB")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isBadRequest())
            .andReturn();

    // then: user is informed that at least 3 characters are required
    String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody).containsIgnoringCase("at least 3");
  }

  @Test
  @DisplayName("GET /api/v2/claims - returns no results when no CRN matches")
  void shouldReturnNoResultsWhenNoCrnMatchesV2() throws Exception {

    // when: searching for a non-matching value
    MvcResult getResult =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("case_reference_number", "NOPE-123")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    String resultBody = getResult.getResponse().getContentAsString();
    var claimResultSet = OBJECT_MAPPER.readValue(resultBody, ClaimResultSetV2.class);
    assertThat(claimResultSet.getTotalElements()).isEqualTo(0);
  }

  @Test
  @DisplayName("GET /claims (v1) - exact match behaviour remains unchanged")
  void shouldNotChangeV1ExactMatchBehaviour() throws Exception {

    UUID newClaimId = createAndValidateClaimWithCRN("V1-EXACT-1");

    // when: calling v1 with the exact CRN
    MvcResult getResult =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("case_reference_number", "V1-EXACT-1")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    String resultBody = getResult.getResponse().getContentAsString();
    var claimResultSet = OBJECT_MAPPER.readValue(resultBody, ClaimResultSet.class);
    assertThat(claimResultSet.getTotalElements()).isEqualTo(1);
    assertThat(claimResultSet.getContent().getFirst().getId()).isEqualTo(newClaimId.toString());
  }

  @Test
  @DisplayName(
      "GET /api/v2/claims - pagination and other filters unaffected by case_reference filter")
  void paginationAndOtherFiltersUnaffectedWhenUsingCaseReferenceV2() throws Exception {

    UUID newClaimId = createAndValidateClaimWithCRN("PAG-123");

    // when: calling v2 with case_reference_number and pagination/sorting params
    MvcResult getResult =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("case_reference_number", "PAG")
                    .param("page", "0")
                    .param("size", "10")
                    .param("sort", "submission_period,asc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    String resultBody = getResult.getResponse().getContentAsString();
    var claimResultSet = OBJECT_MAPPER.readValue(resultBody, ClaimResultSetV2.class);
    assertThat(claimResultSet.getTotalElements()).isGreaterThanOrEqualTo(1);
    assertThat(claimResultSet.getContent().stream().map(ClaimResponseV2::getId))
        .contains(newClaimId.toString());
  }

  @ParameterizedTest
  @DisplayName("GET /api/v2/claims - rejects various invalid case_reference_number inputs (400)")
  @CsvSource({
    "ABC%123,INVALID",
    "ABC_123,INVALID",
    "ABC!123,INVALID",
    "1234567890123456789012345678901,TOO_LONG"
  })
  void shouldRejectInvalidCaseReferenceNumberInV2Search(String input, String expectedType)
      throws Exception {

    // when: calling the v2 claims endpoint with an invalid case_reference_number
    MvcResult result =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("case_reference_number", input)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isBadRequest())
            .andReturn();

    // then: response should contain the correct validation message constant
    String responseBody = result.getResponse().getContentAsString();

    String expectedMessage;
    switch (expectedType) {
      case "TOO_LONG":
        expectedMessage =
            String.format(
                ClaimSearchRequestValidator.CASE_REFERENCE_TOO_LONG,
                ClaimSearchRequestValidator.MAX_CASE_REFERENCE_LENGTH);
        break;
      case "INVALID":
      default:
        expectedMessage = ClaimSearchRequestValidator.CASE_REFERENCE_INVALID;
        break;
    }

    assertThat(responseBody).contains(expectedMessage);
  }

  private UUID createAndValidateClaimWithCRN(String crn) throws Exception {

    createSubmissionTestData(AreaOfLaw.LEGAL_HELP);

    MvcResult postResult =
        mockMvc
            .perform(
                post(POST_A_CLAIM_ENDPOINT, SUBMISSION_ID)
                    .content(OBJECT_MAPPER.writeValueAsString(getClaimPost(crn)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isCreated())
            .andReturn();

    String createdBody = postResult.getResponse().getContentAsString();
    var created = OBJECT_MAPPER.readValue(createdBody, CreateClaim201Response.class);

    return created.getId();
  }

  @Nested
  @DisplayName("Void Claim Endpoint")
  class VoidClaimTests {

    @Test
    void shouldVoidClaimAndCreateAssessment() throws Exception {

      UUID userId = Uuid7.timeBasedUuid();

      String requestBody =
          "{"
              + "\"created_by_user_id\":\""
              + userId
              + "\","
              + "\"assessment_reason\":\"test reason\""
              + "}";

      MvcResult result =
          mockMvc
              .perform(
                  post(ClaimsDataTestUtil.API_URI_PREFIX + "/claims/{claimId}/void", CLAIM_2_ID)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(requestBody)
                      .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
              .andExpect(status().isCreated())
              .andReturn();

      // Verify response body
      String responseBody = result.getResponse().getContentAsString();
      var response = OBJECT_MAPPER.readValue(responseBody, VoidClaim201Response.class);

      assertThat(response.getId()).isNotNull();

      // Verify assessment saved in database
      var assessment =
          assessmentRepository
              .findById(response.getId())
              .orElseThrow(() -> new RuntimeException("Assessment not found"));

      assertThat(assessment.getAssessmentType()).isEqualTo(AssessmentType.VOID);
      assertThat(assessment.getAssessmentReason()).isEqualTo("test reason");
      assertThat(assessment.getCreatedByUserId()).isEqualTo(userId.toString());
    }

    @Test
    void shouldReturnBadRequestWhenClaimDoesNotExistInValidStatus() throws Exception {
      String requestBody =
          "{"
              + "\"created_by_user_id\":\""
              + API_USER_ID
              + "\","
              + "\"assessment_reason\":\"test reason\""
              + "}";

      mockMvc
          .perform(
              post(ClaimsDataTestUtil.API_URI_PREFIX + "/claims/{claimId}/void", CLAIM_1_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody)
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
          .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundWhenClaimDoesNotExistForVoidOperation() throws Exception {

      String requestBody =
          "{"
              + "\"created_by_user_id\":\""
              + API_USER_ID
              + "\","
              + "\"assessment_reason\":\"test reason\""
              + "}";

      mockMvc
          .perform(
              post(
                      ClaimsDataTestUtil.API_URI_PREFIX + "/claims/{claimId}/void",
                      Uuid7.timeBasedUuid())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody)
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
          .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequestWhenCreatedByUserIdIsMissing() throws Exception {

      String requestBody = "{" + "\"assessment_reason\":\"test reason\"" + "}";

      mockMvc
          .perform(
              post(ClaimsDataTestUtil.API_URI_PREFIX + "/claims/{claimId}/void", CLAIM_2_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody)
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
          .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnUnauthorizedWhenVoidClaimCalledWithInvalidToken() throws Exception {
      String requestBody =
          "{"
              + "\"created_by_user_id\":\""
              + API_USER_ID
              + "\","
              + "\"assessment_reason\":\"test reason\""
              + "}";

      mockMvc
          .perform(
              post(ClaimsDataTestUtil.API_URI_PREFIX + "/claims/{claimId}/void", CLAIM_2_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody)
                  .header(AUTHORIZATION_HEADER, Uuid7.timeBasedUuid()))
          .andExpect(status().isUnauthorized());
    }
  }
}
