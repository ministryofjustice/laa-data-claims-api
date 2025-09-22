package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

@TestInstance(Lifecycle.PER_CLASS)
public class BulkSubmissionControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private BulkSubmissionRepository bulkSubmissionRepository;

  @Autowired
  private SqsClient sqsClient;

  private static final String AUTHORIZATION_HEADER = "Authorization";

  // must match application-test.yml for test-runner token
  private static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Value("${aws.sqs.queue-name}")
  private String queueName;

  private String queueUrl;

  @BeforeAll
  void setup() {
    bulkSubmissionRepository.deleteAll();

    // create the queue if it doesn't exist
    sqsClient.createQueue(builder -> builder.queueName(queueName));

    // then get its URL
    GetQueueUrlResponse queueUrlResponse =
        sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
    this.queueUrl = queueUrlResponse.queueUrl();
  }

  @Test
  void shouldSaveSubmissionToDatabaseAndPublishMessage() throws Exception {
    // given: a fake file
    ClassPathResource resource = new ClassPathResource("test_upload_files/csv/outcomes.csv");

    MockMultipartFile file =
        new MockMultipartFile(
            "file", resource.getFilename(), "text/csv", resource.getInputStream());

    // when: calling the POST endpoint with the file
    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/v0/bulk-submissions")
                    .file(file)
                    .param("userId", "test-user")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isCreated())
            .andReturn();

    // then: response body contains IDs
    String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody).contains("bulk_submission_id");
    assertThat(responseBody).contains("submission_ids");

    // then: database has persisted entity
    List<BulkSubmission> submissions = bulkSubmissionRepository.findAll();
    assertThat(submissions).hasSize(1);
    BulkSubmission saved = submissions.getFirst();
    assertThat(saved.getCreatedByUserId()).isEqualTo("test-user");
    assertThat(saved.getStatus()).isEqualTo(BulkSubmissionStatus.READY_FOR_PARSING);

    // then: SQS has received a message
    ReceiveMessageResponse receiveResp =
        sqsClient.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(this.queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(2)
                .build());
    assertThat(receiveResp.messages()).hasSize(1);
    assertThat(receiveResp.messages().getFirst().body()).contains(saved.getId().toString());
    // Delete the message from the queue.
    sqsClient.deleteMessage(
        DeleteMessageRequest.builder()
            .queueUrl(this.queueUrl)
            .receiptHandle(receiveResp.messages().getFirst().receiptHandle())
            .build());
  }

  @Test
  void shouldGetBulkSubmissionById() throws Exception {
    // given: a bulk submission is saved to the database
    GetBulkSubmission200ResponseDetails bulkSubmission200ResponseDetails = new GetBulkSubmission200ResponseDetails()
        .addMatterStartsItem(ClaimsDataTestUtil.getBulkSubmissionMatterStart())
        .addOutcomesItem(ClaimsDataTestUtil.getBulkSubmissionOutcome())
        .office(ClaimsDataTestUtil.getBulkSubmissionOffice())
        .schedule(ClaimsDataTestUtil.getBulkSubmissionSchedule());
    var bulkSubmission =
        BulkSubmission.builder()
            .data(bulkSubmission200ResponseDetails)
            .status(BulkSubmissionStatus.READY_FOR_PARSING)
            .createdByUserId(USER_ID)
            .createdOn(Instant.now())
            .build();
    BulkSubmission savedBulkSubmission = bulkSubmissionRepository.save(bulkSubmission);

    // when: calling the GET endpoint with the ID
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v0/bulk-submissions/{id}", savedBulkSubmission.getId().toString())
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: response body contains bulk_submission_id, status and details
    String responseBody = result.getResponse().getContentAsString();

    GetBulkSubmission200Response getBulkSubmission200Response = OBJECT_MAPPER.readValue(responseBody, GetBulkSubmission200Response.class);
    assertThat(getBulkSubmission200Response.getBulkSubmissionId()).isEqualTo(savedBulkSubmission.getId());
    assertThat(getBulkSubmission200Response.getStatus()).isEqualTo(BulkSubmissionStatus.READY_FOR_PARSING);
    assertThat(getBulkSubmission200Response.getDetails()).isEqualTo(bulkSubmission200ResponseDetails);
  }

}
