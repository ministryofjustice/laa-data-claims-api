package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimService;

@WebMvcTest(ClaimController.class)
@ImportAutoConfiguration(
    exclude = {
      org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
    })
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class ClaimControllerTest {

  private static final String SUBMISSIONS_CLAIMS_URI = API_URI_PREFIX + "/submissions";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ClaimService claimService;

  @Test
  void createClaim_returnsCreatedStatusAndLocationHeader() throws Exception {
    final UUID submissionId = UUID.randomUUID();
    final UUID claimId = UUID.randomUUID();
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
            + "\"outcome_code\":\"OUT01\""
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
    final UUID submissionId = UUID.randomUUID();
    final UUID claimId = UUID.randomUUID();
    final ClaimFields claimFields =
        new ClaimFields()
            .status(ClaimStatus.VALID)
            .scheduleReference("SCH-777")
            .lineNumber(42)
            .caseReferenceNumber("CRN-777")
            .uniqueFileNumber("UFN-777")
            .caseStartDate("2025-06-01")
            .caseConcludedDate("2025-06-30")
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
    final UUID submissionId = UUID.randomUUID();
    final UUID claimId = UUID.randomUUID();
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
}
