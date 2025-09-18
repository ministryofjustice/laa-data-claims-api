package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;

@TestInstance(Lifecycle.PER_CLASS)
public class BulkSubmissionControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private BulkSubmissionRepository bulkSubmissionRepository;

  @Autowired private SqsClient sqsClient;

  private static final String AUTHORIZATION_HEADER = "Authorization";

  // must match application-test.yml for test-runner token
  private static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";

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
}
