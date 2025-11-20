package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_SUMMARY_FEE_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getAssessmentPost;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateAssessment201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AssessmentControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String POST_AN_ASSESSMENT_ENDPOINT =
      ClaimsDataTestUtil.API_URI_PREFIX + "/claims/{claimId}/assessments";

  @Test
  void shouldSaveAnAssessmentToDatabase() throws Exception {
    // given: claims test data exists in the database
    createClaimsTestData();
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

    assertThat(savedAssessment.getClaim().getId()).isEqualTo(CLAIM_1_ID);
    assertThat(savedAssessment.getClaimSummaryFee().getId()).isEqualTo(CLAIM_1_SUMMARY_FEE_ID);
    assertThat(savedAssessment.getCreatedByUserId()).isEqualTo(API_USER_ID);
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
    createClaimsTestData();
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
    createClaimsTestData();
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
}
