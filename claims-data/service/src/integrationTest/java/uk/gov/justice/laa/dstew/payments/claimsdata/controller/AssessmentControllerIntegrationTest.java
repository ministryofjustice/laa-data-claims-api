package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.ASSESSMENT_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_SUMMARY_FEE_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_2_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getAssessmentPost;

import java.util.List;
import java.util.UUID;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateAssessment201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AssessmentControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String POST_AN_ASSESSMENT_ENDPOINT =
      ClaimsDataTestUtil.API_URI_PREFIX + "/claims/{claimId}/assessments";
  private static final String GET_ASSESSMENT_URI = "/claims/{claimId}/assessments/{assessmentId}";
  private static final String GET_ASSESSMENTS_URI = "/claims/{claimId}/assessments";

  @BeforeEach
  void setUp() {
    seedAssessmentsData();
  }

  @Test
  void shouldSaveAnAssessmentToDatabase() throws Exception {
    // given: claims test data exists in the database
    final AssessmentPost assessmentPost = getAssessmentPost();

    // when: calling the POST endpoint with the AssessmentPost
    MvcResult result =
        mockMvc
            .perform(
                post(POST_AN_ASSESSMENT_ENDPOINT, CLAIM_1_ID)
                    .content(OBJECT_MAPPER.writeValueAsString(assessmentPost))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isCreated())
            .andReturn();

    // then: response body contains an assessmentId, and the assessment is saved to the database.
    String responseBody = result.getResponse().getContentAsString();
    var createAssessment201Response =
        OBJECT_MAPPER.readValue(responseBody, CreateAssessment201Response.class);
    assertThat(createAssessment201Response.getId()).isNotNull();

    Assessment savedAssessment =
        assessmentRepository
            .findById(createAssessment201Response.getId())
            .orElseThrow(() -> new RuntimeException("Assessment not found"));

    final var updatedClaim =
        claimRepository
            .findById(CLAIM_1_ID)
            .orElseThrow(() -> new RuntimeException("Claim not found exception"));

    assertThat(savedAssessment.getClaim().getId()).isEqualTo(CLAIM_1_ID);
    assertThat(savedAssessment.getClaimSummaryFee().getId()).isEqualTo(CLAIM_1_SUMMARY_FEE_ID);
    assertThat(savedAssessment.getCreatedByUserId()).isEqualTo(API_USER_ID);
    assertTrue(updatedClaim.isHasAssessment());
  }

  @Test
  void shouldReturnBadRequestWhenPostIsCalledWithIncorrectBody() throws Exception {
    // when: calling the POST endpoint with an incorrect body, 400 should be returned
    mockMvc
        .perform(
            post(POST_AN_ASSESSMENT_ENDPOINT, CLAIM_1_ID)
                .content("INVALID_DATA")
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnUnauthorisedWhenPostIsCalledWithInvalidToken() throws Exception {
    final AssessmentPost assessmentPost = getAssessmentPost();
    // when: calling the POST endpoint with an invalid token, 401 should be returned
    mockMvc
        .perform(
            post(POST_AN_ASSESSMENT_ENDPOINT, CLAIM_1_ID)
                .content(OBJECT_MAPPER.writeValueAsString(assessmentPost))
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, INVALID_AUTH_TOKEN))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnNotFoundWhenClaimNotFound() throws Exception {
    AssessmentPost assessmentPost = getAssessmentPost();

    // when: calling the POST endpoint for an unknown claimId, 404 should be returned.
    mockMvc
        .perform(
            post(POST_AN_ASSESSMENT_ENDPOINT, UUID.randomUUID())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(assessmentPost))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnNotFoundWhenClaimSummaryFeeNotFound() throws Exception {
    UUID claimSummaryFeeId = UUID.randomUUID();
    AssessmentPost assessmentPost = getAssessmentPost();
    assessmentPost.setClaimSummaryFeeId(claimSummaryFeeId);

    // when: calling the POST endpoint for an unknown claimSummaryFeeId, 404 should be returned.
    mockMvc
        .perform(
            post(POST_AN_ASSESSMENT_ENDPOINT, CLAIM_1_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(assessmentPost))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  void getAssessmentShouldReturnNotFound() throws Exception {
    mockMvc
        .perform(
            get(API_URI_PREFIX + GET_ASSESSMENT_URI, CLAIM_2_ID, UUID.randomUUID())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @DisplayName("Status 200: when a valid Claim ID & Assessment ID is provided")
  @Test
  void getAssessmentShouldReturnSuccess() throws Exception {
    // when: calling GET endpoint with a valid claim and assessment ID
    MvcResult mvcResult =
        mockMvc
            .perform(
                get(API_URI_PREFIX + GET_ASSESSMENT_URI, CLAIM_1_ID, ASSESSMENT_1_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    AssessmentGet result =
        OBJECT_MAPPER.readValue(mvcResult.getResponse().getContentAsString(), AssessmentGet.class);
    AssertionsForClassTypes.assertThat(result.getClaimId()).isEqualTo(CLAIM_1_ID);
    AssertionsForClassTypes.assertThat(result.getAssessmentOutcome())
        .isEqualTo(AssessmentOutcome.REDUCED_TO_FIXED_FEE);
  }

  @DisplayName("Status 400: when a Assessment ID with an invalid format (non-UUID)")
  @Test
  void getAssessmentShouldReturnBadRequest() throws Exception {
    mockMvc
        .perform(
            get(API_URI_PREFIX + GET_ASSESSMENT_URI, CLAIM_1_ID, "invalid-claim-id")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("Status 401: When authentication token missing")
  @Test
  void getAssessmentShouldReturnForbidden() throws Exception {
    mockMvc
        .perform(get(API_URI_PREFIX + GET_ASSESSMENT_URI, CLAIM_1_ID, ASSESSMENT_1_ID))
        .andExpect(status().isUnauthorized());
  }

  @DisplayName("Status 200: when a valid Claim ID is provided")
  @Test
  void getAssessmentsShouldReturnSuccess() throws Exception {
    // when: calling GET endpoint with a valid claim ID
    MvcResult mvcResult =
        mockMvc
            .perform(
                get(API_URI_PREFIX + GET_ASSESSMENTS_URI, CLAIM_1_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    AssessmentResultSet result =
        OBJECT_MAPPER.readValue(
            mvcResult.getResponse().getContentAsString(), AssessmentResultSet.class);

    List<AssessmentGet> assessments = result.getAssessments();

    assertThat(assessments).isNotEmpty().hasSize(2);

    AssessmentGet first = assessments.getFirst();
    assertThat(first.getClaimId()).isEqualTo(CLAIM_1_ID);
    assertThat(first.getAssessmentOutcome()).isEqualTo(AssessmentOutcome.REDUCED_TO_FIXED_FEE);
    assertThat(first.getClaimSummaryFeeId()).isEqualTo(CLAIM_1_SUMMARY_FEE_ID);
    assertNotNull(first.getCreatedOn());

    AssessmentGet second = assessments.get(1);
    assertThat(second.getClaimId()).isEqualTo(CLAIM_1_ID);
    assertThat(second.getId()).isEqualTo(ASSESSMENT_1_ID);
    assertNotNull(second.getCreatedOn());

    assertThat(assessments)
        .isSortedAccordingTo((a1, a2) -> a2.getCreatedOn().compareTo(a1.getCreatedOn()));
  }
}
