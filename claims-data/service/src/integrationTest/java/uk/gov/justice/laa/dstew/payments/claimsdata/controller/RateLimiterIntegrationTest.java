package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultMatcher;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;

@ActiveProfiles("limit-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RateLimiterIntegrationTest extends AbstractIntegrationTest {

  private static final String GET_ALL_MATTER_STARTS_URI =
      "/submissions/{submissionId}/matter-starts";
  private static final String GET_SUBMISSION_URI = "/submissions/{submissionId}";
  private static final String GET_CLAIMS_URI = "/claims";
  private static final String GET_BULK_SUBMISSION_URI = "/bulk-submissions/{id}";
  private static final String GET_VALIDATION_MESSAGES_URI = "/validation-messages";
  private Submission submission;

  @BeforeEach
  void setup() {
    submission = getSubmissionTestData();
  }

  @DisplayName("Status 429 for Matter Start: When Rate Limit exceeded")
  @Test
  @Transactional
  void rateLimit_getAllMatterStart() throws Exception {
    for (int i = 0; i < 2; i++) {
      doGetAllMatterStartRequest(status().isOk());
    }
    doGetAllMatterStartRequest(status().isTooManyRequests());
  }

  private void doGetAllMatterStartRequest(ResultMatcher resultMatcher) throws Exception {
    mockMvc
        .perform(
            get(API_URI_PREFIX + GET_ALL_MATTER_STARTS_URI, submission.getId())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(resultMatcher)
        .andReturn();
  }

  @DisplayName("Status 429 for Submission: When Rate Limit exceeded")
  @Test
  @Transactional
  void rateLimit_getSubmission() throws Exception {
    for (int i = 0; i < 2; i++) {
      doGetSubmissionRequest(status().isOk());
    }
    doGetSubmissionRequest(status().isTooManyRequests());
  }

  private void doGetSubmissionRequest(ResultMatcher resultMatcher) throws Exception {
    mockMvc
        .perform(
            get(API_URI_PREFIX + GET_SUBMISSION_URI, submission.getId())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(resultMatcher)
        .andReturn();
  }

  @DisplayName("Status 429 for Get Claims: When Rate Limit exceeded")
  @Test
  @Transactional
  void rateLimit_getClaims() throws Exception {
    for (int i = 0; i < 2; i++) {
      doGetClaimsRequest(status().isOk());
    }
    doGetClaimsRequest(status().isTooManyRequests());
  }

  private void doGetClaimsRequest(ResultMatcher resultMatcher) throws Exception {
    mockMvc
        .perform(
            get(API_URI_PREFIX + GET_CLAIMS_URI)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .param("office_code", "OFF_123"))
        .andExpect(resultMatcher)
        .andReturn();
  }

  @DisplayName("Status 429 for Get Bulk Submission: When Rate Limit exceeded")
  @Test
  @Transactional
  void rateLimit_getBulkSubmission() throws Exception {
    for (int i = 0; i < 2; i++) {
      doGetBulkSubmissionRequest(status().isOk());
    }
    doGetBulkSubmissionRequest(status().isTooManyRequests());
  }

  private void doGetBulkSubmissionRequest(ResultMatcher resultMatcher) throws Exception {
    mockMvc
        .perform(
            get(API_URI_PREFIX + GET_BULK_SUBMISSION_URI, submission.getBulkSubmissionId())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(resultMatcher)
        .andReturn();
  }

  @DisplayName("Status 429 for Get Validation messages: When Rate Limit exceeded")
  @Test
  @Transactional
  void rateLimit_getValidationMessages() throws Exception {
    for (int i = 0; i < 2; i++) {
      doGetValidationMessagesRequest(status().isOk());
    }
    doGetValidationMessagesRequest(status().isTooManyRequests());
  }

  private void doGetValidationMessagesRequest(ResultMatcher resultMatcher) throws Exception {
    mockMvc
        .perform(
            get(API_URI_PREFIX + GET_VALIDATION_MESSAGES_URI)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .param("submission-id", submission.getId().toString()))
        .andExpect(resultMatcher)
        .andReturn();
  }
}
