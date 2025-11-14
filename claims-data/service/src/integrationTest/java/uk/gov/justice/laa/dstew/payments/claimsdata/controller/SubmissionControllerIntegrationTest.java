package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.SubmissionService.DECIMAL_PLACES;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.IntegrationTestUtils;

@TestInstance(Lifecycle.PER_CLASS)
public class SubmissionControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private SubmissionRepository submissionRepository;

  @Autowired private BulkSubmissionRepository bulkSubmissionRepository;

  @Autowired private ValidationMessageLogRepository validationMessageLogRepository;

  @Autowired private ClaimRepository claimRepository;

  @Autowired private MatterStartRepository matterStartRepository;

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
    // creating some data on DB
    submission = getSubmissionTestData();
  }

  @AfterEach
  void close() {
    // delete everything from DB
    clearIntegrationData();
  }

  @Test
  void postSubmission_shouldCreate() throws Exception {
    // given: a SubmissionPost payload
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .submissionId(submission.getId())
            .bulkSubmissionId(submission.getBulkSubmissionId())
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
            .submissionPeriod("JAN-25")
            .areaOfLaw(AREA_OF_LAW)
            .status(SubmissionStatus.CREATED)
            .providerUserId(BULK_SUBMISSION_CREATED_BY_USER_ID)
            .createdByUserId(API_USER_ID)
            .build();

    // when: calling POST endpoint for submissions
    mockMvc
        .perform(
            post(API_URI_PREFIX + "/submissions")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isCreated());

    Submission createdSubmission = submissionRepository.findById(submission.getId()).orElseThrow();

    // then: submission is correctly created
    assertThat(createdSubmission.getOfficeAccountNumber()).isEqualTo(OFFICE_ACCOUNT_NUMBER);
    assertThat(createdSubmission.getAreaOfLaw()).isEqualTo(AREA_OF_LAW);

    assertThat(createdSubmission.getProviderUserId()).isEqualTo(BULK_SUBMISSION_CREATED_BY_USER_ID);
    assertThat(createdSubmission.getCreatedByUserId()).isEqualTo(API_USER_ID);
  }

  @Test
  void postSubmission_shouldReturnBadRequest_WhenProviderUserIdIsNull() throws Exception {
    // given: a SubmissionPost payload with null providerUserId
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .submissionId(submission.getId())
            .bulkSubmissionId(submission.getBulkSubmissionId())
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
            .submissionPeriod("JAN-25")
            .areaOfLaw(AREA_OF_LAW)
            .status(SubmissionStatus.CREATED)
            .providerUserId(null)
            .build();

    // when: calling POST endpoint for submissions, should return a bad request.
    mockMvc
        .perform(
            post(API_URI_PREFIX + "/submissions")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postSubmission_shouldReturnBadRequest() throws Exception {
    // when: calling post endpoint without a valid payload, should return bad request
    mockMvc
        .perform(
            post(API_URI_PREFIX + "/submissions")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(new SubmissionPost())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getSubmission_Returns200() throws Exception {
    createClaimsTestData();

    // when: calling get endpoint with and ID
    MvcResult result =
        mockMvc
            .perform(
                get(API_URI_PREFIX + "/submissions/{id}", SUBMISSION_1_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    var submissionResult =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), SubmissionResponse.class);

    // then: submission is correctly retrieved
    assertThat(submissionResult.getSubmissionId()).isEqualTo(SUBMISSION_1_ID);
    assertThat(submissionResult.getCalculatedTotalAmount())
        .isEqualTo(
            calculatedFeeDetail1
                .getTotalAmount()
                .add(calculatedFeeDetail2.getTotalAmount())
                .setScale(DECIMAL_PLACES, RoundingMode.HALF_UP));
    assertThat(submissionResult.getNumberOfClaims()).isEqualTo(0);
    assertThat(submissionResult.getMatterStarts().size()).isEqualTo(0);

    assertThat(submissionResult.getProviderUserId()).isEqualTo(BULK_SUBMISSION_CREATED_BY_USER_ID);
  }

  @Test
  void getSubmission_ReturnsNotFound() throws Exception {
    // when: calling get endpoint without a valid ID, should return not found
    mockMvc
        .perform(
            get(API_URI_PREFIX + "/submissions/{id}", UUID.randomUUID())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  void getSubmissions_Returns200() throws Exception {
    // when: calling get all submissions endpoint with a valid office
    MvcResult result =
        mockMvc
            .perform(
                get(API_URI_PREFIX + "/submissions")
                    .param("offices", OFFICE_ACCOUNT_NUMBER)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();

    // then: submissions are correctly retrieved
    var submissionsResultSet = OBJECT_MAPPER.readValue(responseBody, SubmissionsResultSet.class);
    SubmissionBase submissionBase = submissionsResultSet.getContent().getFirst();
    assertThat(submissionBase.getSubmissionId()).isEqualTo(submission.getId());
    assertThat(submissionBase.getStatus()).isEqualTo(SubmissionStatus.CREATED);
    assertThat(submissionBase.getProviderUserId()).isEqualTo(submission.getProviderUserId());
  }

  @Test
  void getSubmissions_NoOffices_BadRequest() throws Exception {
    // when: calling get all submissions endpoint without an office, should return bad request
    mockMvc
        .perform(
            get(API_URI_PREFIX + "/submissions").header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @Test
  void updateSubmission_shouldUpdate() throws Exception {
    // given: a Submission patch payload with the changes to make
    SubmissionPatch patch = SubmissionPatch.builder().areaOfLaw(AREA_OF_LAW).build();

    // when: calling the patch endpoint
    mockMvc
        .perform(
            patch(API_URI_PREFIX + "/submissions/{id}", submission.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent())
        .andReturn();

    // then: should update the submission
    Submission updated = submissionRepository.findById(submission.getId()).orElseThrow();
    assertThat(updated.getAreaOfLaw()).isEqualTo(AREA_OF_LAW);
  }

  @Test
  void updateSubmission_shouldPublishValidationEventAndUpdate() throws Exception {
    // given: a submission patch payload changing the status to READY_FOR_VALIDATION
    SubmissionPatch patch =
        SubmissionPatch.builder().status(SubmissionStatus.READY_FOR_VALIDATION).build();

    // when: calling the patch endpoint
    mockMvc
        .perform(
            patch(API_URI_PREFIX + "/submissions/{id}", submission.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent())
        .andReturn();

    // then: should update and send validation event
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
    // given: a submission patch with validation messages
    ValidationMessagePatch messagePatch =
        ValidationMessagePatch.builder()
            .type(ValidationMessageType.WARNING)
            .source("SOURCE")
            .displayMessage("DISPLAY_MESSAGE")
            .build();

    SubmissionPatch patch =
        SubmissionPatch.builder().validationMessages(List.of(messagePatch)).build();

    // when: calling the patch endpoint
    mockMvc
        .perform(
            patch(API_URI_PREFIX + "/submissions/{id}", submission.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent())
        .andReturn();

    // then: should save new validation messages
    List<ValidationMessageLog> logs = validationMessageLogRepository.findAll();
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getDisplayMessage()).isEqualTo("DISPLAY_MESSAGE");
    assertThat(logs.getFirst().getType()).isEqualTo(ValidationMessageType.WARNING);
    assertThat(logs.getFirst().getSource()).isEqualTo("SOURCE");
    validationMessageLogRepository.deleteAll();
  }

  @Test
  void updateSubmission_shouldReturnNotFound() throws Exception {
    // given: a submission patch payload
    SubmissionPatch patch =
        SubmissionPatch.builder().status(SubmissionStatus.READY_FOR_VALIDATION).build();

    // when: calling patch endpoint without a valid submission ID, should return Not Found
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
    // given: and invalid JSON payload
    String invalidJson = "{ \"status\": \"INVALID_ENUM\" }";

    // when: calling the patch endpoint, should return Bad Request
    mockMvc
        .perform(
            patch(API_URI_PREFIX + "/submissions/{id}", submission.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(invalidJson)))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @DisplayName("Should return result with area of law and submission period")
  @Test
  void shouldReturnResultWithAreaOfLawAndSubmissionPeriod() throws Exception {

    MvcResult result =
        mockMvc
            .perform(
                get(API_URI_PREFIX + "/submissions")
                    .param("offices", OFFICE_ACCOUNT_NUMBER)
                    .param("areaOfLaw", String.valueOf(AREA_OF_LAW))
                    .param("submissionPeriod", SUBMISSION_PERIOD)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();

    // then: submissions are correctly retrieved
    var submissionsResultSet = OBJECT_MAPPER.readValue(responseBody, SubmissionsResultSet.class);

    assertThat(submissionsResultSet.getContent().getFirst().getAreaOfLaw()).isEqualTo(AREA_OF_LAW);
    assertThat(submissionsResultSet.getContent().getFirst().getSubmissionPeriod())
        .isEqualTo(SUBMISSION_PERIOD);
    assertThat(submissionsResultSet.getContent().getFirst().getStatus())
        .isEqualTo(SubmissionStatus.CREATED);
  }

  @Test
  void updateSubmission_shouldUpdateAllClaimsAsInvalid_WhenSubmissionIsInvalid() throws Exception {
    createClaimsTestData();
    // given: a Submission patch payload with the changes to make
    SubmissionPatch patch =
        SubmissionPatch.builder()
            .areaOfLaw(AREA_OF_LAW)
            .status(SubmissionStatus.VALIDATION_FAILED)
            .build();
    // Verify that Claims are not invalid before patching
    claimRepository
        .findBySubmissionId(submission1.getId())
        .forEach(claim -> assertThat(claim.getStatus()).isNotEqualTo(ClaimStatus.INVALID));

    // when: calling the patch endpoint
    mockMvc
        .perform(
            patch(API_URI_PREFIX + "/submissions/{id}", submission1.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent())
        .andReturn();

    // then: should update the submission and all claims as invalid
    Submission updated = submissionRepository.findById(submission1.getId()).orElseThrow();
    claimRepository
        .findBySubmissionId(submission1.getId())
        .forEach(claim -> assertThat(claim.getStatus()).isEqualTo(ClaimStatus.INVALID));
  }
}
