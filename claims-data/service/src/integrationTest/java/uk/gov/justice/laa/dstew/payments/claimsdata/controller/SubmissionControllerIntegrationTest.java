package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.IntegrationTestUtils;

@TestInstance(Lifecycle.PER_CLASS)
public class SubmissionControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private SubmissionRepository submissionRepository;

  @Autowired private BulkSubmissionRepository bulkSubmissionRepository;

  @Autowired private ValidationMessageLogRepository validationMessageLogRepository;

  @Autowired private SqsClient sqsClient;

  @Value("${aws.sqs.queue-name}")
  private String queueName;

  private String queueUrl;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String AUTHORIZATION_HEADER = "Authorization";

  // must match application-test.yml for test-runner token
  private static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";

  private BulkSubmission bulkSubmission;

  private Submission submission;

  @BeforeAll
  void initialSetup() {
    OBJECT_MAPPER.registerModule(new JavaTimeModule());

    // create the queue if it doesn't exist
    sqsClient.createQueue(builder -> builder.queueName(queueName));

    // then get its URL
    GetQueueUrlResponse queueUrlResponse =
        sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
    this.queueUrl = queueUrlResponse.queueUrl();
  }

  @BeforeEach
  void setup() {
    submissionRepository.deleteAll();
    bulkSubmissionRepository.deleteAll();
    validationMessageLogRepository.deleteAll();

    bulkSubmission =
        BulkSubmission.builder()
            .data(new GetBulkSubmission200ResponseDetails())
            .status(BulkSubmissionStatus.READY_FOR_PARSING)
            .createdByUserId(USER_ID)
            .createdOn(Instant.now())
            .updatedOn(Instant.now())
            .build();
    bulkSubmissionRepository.save(bulkSubmission);

    submission =
        Submission.builder()
            .id(SUBMISSION_1_ID)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("office1")
            .submissionPeriod("JAN-25")
            .areaOfLaw("CIVIL")
            .status(SubmissionStatus.CREATED)
            .scheduleNumber("office1/CIVIL")
            .previousSubmissionId(SUBMISSION_1_ID)
            .isNilSubmission(false)
            .numberOfClaims(5)
            .createdByUserId(USER_ID)
            .build();
    submission = submissionRepository.save(submission);
  }

  @Test
  void shouldGetSubmissions_Returns200() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get(API_URI_PREFIX + "/submissions")
                    .param("offices", "office1")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();

    var submissionsResultSet = OBJECT_MAPPER.readValue(responseBody, SubmissionsResultSet.class);
    assertThat(submissionsResultSet.getContent().getFirst().getSubmissionId())
        .isEqualTo(submission.getId());
    assertThat(submissionsResultSet.getContent().getFirst().getStatus())
        .isEqualTo(SubmissionStatus.CREATED);
  }

  @Test
  void shouldNotGetSubmissions_NoOffices_BadRequest() throws Exception {
    mockMvc
        .perform(
            get(API_URI_PREFIX + "/submissions").header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @Test
  void updateSubmission_shouldUpdate() throws Exception {
    SubmissionPatch patch = SubmissionPatch.builder().areaOfLaw("PENAL").build();

    mockMvc
        .perform(
            patch(API_URI_PREFIX + "/submissions/{id}", submission.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent())
        .andReturn();

    Submission updated = submissionRepository.findById(submission.getId()).orElseThrow();
    assertThat(updated.getAreaOfLaw()).isEqualTo("PENAL");
  }

  @Test
  void updateSubmission_shouldPublishValidationEventAndUpdate() throws Exception {
    SubmissionPatch patch =
        SubmissionPatch.builder().status(SubmissionStatus.READY_FOR_VALIDATION).build();

    mockMvc
        .perform(
            patch(API_URI_PREFIX + "/submissions/{id}", submission.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent())
        .andReturn();

    Submission updated = submissionRepository.findById(submission.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(SubmissionStatus.READY_FOR_VALIDATION);

    ReceiveMessageResponse receiveResp =
        IntegrationTestUtils.receiveMessageResponse(sqsClient, this.queueUrl);
    assertThat(receiveResp.messages()).hasSize(1);
    assertThat(receiveResp.messages().getFirst().body()).contains(updated.getId().toString());
    // Delete the message from the queue.
    IntegrationTestUtils.deleteMessagesFromQueue(sqsClient, this.queueUrl, receiveResp);
  }

  @Test
  void updateSubmission_shouldHandleValidationMessages() throws Exception {
    ValidationMessagePatch messagePatch =
        ValidationMessagePatch.builder()
            .type(ValidationMessageType.WARNING)
            .source("SOURCE")
            .displayMessage("DISPLAY_MESSAGE")
            .build();

    SubmissionPatch patch =
        SubmissionPatch.builder().validationMessages(List.of(messagePatch)).build();

    mockMvc
        .perform(
            patch(API_URI_PREFIX + "/submissions/{id}", submission.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent())
        .andReturn();

    List<ValidationMessageLog> logs = validationMessageLogRepository.findAll();
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getDisplayMessage()).isEqualTo("DISPLAY_MESSAGE");
    assertThat(logs.getFirst().getType()).isEqualTo(ValidationMessageType.WARNING);
    assertThat(logs.getFirst().getSource()).isEqualTo("SOURCE");
    validationMessageLogRepository.deleteAll();
  }

  @Test
  void updateSubmission_shouldReturnNotFound() throws Exception {
    SubmissionPatch patch =
        SubmissionPatch.builder().status(SubmissionStatus.READY_FOR_VALIDATION).build();
    mockMvc
        .perform(
            patch(API_URI_PREFIX + "/submissions/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  void updateSubmission_shouldReturnBadRequest() throws Exception {
    String invalidJson = "{ \"status\": \"INVALID_ENUM\" }";

    mockMvc
        .perform(
            patch(API_URI_PREFIX + "/submissions/{id}", submission.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(invalidJson)))
        .andExpect(status().isBadRequest())
        .andReturn();
  }
}
