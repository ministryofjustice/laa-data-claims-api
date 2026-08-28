package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_SUMMARY_FEE_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getAssessmentBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryEvent;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryResultSet;
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

  @Test
  @DisplayName(
      "Preserves an explicit null 'after' in amendment changes as a present-and-null key at the"
          + " HTTP boundary")
  void preservesExplicitNullAfterAsPresentNullKey() throws Exception {
    // A provider-requested clear: before=<value>, after=explicit JSON null. The null must survive
    // end-to-end serialization as a PRESENT key whose value is null - never collapsed to a missing
    // key - so a consumer can distinguish "field cleared" from "field not in this change".
    UUID amendmentId = Uuid7.timeBasedUuid();
    String diffJson =
        "{\"schema_version\":1,\"changes\":["
            + "{\"field_identifier\":\"client.client2Surname\",\"before\":\"Bloggs\","
            + "\"after\":null,\"change_source\":\"REQUESTED\"}]}";

    // Seed inside a committed transaction so the amendment is visible to the separate HTTP request.
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            txStatus ->
                claimAmendmentRepository.save(
                    ClaimAmendment.builder()
                        .id(amendmentId)
                        .claim(claimRepository.getReferenceById(CLAIM_1_ID))
                        .requestedByCode("PROVIDER")
                        .amendmentReasonCode("PROVIDER_ERROR")
                        .beforeState("{}")
                        .requestPayload("{}")
                        .diff(diffJson)
                        .createdByUserId(USER_ID)
                        .createdOn(Instant.now())
                        .build()));

    String body =
        mockMvc
            .perform(get(HISTORY_URI, CLAIM_1_ID).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode events = OBJECT_MAPPER.readTree(body).get("events");
    JsonNode amendment = null;
    for (JsonNode event : events) {
      if ("AMENDMENT".equals(event.path("event_type").asText())) {
        amendment = event;
        break;
      }
    }

    assertThat(amendment).as("an AMENDMENT event is present in the timeline").isNotNull();
    JsonNode change = amendment.get("metadata").get("changes").get(0);
    assertThat(change.get("before").asText()).isEqualTo("Bloggs");
    // The crux: the 'after' key is PRESENT and null, not omitted.
    assertThat(change.has("after")).isTrue();
    assertThat(change.get("after").isNull()).isTrue();
  }

  @Test
  @DisplayName("GET v1/claims/{claimId}/history - page size limits results to requested size")
  void pageSizeLimitsResultsV1History() throws Exception {
    // create deterministic additional history events for the seeded claim
    var created = createManyHistoryEvents(CLAIM_1_ID, 25);

    MvcResult result =
        mockMvc
            .perform(
                get(HISTORY_URI, CLAIM_1_ID)
                    .param("size", "10")
                    .param("page", "0")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    ClaimHistoryResultSet response =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), ClaimHistoryResultSet.class);

    assertThat(response.getTotalElements()).isGreaterThanOrEqualTo(created.size());
    assertThat(response.getSize()).isEqualTo(10);
    assertThat(response.getEvents()).hasSize(10);
  }

  @Test
  @DisplayName("GET v1/claims/{claimId}/history - page offset returns correct subset (items 11-20)")
  void pageOffsetReturnsCorrectSubsetV1History() throws Exception {
    createManyHistoryEvents(CLAIM_1_ID, 25);

    MvcResult page0 =
        mockMvc
            .perform(
                get(HISTORY_URI, CLAIM_1_ID)
                    .param("page", "0")
                    .param("size", "10")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    MvcResult page1 =
        mockMvc
            .perform(
                get(HISTORY_URI, CLAIM_1_ID)
                    .param("page", "1")
                    .param("size", "10")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    ClaimHistoryResultSet r0 =
        OBJECT_MAPPER.readValue(
            page0.getResponse().getContentAsString(), ClaimHistoryResultSet.class);
    ClaimHistoryResultSet r1 =
        OBJECT_MAPPER.readValue(
            page1.getResponse().getContentAsString(), ClaimHistoryResultSet.class);

    assertThat(r0.getEvents()).hasSize(10);
    assertThat(r1.getEvents()).hasSize(10);

    var ids0 = r0.getEvents().stream().map(ClaimHistoryEvent::getSourceId).toList();
    var ids1 = r1.getEvents().stream().map(ClaimHistoryEvent::getSourceId).toList();
    assertThat(ids0).doesNotContainAnyElementsOf(ids1);
  }

  @Test
  @DisplayName("No pageable parameters: defaults to page 0 and size 20")
  void noPageableParametersDefaultsToPageZeroAndSize20History() throws Exception {
    var created = createManyHistoryEvents(CLAIM_1_ID, 25);

    org.springframework.test.web.servlet.MvcResult result =
        mockMvc
            .perform(get(HISTORY_URI, CLAIM_1_ID).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    ClaimHistoryResultSet response =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), ClaimHistoryResultSet.class);

    // Defaults
    assertThat(response.getNumber()).isEqualTo(0);
    assertThat(response.getSize()).isEqualTo(20);
    assertThat(response.getEvents()).hasSize(20);
    assertThat(response.getTotalElements()).isGreaterThanOrEqualTo(created.size());
  }

  private List<UUID> createManyHistoryEvents(UUID claimId, int count) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      java.util.UUID amendmentId = Uuid7.timeBasedUuid();
      String diffJson = "{\"schema_version\":1,\"changes\":[]}";
      ClaimAmendment amendment =
          ClaimAmendment.builder()
              .id(amendmentId)
              .claim(claimRepository.getReferenceById(claimId))
              .requestedByCode("PROVIDER")
              .amendmentReasonCode("PROVIDER_ERROR")
              .beforeState("{}")
              .requestPayload("{}")
              .diff(diffJson)
              .createdByUserId(USER_ID)
              .createdOn(java.time.Instant.now().plusSeconds(i))
              .build();

      new TransactionTemplate(transactionManager)
          .executeWithoutResult(tx -> claimAmendmentRepository.save(amendment));
      ids.add(amendmentId);
    }
    return ids;
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
