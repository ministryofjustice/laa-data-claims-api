package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetSubmission200Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.SubmissionService;

@WebMvcTest(SubmissionController.class)
@ImportAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.security.servlet
                                 .SecurityAutoConfiguration.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class SubmissionControllerTest {
  private static final String SUBMISSIONS_URI = API_URI_PREFIX + "/submissions";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SubmissionService submissionService;

  @Test
  void createSubmission_returnsCreatedStatusAndLocationHeader() throws Exception {
    UUID id = UUID.randomUUID();
    when(submissionService.createSubmission(any(SubmissionPost.class))).thenReturn(id);

    String body = "{"
        + "\"submission_id\": \"" + id + "\","
        + "\"bulk_submission_id\": \"" + UUID.randomUUID() + "\","
        + "\"office_account_number\": \"12345\","
        + "\"submission_period\": \"2025-07\","
        + "\"area_of_law\": \"crime\","
        + "\"status\": \"CREATED\","
        + "\"is_nil_submission\": false,"
        + "\"number_of_claims\": 1"
        + "}";

    mockMvc
        .perform(post(SUBMISSIONS_URI).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", containsString(SUBMISSIONS_URI + "/" + id)))
        .andExpect(jsonPath("$.id").value(id.toString()));
  }

  @Test
  void createSubmission_returnsBadRequestStatusWhenInvalidBody() throws Exception {
    mockMvc
        .perform(post(SUBMISSIONS_URI).contentType(MediaType.APPLICATION_JSON).content("{ }"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("{"
            + "\"type\":\"about:blank\","
            + "\"title\":\"Bad Request\","
            + "\"status\":400,"
            + "\"detail\":\"Invalid request content.\","
            + "\"instance\":\"" + SUBMISSIONS_URI + "\"}"));

    verify(submissionService, never()).createSubmission(any());
  }

  @Test
  void getSubmission_returnsSubmissionDetails() throws Exception {
    UUID id = UUID.randomUUID();
    SubmissionFields fields =
        new SubmissionFields()
            .submissionId(id)
            .bulkSubmissionId(UUID.randomUUID())
            .officeAccountNumber("12345")
            .submissionPeriod("2025-07")
            .areaOfLaw("crime")
            .isNilSubmission(false)
            .numberOfClaims(1);
    GetSubmission200Response response =
        new GetSubmission200Response().submission(fields).claims(java.util.List.of()).matterStarts(java.util.List.of());
    when(submissionService.getSubmission(id)).thenReturn(response);

    mockMvc
        .perform(get(SUBMISSIONS_URI + "/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.submission.submission_id").value(id.toString()));
  }

  @Test
  void updateSubmission_returnsNoContent() throws Exception {
    UUID id = UUID.randomUUID();
    String body = "{\"schedule_number\":\"123\"}";

    mockMvc
        .perform(
            patch(SUBMISSIONS_URI + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());

    verify(submissionService).updateSubmission(org.mockito.ArgumentMatchers.eq(id), any());
  }
}
