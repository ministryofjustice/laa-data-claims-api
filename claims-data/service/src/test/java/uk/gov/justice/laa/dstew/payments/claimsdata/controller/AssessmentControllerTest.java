package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.AssessmentService;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@WebMvcTest(AssessmentController.class)
@ImportAutoConfiguration(
    exclude = {
      org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
    })
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class AssessmentControllerTest {

  private static final String CLAIMS_URI = API_URI_PREFIX + "/claims";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AssessmentService assessmentService;

  @Test
  void createAssessment_returnsCreatedStatusAndLocationHeader() throws Exception {
    final UUID claimId = Uuid7.timeBasedUuid();
    final UUID assessmentId = Uuid7.timeBasedUuid();
    when(assessmentService.createAssessment(eq(claimId), any(AssessmentPost.class)))
        .thenReturn(assessmentId);

    final String body =
        "{"
            + "\"claim_id\":\"c2ecc377-3223-49c3-999b-08ea461bbd1e\","
            + "\"claim_summary_fee_id\":\"8dae05c8-1ebd-464d-8a37-8548fd051527\","
            + "\"assessment_outcome\":\"NILLED\","
            + "\"created_by_user_id\":\"test-user\""
            + "}";

    mockMvc
        .perform(
            post(CLAIMS_URI + "/{claimId}/assessments", claimId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    "Location",
                    containsString(CLAIMS_URI + "/" + claimId + "/assessments/" + assessmentId)))
        .andExpect(jsonPath("$.id").value(assessmentId.toString()));

    verify(assessmentService).createAssessment(eq(claimId), any(AssessmentPost.class));
  }
}
