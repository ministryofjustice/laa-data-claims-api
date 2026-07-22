package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_SUMMARY_FEE_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getAssessmentBuilder;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("ClaimHistoryController Integration Test")
public class ClaimHistoryControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String HISTORY_URI = "/api/v1/claims/{claimId}/history";

  @Autowired private PlatformTransactionManager transactionManager;

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

  @Test
  @DisplayName("Surfaces an ESCAPE_CASE_ASSESSMENT row as an ASSESSMENT timeline event")
  void returnsAssessmentEventInTimeline() throws Exception {
    // Assessment is inserted with the current timestamp, so it is newer than the seeded submission
    // (dated in the past) and appears first in the reverse-chronological timeline.
    persistAssessment(
        AssessmentType.ESCAPE_CASE_ASSESSMENT,
        AssessmentOutcome.REDUCED_TO_FIXED_FEE,
        "Escape fee case assessment");

    mockMvc
        .perform(get(HISTORY_URI, CLAIM_1_ID).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events.length()").value(2))
        .andExpect(jsonPath("$.events[0].event_type").value("ASSESSMENT"))
        .andExpect(jsonPath("$.events[0].actor_id").value(USER_ID))
        .andExpect(jsonPath("$.events[0].event_timestamp").exists())
        .andExpect(jsonPath("$.events[0].metadata.assessment_type").value("ESCAPE_CASE_ASSESSMENT"))
        .andExpect(
            jsonPath("$.events[0].metadata.assessment_outcome").value("REDUCED_TO_FIXED_FEE"))
        .andExpect(
            jsonPath("$.events[0].metadata.assessment_reason").value("Escape fee case assessment"))
        .andExpect(jsonPath("$.events[1].event_type").value("SUBMISSION"));
  }

  @Test
  @DisplayName("Surfaces an assessment_type = 'VOID' row as a VOID timeline event")
  void returnsVoidEventInTimeline() throws Exception {
    persistAssessment(AssessmentType.VOID, null, "Voided in error");

    mockMvc
        .perform(get(HISTORY_URI, CLAIM_1_ID).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events.length()").value(2))
        .andExpect(jsonPath("$.events[0].event_type").value("VOID"))
        .andExpect(jsonPath("$.events[0].metadata.assessment_type").value("VOID"))
        .andExpect(jsonPath("$.events[0].metadata.assessment_reason").value("Voided in error"))
        // A VOID event never carries an assessment outcome.
        .andExpect(jsonPath("$.events[0].metadata.assessment_outcome").doesNotExist())
        .andExpect(jsonPath("$.events[1].event_type").value("SUBMISSION"));
  }

  private void persistAssessment(AssessmentType type, AssessmentOutcome outcome, String reason) {
    // Seed inside a committed transaction so the fetched claim/summary-fee stay attached for the
    // save and the data is visible to the subsequent (separate) HTTP request.
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status ->
                assessmentRepository.save(
                    getAssessmentBuilder()
                        .id(Uuid7.timeBasedUuid())
                        .claim(claimRepository.getReferenceById(CLAIM_1_ID))
                        .claimSummaryFee(
                            claimSummaryFeeRepository.getReferenceById(CLAIM_1_SUMMARY_FEE_ID))
                        .assessmentType(type)
                        .assessmentOutcome(outcome)
                        .assessmentReason(reason)
                        .allowedTotalVat(new BigDecimal("100.00"))
                        .allowedTotalInclVat(new BigDecimal("120.00"))
                        .build()));
  }
}
