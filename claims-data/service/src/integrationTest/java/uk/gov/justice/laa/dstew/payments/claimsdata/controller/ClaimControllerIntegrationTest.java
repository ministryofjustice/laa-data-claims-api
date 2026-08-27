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
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_5_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.FEE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.OFFICE_ACCOUNT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getClaimPost;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.model.DerivedClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.VoidClaim201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.dstew.payments.claimsdata.validator.ClaimSearchRequestValidator;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ClaimControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private ClaimsApiProperties claimsApiProperties;

  private static final String GET_A_CLAIM_ENDPOINT =
      ClaimsDataTestUtil.API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";

  private static final String POST_A_CLAIM_ENDPOINT =
      ClaimsDataTestUtil.API_URI_PREFIX + "/submissions/{submissionId}/claims";

  private static final String PATCH_A_CLAIM_ENDPOINT =
      ClaimsDataTestUtil.API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";

  private static final String GET_CLAIMS_ENDPOINT = ClaimsDataTestUtil.API_URI_PREFIX + "/claims";

  private static final String GET_CLAIMS_ENDPOINT_V2 = "/api/v2/claims";

  private static final int NO_CLAIMS_IN_SUBMISSION1 = 4;

  private Boolean amendmentSwitch;

  @BeforeEach
  void setUp() {
    // Capture the original boolean state
    amendmentSwitch = claimsApiProperties.getAmendments().isEnabled();
    seedClaimsData();
  }

  @AfterEach
  void tearDown() {
    // Use String.valueOf() for a null-safe string conversion
    claimsApiProperties.getAmendments().setEnabled(String.valueOf(amendmentSwitch));
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
    assertThat(claimResponse.getCaseId()).isEqualTo("123");
    assertThat(claimResponse.getUniqueCaseId()).isEqualTo("UC_ID_1");
    assertThat(claimResponse.getOutcomeCode()).isEqualTo("AB");

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
      "POST submissions/{id}/claims - returns 409 when the line number already exists in the "
          + "submission")
  void shouldReturnConflictWhenClaimLineNumberIsDuplicatedInSubmission() throws Exception {
    // given: a submission with a claim already persisted (getClaimPost uses lineNumber 123)
    createSubmissionTestData(AreaOfLaw.LEGAL_HELP);
    final ClaimPost first = getClaimPost(CASE_REFERENCE);
    mockMvc
        .perform(
            post(POST_A_CLAIM_ENDPOINT, SUBMISSION_ID)
                .content(OBJECT_MAPPER.writeValueAsString(first))
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isCreated());

    // when: posting a second claim with the same (submission_id, line_number)
    final ClaimPost duplicate = getClaimPost("CASE-DUPLICATE");
    MvcResult result =
        mockMvc
            .perform(
                post(POST_A_CLAIM_ENDPOINT, SUBMISSION_ID)
                    .content(OBJECT_MAPPER.writeValueAsString(duplicate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            // then: the duplicate is rejected as a conflict (not a 500)
            .andExpect(status().isConflict())
            .andReturn();

    // and: the RFC 9457 body carries the user-safe message
    String body = result.getResponse().getContentAsString();
    assertThat(body).contains("already exists for the submission.");

    // and: nothing extra was persisted - only the first claim remains for the submission
    assertThat(claimRepository.findBySubmissionId(SUBMISSION_ID)).hasSize(1);
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
    // capture the pre-update state to assert created metadata is preserved
    Claim beforeClaim =
        claimRepository
            .findById(CLAIM_1_ID)
            .orElseThrow(() -> new RuntimeException("Claim not found"));

    ClaimPatch claimPatch = new ClaimPatch();
    claimPatch.setFeeCode(FEE_CODE);
    claimPatch.setCaseReferenceNumber(CASE_REFERENCE);
    claimPatch.setStatus(ClaimStatus.READY_TO_PROCESS);

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
    claimsApiProperties.getAmendments().setEnabled("false");
    // given: required claims exist in the database
    ClaimPatch claimPatch = new ClaimPatch();
    claimPatch.setCaseReferenceNumber(CASE_REFERENCE);
    claimPatch.setStatus(ClaimStatus.VOID);

    // when: calling the PATCH endpoint to update the claim to VOID status, 400 should be returned
    MvcResult result =
        mockMvc
            .perform(
                patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
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

    ClaimPatch claimPatch = new ClaimPatch();
    claimPatch.setFeeCode(FEE_CODE);
    claimPatch.setCaseReferenceNumber(CASE_REFERENCE);
    claimPatch.setStatus(ClaimStatus.READY_TO_PROCESS);
    String createdByUserId = "' OR name LIKE '%'";
    claimPatch.setCreatedByUserId(createdByUserId);
    claimPatch.setValidationMessages(
        List.of(
            new ValidationMessagePatch()
                .displayMessage("createdByUserId" + "is not allowed")
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
    assertThat(updatedClaim.getCreatedByUserId()).isEqualTo(createdByUserId);

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
    claimPatch.setVersion(1L); // ADDED VERSION

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
                    .param("office_code", OFFICE_ACCOUNT_NUMBER_1)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: response body contains the expected number of claims
    String responseBody = result.getResponse().getContentAsString();
    var claimResultSet = OBJECT_MAPPER.readValue(responseBody, ClaimResultSet.class);
    assertThat(claimResultSet.getTotalElements()).isEqualTo(NO_CLAIMS_IN_SUBMISSION1);
    assertThat(claimResultSet.getContent()).hasSize(NO_CLAIMS_IN_SUBMISSION1);
    assertThat(claimResultSet.getContent().stream().map(ClaimResponse::getId))
        .containsExactlyInAnyOrder(
            CLAIM_1_ID.toString(),
            CLAIM_2_ID.toString(),
            CLAIM_4_ID.toString(),
            CLAIM_5_ID.toString());
  }

  @Test
  @DisplayName("GET /claims - returns claims for office code and unique file reference")
  void shouldReturnAllClaimsForAGivenOfficeCodeAndUniqueFileReference() throws Exception {
    // given: required claims exist in the database
    var amendedClaim =
        claimRepository
            .findById(CLAIM_2_ID)
            .orElseThrow(() -> new RuntimeException("Claim not found for fixture setup"));
    amendedClaim.setHasAssessment(true);
    amendedClaim.setAmended(true);
    claimRepository.saveAndFlush(amendedClaim);

    // when: calling the GET endpoint to retrieve all claims for an office_code and a unique file
    // number
    MvcResult result =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER_1)
                    .param("unique_file_number", "020125/002")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: response body contains the expected number of claims
    String responseBody = result.getResponse().getContentAsString();
    var claimResultSet = OBJECT_MAPPER.readValue(responseBody, ClaimResultSet.class);
    assertThat(claimResultSet.getTotalElements()).isEqualTo(1);
    assertThat(claimResultSet.getContent()).hasSize(1);
    var claimResponse = claimResultSet.getContent().getFirst();
    assertThat(claimResponse.getId()).isEqualTo(CLAIM_2_ID.toString());
    assertThat(claimResponse.getHasAssessment()).isTrue();
    assertThat(claimResponse.getIsAmended()).isTrue();
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
                    .param("office_code", OFFICE_ACCOUNT_NUMBER_1)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: response body contains the expected number of claims
    String responseBody = result.getResponse().getContentAsString();
    var claimResultSet = OBJECT_MAPPER.readValue(responseBody, ClaimResultSetV2.class);
    assertThat(claimResultSet.getTotalElements()).isEqualTo(NO_CLAIMS_IN_SUBMISSION1);
    assertThat(claimResultSet.getContent()).hasSize(NO_CLAIMS_IN_SUBMISSION1);
    assertThat(claimResultSet.getContent().stream().map(ClaimResponseV2::getId))
        .containsExactlyInAnyOrder(
            CLAIM_1_ID.toString(),
            CLAIM_2_ID.toString(),
            CLAIM_4_ID.toString(),
            CLAIM_5_ID.toString());
  }

  @Test
  @DisplayName("GET /api/v2/claims - returns claims for office code and unique file reference (v2)")
  void shouldReturnAllClaimsForAGivenOfficeCodeAndUniqueFileReferenceV2() throws Exception {
    // given: required claims exist in the database
    var amendedClaim =
        claimRepository
            .findById(CLAIM_2_ID)
            .orElseThrow(() -> new RuntimeException("Claim not found for fixture setup"));
    amendedClaim.setHasAssessment(false);
    amendedClaim.setAmended(true);
    claimRepository.saveAndFlush(amendedClaim);

    // when: calling the GET endpoint to retrieve all claims for an office_code and a unique file
    // number
    MvcResult result =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER_1)
                    .param("unique_file_number", "020125/002")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: response body contains the expected number of claims
    String responseBody = result.getResponse().getContentAsString();
    var claimResultSet = OBJECT_MAPPER.readValue(responseBody, ClaimResultSetV2.class);
    assertThat(claimResultSet.getTotalElements()).isEqualTo(1);
    assertThat(claimResultSet.getContent()).hasSize(1);
    var claimResponse = claimResultSet.getContent().getFirst();
    assertThat(claimResponse.getId()).isEqualTo(CLAIM_2_ID.toString());
    assertThat(claimResponse.getHasAssessment()).isFalse();
    assertThat(claimResponse.getIsAmended()).isTrue();
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

  @Test
  @DisplayName(
      "GET /api/v2/claims - filtering by escaped_case_flag (Failing Test for Bug DSTEW-1943)")
  void shouldReturnClaimsWhenFilteredByEscapedCaseFlag() throws Exception {
    // given: we use an existing office code from the setup

    // when: calling the v2 claims endpoint with the escaped_case_flag filter
    // Note: This was FAILing with a 500 error because ClaimSpecification
    // attempted to join on a scalar "calculatedFeeDetail" field, but the Claim entity
    // now uses a List "calculatedFeeDetails" and gets the first (latest) item
    mockMvc
        .perform(
            get(GET_CLAIMS_ENDPOINT_V2)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("escaped_case_flag", "true")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isOk()); // Will fail here until the specification bug is fixed
  }

  @Test
  @DisplayName("GET /api/v2/claims - filtering by escaped_case_flag isolates the latest fee record")
  void shouldReturnLatestClaimCFDsWhenFilteredByEscapedCaseFlag() throws Exception {
    // given: set up a submission context to satisfy the mandatory office code check
    Instant now = Instant.now();

    // 1. Claim A: Historical records are false, but the LATEST is true -> Should be FOUND
    Claim claimA = new Claim();
    claimA.setId(Uuid7.timeBasedUuid());
    claimA.setSubmission(submission1);
    claimA.setCaseReferenceNumber("CRN-AAA");
    claimA.setUniqueFileNumber("UFN-AAA");
    claimA.setCreatedByUserId(API_USER_ID);

    // Add these mandatory fields to satisfy Bean Validation
    claimA.setMatterTypeCode("TEST-MTC");
    // Unique within submission1 (seedClaimsData already uses lines 1,2,4,5) to satisfy
    // uq_claim_submission_line_number.
    claimA.setLineNumber(10);
    claimA.setStatus(ClaimStatus.READY_TO_PROCESS);

    claimA = claimRepository.saveAndFlush(claimA);

    // ... (CFD creations)
    createCalculatedFeeDetail(claimA, false, now.minus(3, ChronoUnit.DAYS));
    createCalculatedFeeDetail(claimA, false, now.minus(2, ChronoUnit.DAYS));
    createCalculatedFeeDetail(claimA, true, now.minus(1, ChronoUnit.DAYS)); // latest

    // 2. Claim B: Historical records are true, but the LATEST is false -> Should be IGNORED
    Claim claimB = new Claim();
    claimB.setId(Uuid7.timeBasedUuid());
    claimB.setSubmission(submission1);
    claimB.setCaseReferenceNumber("CRN-BBB");
    claimB.setUniqueFileNumber("UFN-BBB");
    claimB.setCreatedByUserId(API_USER_ID);

    // Add these mandatory fields to satisfy Bean Validation
    claimB.setMatterTypeCode("TEST-MTC");
    claimB.setLineNumber(11);
    claimB.setStatus(ClaimStatus.READY_TO_PROCESS);

    claimB = claimRepository.saveAndFlush(claimB);

    createCalculatedFeeDetail(claimB, true, now.minus(3, ChronoUnit.DAYS));
    createCalculatedFeeDetail(claimB, true, now.minus(2, ChronoUnit.DAYS));
    createCalculatedFeeDetail(claimB, false, now.minus(1, ChronoUnit.DAYS)); // latest

    // Ensure Hibernate flushes state to the database before running the query
    claimRepository.flush();

    // when: filtering for escaped_case_flag = true
    MvcResult result =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER_1)
                    .param("escaped_case_flag", "true")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: unpack and verify that only Claim A is returned
    var claimResultSet =
        OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), ClaimResultSetV2.class);

    assertThat(claimResultSet.getContent().stream().map(ClaimResponseV2::getId))
        .contains(claimA.getId().toString())
        .doesNotContain(claimB.getId().toString());
  }

  // Helper method to cleanly persist fee records for the scenario
  private void createCalculatedFeeDetail(Claim claim, boolean escapeCaseFlag, Instant createdOn) {
    // 1. Create and persist the ClaimSummaryFee
    ClaimSummaryFee summaryFee =
        ClaimSummaryFee.builder()
            .claim(claim)
            .id(Uuid7.timeBasedUuid())
            .createdByUserId("Test")
            .build();

    claimSummaryFeeRepository.saveAndFlush(summaryFee);

    // 2. Create and persist the CalculatedFeeDetail, linking the newly saved summary fee
    CalculatedFeeDetail cfd = new CalculatedFeeDetail();
    cfd.setId(Uuid7.timeBasedUuid());
    cfd.setClaim(claim);
    cfd.setEscapeCaseFlag(escapeCaseFlag);
    cfd.setCreatedOn(createdOn);
    cfd.setFeeCode("FEE-123");
    cfd.setCreatedByUserId("Test");
    cfd.setClaimSummaryFee(summaryFee);

    calculatedFeeDetailRepository.saveAndFlush(cfd);
  }

  @Nested
  @DisplayName("Derived claim status sorting")
  class DerivedClaimStatusSortTests {

    private static final String SORT_OFFICE = "SORTOFC";

    private Submission createSortSubmission() {
      return submissionRepository.saveAndFlush(
          Submission.builder()
              .id(Uuid7.timeBasedUuid())
              .bulkSubmissionId(bulkSubmission.getId())
              .officeAccountNumber(SORT_OFFICE)
              .submissionPeriod("FEB-2025")
              .areaOfLaw(AreaOfLaw.CRIME_LOWER)
              .status(SubmissionStatus.CREATED)
              .providerUserId(bulkSubmission.getCreatedByUserId())
              .createdByUserId(API_USER_ID)
              .numberOfClaims(6)
              .createdOn(CREATED_ON)
              .build());
    }

    private Claim persistClaim(
        Submission submission,
        ClaimStatus status,
        boolean hasAssessment,
        boolean isAmended,
        int lineNumber) {
      return claimRepository.saveAndFlush(
          Claim.builder()
              .id(Uuid7.timeBasedUuid())
              .submission(submission)
              .status(status)
              .hasAssessment(hasAssessment)
              .isAmended(isAmended)
              .lineNumber(lineNumber)
              .matterTypeCode("TEST-MTC")
              .createdByUserId(API_USER_ID)
              .build());
    }

    private ClaimResultSetV2 search(String sort) throws Exception {
      MvcResult result =
          mockMvc
              .perform(
                  get(GET_CLAIMS_ENDPOINT_V2)
                      .param("office_code", SORT_OFFICE)
                      .param("sort", sort)
                      .param("size", "50")
                      .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
              .andExpect(status().isOk())
              .andReturn();
      return OBJECT_MAPPER.readValue(
          result.getResponse().getContentAsString(), ClaimResultSetV2.class);
    }

    /** Seeds exactly one claim for each derived status under an isolated office code. */
    private void seedOnePerDerivedStatus(Submission submission) {
      persistClaim(submission, ClaimStatus.VALID, false, false, 1); // ACCEPTED
      persistClaim(submission, ClaimStatus.VALID, false, true, 2); // AMENDED
      persistClaim(submission, ClaimStatus.VALID, true, false, 3); // ASSESSED
      persistClaim(submission, ClaimStatus.VOID, false, false, 4); // VOIDED
      persistClaim(submission, ClaimStatus.INVALID, false, false, 5); // INVALID
      persistClaim(submission, ClaimStatus.READY_TO_PROCESS, false, false, 6); // READY_TO_PROCESS
    }

    @Test
    @DisplayName("ascending follows the canonical business ordering")
    void ascendingOrdering() throws Exception {
      seedOnePerDerivedStatus(createSortSubmission());

      ClaimResultSetV2 resultSet = search("derived_claim_status,asc");

      assertThat(resultSet.getContent().stream().map(ClaimResponseV2::getDerivedClaimStatus))
          .containsExactly(
              DerivedClaimStatus.ACCEPTED,
              DerivedClaimStatus.AMENDED,
              DerivedClaimStatus.ASSESSED,
              DerivedClaimStatus.VOIDED,
              DerivedClaimStatus.INVALID,
              DerivedClaimStatus.READY_TO_PROCESS);
    }

    @Test
    @DisplayName("descending is the reverse of the canonical business ordering")
    void descendingOrdering() throws Exception {
      seedOnePerDerivedStatus(createSortSubmission());

      ClaimResultSetV2 resultSet = search("derived_claim_status,desc");

      assertThat(resultSet.getContent().stream().map(ClaimResponseV2::getDerivedClaimStatus))
          .containsExactly(
              DerivedClaimStatus.READY_TO_PROCESS,
              DerivedClaimStatus.INVALID,
              DerivedClaimStatus.VOIDED,
              DerivedClaimStatus.ASSESSED,
              DerivedClaimStatus.AMENDED,
              DerivedClaimStatus.ACCEPTED);
    }

    @Test
    @DisplayName("claims sharing a derived status are tie-broken by id ASC for stable pagination")
    void tieBreakByIdAscending() throws Exception {
      Submission submission = createSortSubmission();
      // Several ACCEPTED claims (VALID, no assessment, not amended) sharing the same derived
      // status.
      Claim a = persistClaim(submission, ClaimStatus.VALID, false, false, 1);
      Claim b = persistClaim(submission, ClaimStatus.VALID, false, false, 2);
      Claim c = persistClaim(submission, ClaimStatus.VALID, false, false, 3);

      ClaimResultSetV2 resultSet = search("derived_claim_status,asc");

      // UUIDv7 ids are time-ordered; a < b < c in insertion order, so the tie-break yields a,b,c.
      List<String> expectedIdOrder =
          java.util.stream.Stream.of(a.getId(), b.getId(), c.getId())
              .map(UUID::toString)
              .sorted()
              .toList();
      assertThat(resultSet.getContent().stream().map(ClaimResponseV2::getId))
          .containsExactlyElementsOf(expectedIdOrder);
    }

    @Test
    @DisplayName("unsupported sort key returns 400")
    void unsupportedSortKeyReturnsBadRequest() throws Exception {
      mockMvc
          .perform(
              get(GET_CLAIMS_ENDPOINT_V2)
                  .param("office_code", SORT_OFFICE)
                  .param("sort", "not_a_real_field,asc")
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
          .andExpect(status().isBadRequest());
    }
  }

  @ParameterizedTest
  @CsvSource({
    "total_amount,desc",
    "calculated_vat_amount,asc",
    "escape_case_flag,desc",
    "category_of_law,asc"
  })
  @DisplayName(
      "GET /api/v2/claims - sorts by fee fields without throwing PropertyReferenceException (Failing Test)")
  void shouldSortByFeeFieldsWithoutThrowingExceptionV2(String sortParam) throws Exception {
    // given: required claims exist in the database via setup

    // when: calling the v2 claims endpoint with a fee-related sort parameter
    // Expecting 200 OK. Currently fails with a 500 error due to PropertyReferenceException
    mockMvc
        .perform(
            get(GET_CLAIMS_ENDPOINT_V2)
                .param("office_code", OFFICE_ACCOUNT_NUMBER_1)
                .param("sort", sortParam)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "GET /api/v2/claims - sorts by total_amount using the latest calculated fee detail values")
  void shouldSortByLatestCalculatedFeeValuesCorrectly() throws Exception {
    Instant now = Instant.now();
    String testOffice = "SORTFEE-OFC";

    // Create an isolated submission for this test
    Submission sortSubmission =
        submissionRepository.saveAndFlush(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .bulkSubmissionId(bulkSubmission.getId())
                .officeAccountNumber(testOffice)
                .submissionPeriod("FEB-2025")
                .areaOfLaw(AreaOfLaw.CRIME_LOWER)
                .status(SubmissionStatus.CREATED)
                .providerUserId(bulkSubmission.getCreatedByUserId())
                .createdByUserId(API_USER_ID)
                .numberOfClaims(2)
                .createdOn(CREATED_ON)
                .build());

    // Claim 1: Older total_amount was 500.00, but LATEST total_amount is 50.00
    Claim claim1 =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(sortSubmission)
            .caseReferenceNumber("CRN-111")
            .uniqueFileNumber("UFN-111")
            .matterTypeCode("TEST-MTC")
            .lineNumber(1)
            .status(ClaimStatus.READY_TO_PROCESS)
            .createdByUserId(API_USER_ID)
            .build();
    claim1 = claimRepository.saveAndFlush(claim1);
    createCalculatedFeeDetailWithAmount(
        claim1, new BigDecimal("500.00"), now.minus(2, ChronoUnit.DAYS));
    createCalculatedFeeDetailWithAmount(
        claim1, new BigDecimal("50.00"), now.minus(1, ChronoUnit.DAYS)); // Latest

    // Claim 2: Older total_amount was 10.00, but LATEST total_amount is 200.00
    Claim claim2 =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(sortSubmission)
            .caseReferenceNumber("CRN-222")
            .uniqueFileNumber("UFN-222")
            .matterTypeCode("TEST-MTC")
            .lineNumber(2)
            .status(ClaimStatus.READY_TO_PROCESS)
            .createdByUserId(API_USER_ID)
            .build();
    claim2 = claimRepository.saveAndFlush(claim2);
    createCalculatedFeeDetailWithAmount(
        claim2, new BigDecimal("10.00"), now.minus(2, ChronoUnit.DAYS));
    createCalculatedFeeDetailWithAmount(
        claim2, new BigDecimal("200.00"), now.minus(1, ChronoUnit.DAYS)); // Latest

    claimRepository.flush();

    // When sorting descending by total_amount: Claim 2 (200.00) should come before Claim 1 (50.00)
    MvcResult resultDesc =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", testOffice)
                    .param("sort", "total_amount,desc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    var resultSetDesc =
        OBJECT_MAPPER.readValue(
            resultDesc.getResponse().getContentAsString(), ClaimResultSetV2.class);

    assertThat(resultSetDesc.getContent()).hasSize(2);

    // 1st Item in Descending Order (200.00)
    assertThat(resultSetDesc.getContent().get(0).getId()).isEqualTo(claim2.getId().toString());
    assertThat(resultSetDesc.getContent().get(0).getFeeCalculationResponse().getTotalAmount())
        .isEqualByComparingTo("200.00");

    // 2nd Item in Descending Order (50.00)
    assertThat(resultSetDesc.getContent().get(1).getId()).isEqualTo(claim1.getId().toString());
    assertThat(resultSetDesc.getContent().get(1).getFeeCalculationResponse().getTotalAmount())
        .isEqualByComparingTo("50.00");

    // When sorting ascending by total_amount: Claim 1 (50.00) should come before Claim 2 (200.00)
    MvcResult resultAsc =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", testOffice)
                    .param("sort", "total_amount,asc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    var resultSetAsc =
        OBJECT_MAPPER.readValue(
            resultAsc.getResponse().getContentAsString(), ClaimResultSetV2.class);

    assertThat(resultSetAsc.getContent()).hasSize(2);

    // 1st Item in Ascending Order (50.00)
    assertThat(resultSetAsc.getContent().get(0).getId()).isEqualTo(claim1.getId().toString());
    assertThat(resultSetAsc.getContent().get(0).getFeeCalculationResponse().getTotalAmount())
        .isEqualByComparingTo("50.00");

    // 2nd Item in Ascending Order (200.00)
    assertThat(resultSetAsc.getContent().get(1).getId()).isEqualTo(claim2.getId().toString());
    assertThat(resultSetAsc.getContent().get(1).getFeeCalculationResponse().getTotalAmount())
        .isEqualByComparingTo("200.00");
  }

  @Test
  @DisplayName(
      "GET /api/v2/claims - sorts correctly with a mix of missing, single, and multiple calculated fee details")
  void shouldSortClaimsWithVaryingCalculatedFeeDetailCountsCorrectly() throws Exception {
    Instant now = Instant.now();
    String testOffice = "SORTFEE-MIXED";

    // 1. Create an isolated submission for this test
    Submission mixedSubmission =
        submissionRepository.saveAndFlush(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .bulkSubmissionId(bulkSubmission.getId())
                .officeAccountNumber(testOffice)
                .submissionPeriod("MAR-2025")
                .areaOfLaw(AreaOfLaw.CRIME_LOWER)
                .status(SubmissionStatus.CREATED)
                .providerUserId(bulkSubmission.getCreatedByUserId())
                .createdByUserId(API_USER_ID)
                .numberOfClaims(3)
                .createdOn(CREATED_ON)
                .build());

    // 2. Claim A: 0 CFDs (totalAmount evaluates to NULL)
    Claim claimNoCfd =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(mixedSubmission)
            .caseReferenceNumber("CRN-NO-CFD")
            .uniqueFileNumber("UFN-1")
            .matterTypeCode("TEST-MTC")
            .lineNumber(1)
            .status(ClaimStatus.READY_TO_PROCESS)
            .createdByUserId(API_USER_ID)
            .build();
    claimNoCfd = claimRepository.saveAndFlush(claimNoCfd);

    // 3. Claim B: 1 CFD (totalAmount = 100.00)
    Claim claimOneCfd =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(mixedSubmission)
            .caseReferenceNumber("CRN-ONE-CFD")
            .uniqueFileNumber("UFN-2")
            .matterTypeCode("TEST-MTC")
            .lineNumber(2)
            .status(ClaimStatus.READY_TO_PROCESS)
            .createdByUserId(API_USER_ID)
            .build();
    claimOneCfd = claimRepository.saveAndFlush(claimOneCfd);
    createCalculatedFeeDetailWithAmount(
        claimOneCfd, new BigDecimal("100.00"), now.minus(1, ChronoUnit.DAYS));

    // 4. Claim C: 3 CFDs (historical = 900.00 & 500.00, LATEST = 50.00)
    Claim claimMultiCfd =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(mixedSubmission)
            .caseReferenceNumber("CRN-MULTI-CFD")
            .uniqueFileNumber("UFN-3")
            .matterTypeCode("TEST-MTC")
            .lineNumber(3)
            .status(ClaimStatus.READY_TO_PROCESS)
            .createdByUserId(API_USER_ID)
            .build();
    claimMultiCfd = claimRepository.saveAndFlush(claimMultiCfd);
    createCalculatedFeeDetailWithAmount(
        claimMultiCfd, new BigDecimal("900.00"), now.minus(3, ChronoUnit.DAYS));
    createCalculatedFeeDetailWithAmount(
        claimMultiCfd, new BigDecimal("500.00"), now.minus(2, ChronoUnit.DAYS));
    createCalculatedFeeDetailWithAmount(
        claimMultiCfd, new BigDecimal("50.00"), now.minus(1, ChronoUnit.DAYS)); // Latest

    claimRepository.flush();

    // 5. Test ASC Sort
    // PostgreSQL default for ASC is NULLS LAST. Expected order: 50.00 -> 100.00 -> NULL
    MvcResult resultAsc =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", testOffice)
                    .param("sort", "total_amount,asc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    var resultSetAsc =
        OBJECT_MAPPER.readValue(
            resultAsc.getResponse().getContentAsString(), ClaimResultSetV2.class);

    assertThat(resultSetAsc.getContent()).hasSize(3);

    // 1st Item (50.00)
    assertThat(resultSetAsc.getContent().get(0).getId())
        .isEqualTo(claimMultiCfd.getId().toString());
    assertThat(resultSetAsc.getContent().get(0).getFeeCalculationResponse().getTotalAmount())
        .isEqualByComparingTo("50.00");

    // 2nd Item (100.00)
    assertThat(resultSetAsc.getContent().get(1).getId()).isEqualTo(claimOneCfd.getId().toString());
    assertThat(resultSetAsc.getContent().get(1).getFeeCalculationResponse().getTotalAmount())
        .isEqualByComparingTo("100.00");

    // 3rd Item (NULL)
    assertThat(resultSetAsc.getContent().get(2).getId()).isEqualTo(claimNoCfd.getId().toString());
    assertThat(resultSetAsc.getContent().get(2).getFeeCalculationResponse()).isNull();

    // 6. Test DESC Sort
    // PostgreSQL default for DESC is NULLS FIRST. Expected order: NULL -> 100.00 -> 50.00
    MvcResult resultDesc =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", testOffice)
                    .param("sort", "total_amount,desc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    var resultSetDesc =
        OBJECT_MAPPER.readValue(
            resultDesc.getResponse().getContentAsString(), ClaimResultSetV2.class);

    assertThat(resultSetDesc.getContent()).hasSize(3);

    // 1st Item (NULL)
    assertThat(resultSetDesc.getContent().get(0).getId()).isEqualTo(claimNoCfd.getId().toString());
    assertThat(resultSetDesc.getContent().get(0).getFeeCalculationResponse()).isNull();

    // 2nd Item (100.00)
    assertThat(resultSetDesc.getContent().get(1).getId()).isEqualTo(claimOneCfd.getId().toString());
    assertThat(resultSetDesc.getContent().get(1).getFeeCalculationResponse().getTotalAmount())
        .isEqualByComparingTo("100.00");

    // 3rd Item (50.00)
    assertThat(resultSetDesc.getContent().get(2).getId())
        .isEqualTo(claimMultiCfd.getId().toString());
    assertThat(resultSetDesc.getContent().get(2).getFeeCalculationResponse().getTotalAmount())
        .isEqualByComparingTo("50.00");
  }

  // Helper method to seed calculated fee details with specific amounts and timestamps
  private void createCalculatedFeeDetailWithAmount(
      Claim claim, BigDecimal totalAmount, Instant createdOn) {
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
    cfd.setTotalAmount(totalAmount);
    cfd.setCreatedOn(createdOn);
    cfd.setFeeCode("FEE-123");
    cfd.setCreatedByUserId("Test");
    cfd.setClaimSummaryFee(summaryFee);
    calculatedFeeDetailRepository.saveAndFlush(cfd);
  }

  @Test
  @DisplayName("tie-break equal latest fees by claim id and maintain pagination stability")
  void tieBreakEqualLatestFeesByClaimIdAndMaintainPaginationStability() throws Exception {
    Instant now = Instant.now();
    String testOffice = "TIEFEE-OFC";

    // Create an isolated submission for this test
    Submission sortSubmission =
        submissionRepository.saveAndFlush(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .bulkSubmissionId(bulkSubmission.getId())
                .officeAccountNumber(testOffice)
                .submissionPeriod("AUG-2026")
                .areaOfLaw(AreaOfLaw.CRIME_LOWER)
                .status(SubmissionStatus.CREATED)
                .providerUserId(bulkSubmission.getCreatedByUserId())
                .createdByUserId(API_USER_ID)
                .numberOfClaims(3)
                .createdOn(CREATED_ON)
                .build());

    // Create three claims with identical latest totalAmount
    Claim claimA =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(sortSubmission)
            .caseReferenceNumber("CRN-A")
            .uniqueFileNumber("UFN-A")
            .matterTypeCode("TEST-MTC")
            .lineNumber(1)
            .status(ClaimStatus.READY_TO_PROCESS)
            .createdByUserId(API_USER_ID)
            .build();
    claimA = claimRepository.saveAndFlush(claimA);
    createCalculatedFeeDetailWithAmount(claimA, new BigDecimal("100.00"), now);

    Claim claimB =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(sortSubmission)
            .caseReferenceNumber("CRN-B")
            .uniqueFileNumber("UFN-B")
            .matterTypeCode("TEST-MTC")
            .lineNumber(2)
            .status(ClaimStatus.READY_TO_PROCESS)
            .createdByUserId(API_USER_ID)
            .build();
    claimB = claimRepository.saveAndFlush(claimB);
    createCalculatedFeeDetailWithAmount(claimB, new BigDecimal("100.00"), now);

    Claim claimC =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(sortSubmission)
            .caseReferenceNumber("CRN-C")
            .uniqueFileNumber("UFN-C")
            .matterTypeCode("TEST-MTC")
            .lineNumber(3)
            .status(ClaimStatus.READY_TO_PROCESS)
            .createdByUserId(API_USER_ID)
            .build();
    claimC = claimRepository.saveAndFlush(claimC);
    createCalculatedFeeDetailWithAmount(claimC, new BigDecimal("100.00"), now);

    claimRepository.flush();

    // Expected order is deterministic by Claim.id ASC for an ASC primary, and the reverse for DESC
    List<String> expectedAscIdOrder =
        java.util.stream.Stream.of(claimA.getId(), claimB.getId(), claimC.getId())
            .map(UUID::toString)
            .sorted()
            .toList();

    List<String> expectedDescIdOrder =
        java.util.stream.Stream.of(claimA.getId(), claimB.getId(), claimC.getId())
            .map(UUID::toString)
            .sorted(java.util.Comparator.reverseOrder())
            .toList();

    // 1) Verify full list ordering with ASC primary
    MvcResult resultAsc =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", testOffice)
                    .param("sort", "total_amount,asc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    var resultSetAsc =
        OBJECT_MAPPER.readValue(
            resultAsc.getResponse().getContentAsString(), ClaimResultSetV2.class);

    assertThat(resultSetAsc.getContent().stream().map(ClaimResponseV2::getId))
        .containsExactlyElementsOf(expectedAscIdOrder);

    // 2) Verify pagination stability (page size 1) for ASC
    for (int i = 0; i < expectedAscIdOrder.size(); i++) {
      MvcResult pageResult =
          mockMvc
              .perform(
                  get(GET_CLAIMS_ENDPOINT_V2)
                      .param("office_code", testOffice)
                      .param("sort", "total_amount,asc")
                      .param("size", "1")
                      .param("page", String.valueOf(i))
                      .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
              .andExpect(status().isOk())
              .andReturn();

      var pageSet =
          OBJECT_MAPPER.readValue(
              pageResult.getResponse().getContentAsString(), ClaimResultSetV2.class);
      assertThat(pageSet.getContent()).hasSize(1);
      assertThat(pageSet.getContent().getFirst().getId()).isEqualTo(expectedAscIdOrder.get(i));
    }

    // 3) Verify full list ordering with DESC primary uses ID DESC as the tie-break
    MvcResult resultDesc =
        mockMvc
            .perform(
                get(GET_CLAIMS_ENDPOINT_V2)
                    .param("office_code", testOffice)
                    .param("sort", "total_amount,desc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    var resultSetDesc =
        OBJECT_MAPPER.readValue(
            resultDesc.getResponse().getContentAsString(), ClaimResultSetV2.class);

    // All primary values equal; tie-break is deterministic by Claim.id DESC
    assertThat(resultSetDesc.getContent().stream().map(ClaimResponseV2::getId))
        .containsExactlyElementsOf(expectedDescIdOrder);

    // 4) Verify pagination stability (page size 1) for DESC
    for (int i = 0; i < expectedDescIdOrder.size(); i++) {
      MvcResult pageResult =
          mockMvc
              .perform(
                  get(GET_CLAIMS_ENDPOINT_V2)
                      .param("office_code", testOffice)
                      .param("sort", "total_amount,desc")
                      .param("size", "1")
                      .param("page", String.valueOf(i))
                      .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
              .andExpect(status().isOk())
              .andReturn();

      var pageSet =
          OBJECT_MAPPER.readValue(
              pageResult.getResponse().getContentAsString(), ClaimResultSetV2.class);
      assertThat(pageSet.getContent()).hasSize(1);
      assertThat(pageSet.getContent().getFirst().getId()).isEqualTo(expectedDescIdOrder.get(i));
    }
  }
}
