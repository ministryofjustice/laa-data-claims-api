package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("ClaimHistoryController Integration Test")
public class ClaimHistoryControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String HISTORY_URI = "/api/v1/claims/{claimId}/history";

  @BeforeEach
  void setUp() {
    seedClaimsData();
  }

  @Test
  @DisplayName("Returns a submission-only timeline for a claim with submission source data")
  void returnsSubmissionOnlyTimeline() throws Exception {
    mockMvc
        .perform(get(HISTORY_URI, CLAIM_1_ID).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.claim_id").value(CLAIM_1_ID.toString()))
        .andExpect(jsonPath("$.events").isArray())
        .andExpect(jsonPath("$.events.length()").value(1))
        .andExpect(jsonPath("$.events[0].event_type").value("SUBMISSION"))
        .andExpect(jsonPath("$.events[0].actor_id").value(USER_ID))
        .andExpect(jsonPath("$.events[0].source_id").value(CLAIM_1_ID.toString()))
        .andExpect(jsonPath("$.events[0].event_timestamp").exists())
        .andExpect(jsonPath("$.events[0].metadata.submission_period").value("JAN-2025"))
        .andExpect(
            jsonPath("$.events[0].metadata.office_account_number").value(OFFICE_ACCOUNT_NUMBER_1))
        .andExpect(jsonPath("$.events[0].metadata.area_of_law").value("LEGAL_HELP"));
  }

  @Test
  @DisplayName("Returns 404 for an unknown claim id")
  void returnsNotFoundForUnknownClaim() throws Exception {
    mockMvc
        .perform(
            get(HISTORY_URI, Uuid7.timeBasedUuid())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isNotFound());
  }
}
