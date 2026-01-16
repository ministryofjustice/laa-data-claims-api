package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_ID;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimService;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@WebMvcTest(ClaimController.class)
@ImportAutoConfiguration(
    exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc(addFilters = false)
class ClaimControllerTest {

  private static final String SUBMISSIONS_CLAIMS_URI = API_URI_PREFIX + "/submissions";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ClaimService claimService;

  @Test
  void createClaim_returnsCreatedStatusAndLocationHeader() throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    when(claimService.createClaim(eq(submissionId), any(ClaimPost.class))).thenReturn(claimId);

    final String body =
        "{"
            + "\"status\":\"READY_TO_PROCESS\","
            + "\"schedule_reference\":\"SCH-001\","
            + "\"line_number\":1,"
            + "\"case_reference_number\":\"CRN-123\","
            + "\"unique_file_number\":\"UFN-999\","
            + "\"case_start_date\":\"2025-07-01\","
            + "\"case_concluded_date\":\"2025-07-31\","
            + "\"matter_type_code\":\"MAT01\","
            + "\"outcome_code\":\"OUT01\","
            + "\"created_by_user_id\":\"test-user\""
            + "}";

    mockMvc
        .perform(
            post(SUBMISSIONS_CLAIMS_URI + "/{id}/claims", submissionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    "Location",
                    containsString(
                        SUBMISSIONS_CLAIMS_URI + "/" + submissionId + "/claims/" + claimId)))
        .andExpect(jsonPath("$.id").value(claimId.toString()));

    verify(claimService).createClaim(eq(submissionId), any(ClaimPost.class));
  }

  @Test
  void getClaim_returnsClaimDetails() throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final ClaimResponse claimFields =
        new ClaimResponse()
            .status(ClaimStatus.VALID)
            .scheduleReference("SCH-777")
            .lineNumber(42)
            .caseReferenceNumber("CRN-777")
            .uniqueFileNumber("UFN-777")
            .caseStartDate("01/06/2025")
            .caseConcludedDate("30/06/2025")
            .matterTypeCode("MAT77")
            .outcomeCode("OUT77");
    when(claimService.getClaim(submissionId, claimId)).thenReturn(claimFields);

    mockMvc
        .perform(
            get(
                SUBMISSIONS_CLAIMS_URI + "/{submission-id}/claims/{claim-id}",
                submissionId,
                claimId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VALID"))
        .andExpect(jsonPath("$.schedule_reference").value("SCH-777"))
        .andExpect(jsonPath("$.line_number").value(42))
        .andExpect(jsonPath("$.case_reference_number").value("CRN-777"))
        .andExpect(jsonPath("$.unique_file_number").value("UFN-777"))
        .andExpect(jsonPath("$.matter_type_code").value("MAT77"))
        .andExpect(jsonPath("$.outcome_code").value("OUT77"));

    verify(claimService).getClaim(submissionId, claimId);
  }

  @Test
  void updateClaim_returnsNoContent() throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final String body = "{ \"status\": \"INVALID\" }";

    mockMvc
        .perform(
            patch(
                    SUBMISSIONS_CLAIMS_URI + "/{submission-id}/claims/{claim-id}",
                    submissionId,
                    claimId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());

    verify(claimService).updateClaim(eq(submissionId), eq(claimId), any(ClaimPatch.class));
  }

  @Test
  void getClaims_returnsClaimDetails() throws Exception {
    var claimResponse = new ClaimResponse();
    var expected = new ClaimResultSet().content(List.of(claimResponse));

    when(claimService.getClaimResultSet(
            anyString(),
            anyString(),
            anyList(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyList(),
            anyString(),
            anyString(),
            any(Pageable.class)))
        .thenReturn(expected);

    String jsonContent = OBJECT_MAPPER.writeValueAsString(expected);

    mockMvc
        .perform(
            get(API_URI_PREFIX + "/claims")
                .queryParam("office_code", "office_123")
                .queryParam("submission_id", String.valueOf(SUBMISSION_ID))
                .queryParam(
                    "submission_statuses",
                    String.valueOf(SubmissionStatus.CREATED),
                    String.valueOf(SubmissionStatus.REPLACED))
                .queryParam("fee_code", "fee_123")
                .queryParam("unique_file_number", "UFN_123")
                .queryParam("unique_client_number", "UCN_123")
                .queryParam("unique_case_id", "UC_ID_123")
                .queryParam(
                    "claim_statuses",
                    String.valueOf(ClaimStatus.VALID),
                    String.valueOf(ClaimStatus.INVALID))
                .queryParam("submission_period", "APR-2025")
                .queryParam("case_reference_number", "CASE_123")
                .queryParam("pageable", String.valueOf(Pageable.unpaged())))
        .andExpect(status().isOk())
        .andExpect(content().json(jsonContent));
  }
}
