package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_ID;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionsResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.SubmissionService;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@WebMvcTest(SubmissionController.class)
@ImportAutoConfiguration(
    exclude = {
      org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
    })
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class SubmissionControllerTest {
  private static final String SUBMISSIONS_URI = API_URI_PREFIX + "/submissions";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SubmissionService submissionService;

  @Test
  void createSubmission_returnsCreatedStatusAndLocationHeader() throws Exception {
    UUID id = Uuid7.timeBasedUuid();
    when(submissionService.createSubmission(any(SubmissionPost.class))).thenReturn(id);

    String body =
        "{"
            + "\"submission_id\": \""
            + id
            + "\","
            + "\"bulk_submission_id\": \""
            + Uuid7.timeBasedUuid()
            + "\","
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
        .andExpect(
            content()
                .string(
                    "{"
                        + "\"type\":\"about:blank\","
                        + "\"title\":\"Bad Request\","
                        + "\"status\":400,"
                        + "\"detail\":\"Invalid request content.\","
                        + "\"instance\":\""
                        + SUBMISSIONS_URI
                        + "\"}"));

    verify(submissionService, never()).createSubmission(any());
  }

  @Test
  void getSubmission_returnsSubmissionDetails() throws Exception {
    UUID id = Uuid7.timeBasedUuid();
    SubmissionResponse response =
        new SubmissionResponse()
            .submissionId(id)
            .bulkSubmissionId(Uuid7.timeBasedUuid())
            .officeAccountNumber("12345")
            .submissionPeriod("2025-07")
            .areaOfLaw("CIVIL")
            .isNilSubmission(false)
            .claims(java.util.List.of())
            .matterStarts(java.util.List.of());
    when(submissionService.getSubmission(id)).thenReturn(response);

    mockMvc
        .perform(get(SUBMISSIONS_URI + "/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.submission_id").value(id.toString()));
  }

  @Test
  void updateSubmission_returnsNoContent() throws Exception {
    UUID id = Uuid7.timeBasedUuid();
    String body = "{\"schedule_number\":\"123\"}";

    mockMvc
        .perform(
            patch(SUBMISSIONS_URI + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());

    verify(submissionService).updateSubmission(org.mockito.ArgumentMatchers.eq(id), any());
  }

  @Test
  void getSubmissions_returnsSubmissionDetails() throws Exception {
    var submissionBase = new SubmissionBase();
    var expected = new SubmissionsResultSet().content(List.of(submissionBase));

    when(submissionService.getSubmissionsResultSet(
            anyList(),
            anyString(),
            any(LocalDate.class),
            any(LocalDate.class),
            any(Pageable.class)))
        .thenReturn(expected);

    String jsonContent = objectMapper.writeValueAsString(expected);

    mockMvc
        .perform(
            get(SUBMISSIONS_URI)
                .queryParam("offices", String.valueOf(List.of("office1", "office2", "office3")))
                .queryParam("submissionId", String.valueOf(SUBMISSION_ID))
                .queryParam("submittedDateFrom", String.valueOf(LocalDate.of(2025, 1, 1)))
                .queryParam("submittedDateTo", String.valueOf(LocalDate.of(2025, 12, 31)))
                .queryParam("pageable", String.valueOf(Pageable.unpaged())))
        .andExpect(status().isOk())
        .andExpect(content().json(jsonContent));
  }
}
