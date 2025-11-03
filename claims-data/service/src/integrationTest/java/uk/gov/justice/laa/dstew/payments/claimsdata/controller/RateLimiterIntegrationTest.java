package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

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
  private Submission submission;

  @BeforeEach
  void setup() {
    clearIntegrationData();
    submission = getSubmissionTestData();
  }

  @DisplayName("Status 200: when a valid submission ID exists in the system")
  @Test
  @Transactional
  void getAllMatterStart_shouldReturnOK() throws Exception {
    for (int i = 0; i < 2; i++) {
      doRequest(status().isOk());
    }
    doRequest(status().isTooManyRequests());
  }

  private void doRequest(ResultMatcher resultMatcher) throws Exception {
    mockMvc
        .perform(
            get(API_URI_PREFIX + GET_ALL_MATTER_STARTS_URI, submission.getId())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(resultMatcher)
        .andReturn();
  }
}
