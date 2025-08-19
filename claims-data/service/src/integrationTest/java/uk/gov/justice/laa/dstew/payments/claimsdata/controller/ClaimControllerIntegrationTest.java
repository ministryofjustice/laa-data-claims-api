package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.ClaimsDataApplication;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;

@ActiveProfiles("test")
@SpringBootTest(classes = ClaimsDataApplication.class)
@AutoConfigureMockMvc
@Transactional
@Testcontainers
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ClaimControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired
  private BulkSubmissionRepository bulkSubmissionRepository;

  @Autowired
  private SqsClient sqsClient;

  @Container @ServiceConnection
  public static final PostgreSQLContainer<?> postgresContainer =
      new PostgreSQLContainer<>("postgres:latest");

  static LocalStackContainer localStack = new LocalStackContainer(
      DockerImageName.parse("localstack/localstack:3.2"))
      .withServices(SQS);

  @Value("${aws.sqs.queue-name}")
  private String queueName;

  private String queueUrl;

  @BeforeAll
  void setup() {
    // Start LocalStack container
    localStack.start();

    GetQueueUrlResponse queueUrlResponse = sqsClient.getQueueUrl(
        GetQueueUrlRequest.builder()
            .queueName(queueName)
            .build()
    );

    this.queueUrl = queueUrlResponse.queueUrl();
  }

  private static final String AUTHORIZATION_HEADER = "Authorization";

  // must match application-test.yml for test-runner token
  private static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";

  @Test
  void shouldSaveSubmissionToDatabaseAndPublishMessage() throws Exception {
    // given: a fake file
    ClassPathResource resource = new ClassPathResource("test_upload_files/csv/outcomes.csv");

    MockMultipartFile file = new MockMultipartFile(
        "file",
        resource.getFilename(),
        "text/csv",
        resource.getInputStream()
    );

    // when: calling the POST endpoint with the file
    MvcResult result = mockMvc.perform(multipart("/api/v0/bulk-submissions")
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
    ReceiveMessageResponse receiveResp = sqsClient.receiveMessage(
        ReceiveMessageRequest.builder()
            .queueUrl(this.queueUrl)
            .maxNumberOfMessages(1)
            .waitTimeSeconds(2)
            .build());
    assertThat(receiveResp.messages()).hasSize(1);
    assertThat(receiveResp.messages().getFirst().body()).contains(saved.getId().toString());
    //Delete the message from the queue.
    sqsClient.deleteMessage(DeleteMessageRequest.builder()
        .queueUrl(this.queueUrl)
        .receiptHandle(receiveResp.messages().getFirst().receiptHandle())
        .build());
  }

  //TODO: DSTEW-321 add more scenarios & add sql scripts to populate db with test data
  @ParameterizedTest(name = """
      GIVEN submissionId={0} and claimId={1}
      WHEN requesting a claim
      THEN the response status is {2}
      """)
  @CsvSource({
    // submissionId, claimId, expectedStatus
    "32765fbb-b258-4c20-a212-b68085843590, 49c5bc98-9b64-4f34-a2f6-861f06c1b95a, 404",
  })
  void shouldRequestClaimWithStatus(UUID submissionId, UUID claimId, int expectedStatus)
      throws Exception {
    mockMvc
        .perform(
            get(
                    API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}",
                    submissionId,
                    claimId)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().is(expectedStatus));
  }
}
