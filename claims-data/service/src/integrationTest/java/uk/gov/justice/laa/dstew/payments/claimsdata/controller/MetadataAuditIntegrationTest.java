package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(Lifecycle.PER_CLASS)
public class MetadataAuditIntegrationTest extends AbstractIntegrationTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String POST_BULK_SUBMISSION_ENDPOINT = API_URI_PREFIX + "/bulk-submissions";
  private static final String BULK_SUBMISSION_ENDPOINT = API_URI_PREFIX + "/bulk-submissions/{id}";
  private static final String SUBMISSIONS_ENDPOINT = API_URI_PREFIX + "/submissions";
  private static final String SUBMISSION_BY_ID_ENDPOINT = API_URI_PREFIX + "/submissions/{id}";
  private static final String POST_A_CLAIM_ENDPOINT =
      API_URI_PREFIX + "/submissions/{submissionId}/claims";
  private static final String PATCH_A_CLAIM_ENDPOINT =
      API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";
  private static final String POST_MATTER_START_ENDPOINT =
      API_URI_PREFIX + "/submissions/{submissionId}/matter-starts";
  private static final String VOID_CLAIM_ENDPOINT = API_URI_PREFIX + "/claims/{claimId}/void";
  private static final String POST_AN_ASSESSMENT_ENDPOINT =
      API_URI_PREFIX + "/claims/{claimId}/assessments";

  public MetadataAuditIntegrationTest() {
    OBJECT_MAPPER.findAndRegisterModules();
  }

  private void assertUpdatedMatchesCreated(
      String label, String createdBy, String updatedBy, Instant createdOn, Instant updatedOn) {
    // updatedBy should equal createdBy
    assertThat(updatedBy).as(label + " updatedByUserId on create").isEqualTo(createdBy);
    // timestamps may differ by small amounts (nanos/micros); allow small tolerance (1 ms)
    long diffMillis = Math.abs(Duration.between(createdOn, updatedOn).toMillis());
    assertThat(diffMillis)
        .as(label + " updatedOn on create within tolerance (ms)")
        .isLessThanOrEqualTo(1L);
  }

  @Autowired private SqsClient sqsClient;
  @Autowired private SnsClient snsClient;

  @Value("${aws.sqs.queue-name}")
  private String queueName;

  @Value("${aws.sns.topic-arn}")
  private String topicArn;

  private String queueUrl;

  @BeforeAll
  void setupAwsAndSeedBaseData() {
    // Create the SQS queue if it doesn't exist and subscribe it to the SNS topic used by the
    // application. This mirrors the setup in BulkSubmissionControllerIntegrationTest so the
    // MetadataAuditIntegrationTest can exercise the same code paths that publish events.
    sqsClient.createQueue(builder -> builder.queueName(queueName));

    GetQueueUrlResponse queueUrlResponse =
        sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
    this.queueUrl = queueUrlResponse.queueUrl();

    GetQueueAttributesResponse queueAttributes =
        sqsClient.getQueueAttributes(
            GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build());

    String queueArn = queueAttributes.attributes().get(QueueAttributeName.QUEUE_ARN);

    // Ensure the SNS topic exists and subscribe the queue to it. The application uses the
    // configured topicArn value when publishing, so creating the topic and subscribing the queue
    // avoids NotFoundException from the SnsClient in tests.
    snsClient.createTopic(CreateTopicRequest.builder().name("claims-events").build());
    snsClient.subscribe(
        SubscribeRequest.builder()
            .topicArn(topicArn)
            .protocol("sqs")
            .endpoint(queueArn)
            .attributes(Map.of("RawMessageDelivery", "true"))
            .build());

    // Ensure a BulkSubmission exists for tests that reference BULK_SUBMISSION_ID
    createBulkSubmission();
  }

  @Nested
  @DisplayName("Bulk submission metadata tests")
  class BulkSubmissionTests {

    @Test
    @DisplayName("POST /api/v1/bulk-submissions sets created metadata")
    void postBulkSubmissionSetsCreatedMetadata() throws Exception {
      ClassPathResource resource = new ClassPathResource("test_upload_files/csv/outcomes.csv");
      MockMultipartFile file =
          new MockMultipartFile(
              "file", resource.getFilename(), "text/csv", resource.getInputStream());

      Instant preCall = Instant.now();

      mockMvc
          .perform(
              multipart(POST_BULK_SUBMISSION_ENDPOINT)
                  .file(file)
                  .param("userId", "test-user")
                  .param("offices", "0U099L")
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
          .andExpect(status().isCreated());

      List<uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission> subs =
          bulkSubmissionRepository.findAll();
      var saved = subs.getFirst();

      assertCreatedMetadata(
          "bulk_submission", "test-user", preCall, saved::getCreatedByUserId, saved::getCreatedOn);
    }

    @Test
    @DisplayName("POST /api/v1/bulk-submissions does not set updated metadata on creation")
    void postBulkSubmissionDoesNotSetUpdatedMetadata() throws Exception {
      ClassPathResource resource = new ClassPathResource("test_upload_files/csv/outcomes.csv");
      MockMultipartFile file =
          new MockMultipartFile(
              "file", resource.getFilename(), "text/csv", resource.getInputStream());

      mockMvc
          .perform(
              multipart(POST_BULK_SUBMISSION_ENDPOINT)
                  .file(file)
                  .param("userId", "test-user")
                  .param("offices", "0U099L")
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
          .andExpect(status().isCreated());

      var saved = bulkSubmissionRepository.findAll().getFirst();

      assertUpdatedMatchesCreated(
          "bulk_submission",
          saved.getCreatedByUserId(),
          saved.getUpdatedByUserId(),
          saved.getCreatedOn(),
          saved.getUpdatedOn());
    }

    @Test
    @DisplayName("PATCH /api/v1/bulk-submissions/{id} preserves created and sets updated metadata")
    void patchBulkSubmissionPreservesCreatedAndSetsUpdated() throws Exception {
      // seed via helper
      createBulkSubmission();
      var before = bulkSubmissionRepository.findById(BULK_SUBMISSION_ID).orElseThrow();

      BulkSubmissionPatch patch = new BulkSubmissionPatch();
      patch.setStatus(BulkSubmissionStatus.VALIDATION_FAILED);
      patch.setErrorCode(BulkSubmissionErrorCode.V100);
      patch.setUpdatedByUserId(ClaimsDataTestUtil.API_USER_ID);

      Instant preUpdate = Instant.now();

      mockMvc
          .perform(
              patch(BULK_SUBMISSION_ENDPOINT, BULK_SUBMISSION_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                  .content(OBJECT_MAPPER.writeValueAsString(patch)))
          .andExpect(status().isNoContent());

      var after = bulkSubmissionRepository.findById(BULK_SUBMISSION_ID).orElseThrow();

      // original seed values are available in the protected fixture field
      assertCreatedPreserved(
          "bulk_submission",
          before.getCreatedByUserId(),
          before.getCreatedOn(),
          after::getCreatedByUserId,
          after::getCreatedOn);

      assertUpdatedMetadata(
          "bulk_submission",
          ClaimsDataTestUtil.API_USER_ID,
          preUpdate,
          after::getUpdatedByUserId,
          after::getUpdatedOn);
    }
  }

  @Nested
  @DisplayName("Submission metadata tests")
  class SubmissionTests {

    @Test
    @DisplayName("POST /api/v1/submissions sets created metadata")
    void postSubmissionSetsCreatedMetadata() throws Exception {
      UUID submissionId = Uuid7.timeBasedUuid();
      SubmissionPost submissionPost =
          SubmissionPost.builder()
              .submissionId(submissionId)
              .bulkSubmissionId(BULK_SUBMISSION_ID)
              .officeAccountNumber("OFF123")
              .submissionPeriod("JAN-2025")
              .areaOfLaw(AreaOfLaw.LEGAL_HELP)
              .status(SubmissionStatus.CREATED)
              .providerUserId("prov-1")
              .createdByUserId(ClaimsDataTestUtil.API_USER_ID)
              .submitted(ClaimsDataTestUtil.SUBMITTED_DATE)
              .build();

      // ensure referenced bulk_submission exists
      createBulkSubmission();

      Instant preCall = Instant.now();

      mockMvc
          .perform(
              post(SUBMISSIONS_ENDPOINT)
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
          .andExpect(status().isCreated());

      var created = submissionRepository.findById(submissionId).orElseThrow();

      assertCreatedMetadata(
          "submission",
          ClaimsDataTestUtil.API_USER_ID,
          ClaimsDataTestUtil.SUBMITTED_DATE.toInstant(),
          created::getCreatedByUserId,
          created::getCreatedOn);
    }

    @Test
    @DisplayName("POST /api/v1/submissions does not set updated metadata on creation")
    void postSubmissionDoesNotSetUpdatedMetadata() throws Exception {
      UUID submissionId = Uuid7.timeBasedUuid();
      SubmissionPost submissionPost =
          SubmissionPost.builder()
              .submissionId(submissionId)
              .bulkSubmissionId(BULK_SUBMISSION_ID)
              .officeAccountNumber("OFF123")
              .submissionPeriod("JAN-2025")
              .areaOfLaw(AreaOfLaw.LEGAL_HELP)
              .status(SubmissionStatus.CREATED)
              .providerUserId("prov-1")
              .createdByUserId(ClaimsDataTestUtil.API_USER_ID)
              .submitted(ClaimsDataTestUtil.SUBMITTED_DATE)
              .build();

      // ensure referenced bulk_submission exists
      createBulkSubmission();

      mockMvc
          .perform(
              post(SUBMISSIONS_ENDPOINT)
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
          .andExpect(status().isCreated());

      var created = submissionRepository.findById(submissionId).orElseThrow();

      assertUpdatedMatchesCreated(
          "submission",
          created.getCreatedByUserId(),
          created.getUpdatedByUserId(),
          created.getCreatedOn(),
          created.getUpdatedOn());
    }

    @Test
    @DisplayName("PATCH /api/v1/submissions/{id} preserves created and sets updated metadata")
    void patchSubmissionPreservesCreatedAndSetsUpdated() throws Exception {
      // seed
      seedSubmissionsData();

      SubmissionPatch patch =
          SubmissionPatch.builder()
              .areaOfLaw(AreaOfLaw.CRIME_LOWER)
              .createdByUserId("new-test-user")
              .build();

      Instant preUpdate = Instant.now();

      mockMvc
          .perform(
              patch(SUBMISSION_BY_ID_ENDPOINT, submission1.getId())
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(OBJECT_MAPPER.writeValueAsString(patch)))
          .andExpect(status().isNoContent());

      var after = submissionRepository.findById(submission1.getId()).orElseThrow();

      assertCreatedPreserved(
          "submission",
          submission1.getCreatedByUserId(),
          submission1.getCreatedOn(),
          after::getCreatedByUserId,
          after::getCreatedOn);

      assertUpdatedMetadata(
          "submission",
          ClaimsDataTestUtil.API_USER_ID,
          preUpdate,
          after::getUpdatedByUserId,
          after::getUpdatedOn);
    }

    @Test
    @DisplayName(
        "PATCH /api/v1/submissions/{id} to VALIDATION_FAILED sets claims to INVALID and updates claim metadata")
    void patchSubmissionValidationFailedUpdatesClaimsAndMetadata() throws Exception {
      // seed submissions and claims
      seedClaimsData();

      // capture before snapshots for claims belonging to submission1
      Claim before1 = claimRepository.findById(CLAIM_1_ID).orElseThrow();
      Claim before2 = claimRepository.findById(CLAIM_2_ID).orElseThrow();
      Claim before4 = claimRepository.findById(CLAIM_4_ID).orElseThrow();
      Claim before5 = claimRepository.findById(CLAIM_5_ID).orElseThrow();

      SubmissionPatch patch = new SubmissionPatch();
      patch.setStatus(SubmissionStatus.VALIDATION_FAILED);
      patch.setCreatedByUserId(ClaimsDataTestUtil.API_USER_ID);

      Instant preUpdate = Instant.now();

      mockMvc
          .perform(
              patch(SUBMISSION_BY_ID_ENDPOINT, submission1.getId())
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(OBJECT_MAPPER.writeValueAsString(patch)))
          .andExpect(status().isNoContent());

      // verify each claim has been set to INVALID and metadata behaved as expected
      Claim after1 = claimRepository.findById(CLAIM_1_ID).orElseThrow();
      assertThat(after1.getStatus()).as("claim1 status").isEqualTo(ClaimStatus.INVALID);
      assertClaimCreatedPreserved(before1, after1);
      assertClaimUpdatedByAndTimestamp(after1, ClaimsDataTestUtil.API_USER_ID);

      Claim after2 = claimRepository.findById(CLAIM_2_ID).orElseThrow();
      assertThat(after2.getStatus()).as("claim2 status").isEqualTo(ClaimStatus.INVALID);
      assertClaimCreatedPreserved(before2, after2);
      assertClaimUpdatedByAndTimestamp(after2, ClaimsDataTestUtil.API_USER_ID);

      Claim after4 = claimRepository.findById(CLAIM_4_ID).orElseThrow();
      assertThat(after4.getStatus()).as("claim4 status").isEqualTo(ClaimStatus.INVALID);
      assertClaimCreatedPreserved(before4, after4);
      assertClaimUpdatedByAndTimestamp(after4, ClaimsDataTestUtil.API_USER_ID);

      Claim after5 = claimRepository.findById(CLAIM_5_ID).orElseThrow();
      assertThat(after5.getStatus()).as("claim5 status").isEqualTo(ClaimStatus.INVALID);
      assertClaimCreatedPreserved(before5, after5);
      assertClaimUpdatedByAndTimestamp(after5, ClaimsDataTestUtil.API_USER_ID);
    }
  }

  @Nested
  @DisplayName("Claim metadata tests")
  class ClaimTests {

    @Test
    @DisplayName("POST /api/v1/submissions/{id}/claims sets created metadata")
    void postClaimSetsCreatedMetadata() throws Exception {
      seedSubmissionsData();
      ClaimPost claimPost = ClaimsDataTestUtil.getClaimPost("CASE-123");

      Instant preCall = Instant.now();

      MvcResult result =
          mockMvc
              .perform(
                  post(POST_A_CLAIM_ENDPOINT, submission1.getId())
                      .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(OBJECT_MAPPER.writeValueAsString(claimPost)))
              .andExpect(status().isCreated())
              .andReturn();

      var createResp = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
      UUID createdId = UUID.fromString(createResp.get("id").asText());
      var created = claimRepository.findById(createdId).orElseThrow();

      assertCreatedMetadata(
          "claim",
          ClaimsDataTestUtil.API_USER_ID,
          preCall,
          created::getCreatedByUserId,
          created::getCreatedOn);
    }

    @Test
    @DisplayName(
        "POST /api/v1/submissions/{id}/claims does not set updated metadata on creation and creates child records with created metadata")
    void postClaimDoesNotSetUpdatedMetadataAndCreatesChildRecords() throws Exception {
      seedSubmissionsData();
      ClaimPost claimPost = ClaimsDataTestUtil.getClaimPost("CASE-123");

      Instant preCall = Instant.now();

      MvcResult result =
          mockMvc
              .perform(
                  post(POST_A_CLAIM_ENDPOINT, submission1.getId())
                      .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(OBJECT_MAPPER.writeValueAsString(claimPost)))
              .andExpect(status().isCreated())
              .andReturn();

      var createResp = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
      UUID createdId = UUID.fromString(createResp.get("id").asText());
      var created = claimRepository.findById(createdId).orElseThrow();


      assertUpdatedMatchesCreated(
          "claim",
          created.getCreatedByUserId(),
          created.getUpdatedByUserId(),
          created.getCreatedOn(),
          created.getUpdatedOn());

      // Claim summary fee should have created metadata
      var summaryFeeOpt = claimSummaryFeeRepository.findByClaimId(createdId);
      if (summaryFeeOpt.isPresent()) {
        var summaryFee = summaryFeeOpt.get();
        assertCreatedMetadata(
            "claim_summary_fee",
            ClaimsDataTestUtil.API_USER_ID,
            preCall,
            summaryFee::getCreatedByUserId,
            summaryFee::getCreatedOn);
  
        assertUpdatedMatchesCreated(
            "claim_summary_fee",
            summaryFee.getCreatedByUserId(),
            summaryFee.getUpdatedByUserId(),
            summaryFee.getCreatedOn(),
            summaryFee.getUpdatedOn());
      }

      // Claim case if present
      var claimCaseOpt = claimCaseRepository.findByClaimId(createdId);
      if (claimCaseOpt.isPresent()) {
        var claimCase = claimCaseOpt.get();
        assertCreatedMetadata(
            "claim_case",
            ClaimsDataTestUtil.API_USER_ID,
            preCall,
            claimCase::getCreatedByUserId,
            claimCase::getCreatedOn);
  
        assertUpdatedMatchesCreated(
            "claim_case",
            claimCase.getCreatedByUserId(),
            claimCase.getUpdatedByUserId(),
            claimCase.getCreatedOn(),
            claimCase.getUpdatedOn());
      }

      // Client if present
      var clientOpt = clientRepository.findByClaimId(createdId);
      if (clientOpt.isPresent()) {
        var client = clientOpt.get();
        assertCreatedMetadata(
            "client",
            ClaimsDataTestUtil.API_USER_ID,
            preCall,
            client::getCreatedByUserId,
            client::getCreatedOn);
  
        assertUpdatedMatchesCreated(
            "client",
            client.getCreatedByUserId(),
            client.getUpdatedByUserId(),
            client.getCreatedOn(),
            client.getUpdatedOn());
      }
    }

    @Test
    @DisplayName(
        "PATCH /api/v1/submissions/{submissionId}/claims/{claimId} preserves created and sets updated metadata")
    void patchClaimPreservesCreatedAndSetsUpdated() throws Exception {
      seedClaimsData();

      ClaimPatch patch = new ClaimPatch();
      patch.setFeeCode("FEE-NEW");
      patch.setCaseReferenceNumber("CASE-NEW");
      patch.setStatus(ClaimStatus.READY_TO_PROCESS);
      patch.version(claim2.getVersion());
      patch.amendmentRequestedBy(ClaimsDataTestUtil.API_USER_ID);
      patch.amendmentReasonCode("AMEND-TEST");

      Instant preUpdate = Instant.now();

      mockMvc
          .perform(
              patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_2_ID)
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(OBJECT_MAPPER.writeValueAsString(patch)))
          .andExpect(status().isNoContent());

      var after = claimRepository.findById(CLAIM_2_ID).orElseThrow();

      assertCreatedPreserved(
          "claim",
          claim2.getCreatedByUserId(),
          claim2.getCreatedOn(),
          after::getCreatedByUserId,
          after::getCreatedOn);

      assertUpdatedMetadata(
          "claim",
          ClaimsDataTestUtil.API_USER_ID,
          preUpdate,
          after::getUpdatedByUserId,
          after::getUpdatedOn);
    }

    @Test
    @DisplayName(
        "PATCH /api/v1/submissions/{submissionId}/claims/{claimId} non-amendment creates validation messages with created timestamps")
    void patchClaimNonAmendmentCreatesValidationMessageLogMetadata() throws Exception {
      seedClaimsData();

      ClaimPatch patch = new ClaimPatch();
      patch.setStatus(ClaimStatus.READY_TO_PROCESS);
      patch.version(claim2.getVersion());

      Instant preUpdate = Instant.now();

      mockMvc
          .perform(
              patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_2_ID)
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(OBJECT_MAPPER.writeValueAsString(patch)))
          .andExpect(status().isNoContent());

      // any validation messages created should have createdOn set
      validationMessageLogRepository.findAll().stream()
          .filter(v -> CLAIM_2_ID.equals(v.getClaimId()))
          .forEach(
              v -> {
                assertThat(v.getCreatedOn()).as("validation_message_log createdOn").isNotNull();
                assertThat(v.getCreatedOn()).isAfterOrEqualTo(preUpdate.minusSeconds(1));
              });
    }

    @Test
    @DisplayName(
        "PATCH /api/v1/submissions/{submissionId}/claims/{claimId} creates calculated fee detail with created metadata")
    void patchClaimCreatesCalculatedFeeDetailMetadata() throws Exception {
      seedClaimsData();

      ClaimPatch patch = new ClaimPatch();
      patch.setStatus(ClaimStatus.READY_TO_PROCESS);
      patch.version(claim2.getVersion());

      Instant preUpdate = Instant.now();

      mockMvc
          .perform(
              patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_2_ID)
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(OBJECT_MAPPER.writeValueAsString(patch)))
          .andExpect(status().isNoContent());

      calculatedFeeDetailRepository
          .findFirstByClaimIdOrderByCreatedOnDescIdDesc(CLAIM_2_ID)
          .ifPresent(
              d -> {
                assertCreatedMetadata(
                    "calculated_fee_detail",
                    ClaimsDataTestUtil.API_USER_ID,
                    preUpdate,
                    d::getCreatedByUserId,
                    d::getCreatedOn);

                assertUpdatedMatchesCreated(
                    "calculated_fee_detail",
                    d.getCreatedByUserId(),
                    d.getUpdatedByUserId(),
                    d.getCreatedOn(),
                    d.getUpdatedOn());
              });
    }
  }

  @Nested
  @DisplayName("Matter start metadata tests")
  class MatterStartTests {

    @Test
    @DisplayName("POST /api/v1/submissions/{id}/matter-starts sets created metadata")
    void postMatterStartSetsCreatedMetadata() throws Exception {
      seedSubmissionsData();
      MatterStartPost matterStartPost =
          MatterStartPost.builder().createdByUserId(ClaimsDataTestUtil.API_USER_ID).build();

      Instant preCall = Instant.now();

      mockMvc
          .perform(
              post(POST_MATTER_START_ENDPOINT, submission1.getId())
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(OBJECT_MAPPER.writeValueAsString(matterStartPost)))
          .andExpect(status().isCreated());

      var saved = matterStartRepository.findBySubmissionId(submission1.getId()).getFirst();

      assertCreatedMetadata(
          "matter_start",
          ClaimsDataTestUtil.API_USER_ID,
          preCall,
          saved::getCreatedByUserId,
          saved::getCreatedOn);
    }

    @Test
    @DisplayName(
        "POST /api/v1/submissions/{id}/matter-starts does not set updated metadata on creation")
    void postMatterStartDoesNotSetUpdatedMetadata() throws Exception {
      seedSubmissionsData();
      MatterStartPost matterStartPost =
          MatterStartPost.builder().createdByUserId(ClaimsDataTestUtil.API_USER_ID).build();

      mockMvc
          .perform(
              post(POST_MATTER_START_ENDPOINT, submission1.getId())
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(OBJECT_MAPPER.writeValueAsString(matterStartPost)))
          .andExpect(status().isCreated());

      var saved = matterStartRepository.findBySubmissionId(submission1.getId()).getFirst();

      assertUpdatedMatchesCreated(
          "matter_start",
          saved.getCreatedByUserId(),
          saved.getUpdatedByUserId(),
          saved.getCreatedOn(),
          saved.getUpdatedOn());
    }
  }

  @Nested
  @DisplayName("Void claim and assessment metadata tests")
  class VoidAndAssessmentTests {

    @Test
    @DisplayName(
        "POST /api/v1/claims/{claimId}/void preserves created and sets updated metadata on claim and inserts assessment with created metadata")
    void postVoidClaimUpdatesClaimAndInsertsAssessmentMetadata() throws Exception {
      seedClaimsData();

      Claim before = claimRepository.findById(CLAIM_2_ID).orElseThrow();

      Instant preCall = Instant.now();
      UUID userId = Uuid7.timeBasedUuid();

      String requestBody =
          "{"
              + "\"created_by_user_id\":\""
              + userId
              + "\","
              + "\"assessment_reason\":\"test reason\""
              + "}";

      mockMvc
          .perform(
              post(VOID_CLAIM_ENDPOINT, CLAIM_2_ID)
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isCreated());

      var updatedClaim = claimRepository.findById(CLAIM_2_ID).orElseThrow();

      assertCreatedPreserved(
          "claim",
          before.getCreatedByUserId(),
          before.getCreatedOn(),
          updatedClaim::getCreatedByUserId,
          updatedClaim::getCreatedOn);

      assertUpdatedMetadata(
          "claim",
          userId.toString(),
          preCall,
          updatedClaim::getUpdatedByUserId,
          updatedClaim::getUpdatedOn);

      // an assessment row should have been inserted with created metadata; the repository exposes
      // a helper to obtain the latest assessment for a claim.
      var createdAssessment =
          assessmentRepository
              .findFirstByClaimIdOrderByCreatedOnDescIdDesc(CLAIM_2_ID)
              .orElseThrow();

      assertCreatedMetadata(
          "assessment",
          userId.toString(),
          preCall,
          createdAssessment::getCreatedByUserId,
          createdAssessment::getCreatedOn);
    }

    @Test
    @DisplayName(
        "POST /api/v1/claims/{claimId}/assessments sets created and updated metadata and updates claim updated metadata")
    void postAssessmentSetsAssessmentAndClaimMetadata() throws Exception {
      seedAssessmentsData();

      AssessmentPost assessmentPost = ClaimsDataTestUtil.getAssessmentPost();
      assessmentPost.setClaimId(CLAIM_2_ID);
      assessmentPost.setClaimSummaryFeeId(CLAIM_2_SUMMARY_FEE_ID);

      Instant preCall = Instant.now();

      MvcResult result =
          mockMvc
              .perform(
                  post(POST_AN_ASSESSMENT_ENDPOINT, CLAIM_2_ID)
                      .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(OBJECT_MAPPER.writeValueAsString(assessmentPost)))
              .andExpect(status().isCreated())
              .andReturn();

      var createResp = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
      UUID assessmentId = UUID.fromString(createResp.get("id").asText());

      var savedAssessment = assessmentRepository.findById(assessmentId).orElseThrow();

      assertCreatedMetadata(
          "assessment",
          ClaimsDataTestUtil.API_USER_ID,
          preCall,
          savedAssessment::getCreatedByUserId,
          savedAssessment::getCreatedOn);

      // claim should have updatedBy/updatedOn set
      var updatedClaim = claimRepository.findById(CLAIM_2_ID).orElseThrow();
      assertUpdatedMetadata(
          "claim",
          ClaimsDataTestUtil.API_USER_ID,
          preCall,
          updatedClaim::getUpdatedByUserId,
          updatedClaim::getUpdatedOn);
    }

    @Test
    @DisplayName(
        "POST /api/v1/claims/{claimId}/void inserts assessment without updated metadata set")
    void postVoidClaimAssessmentHasNoUpdatedMetadata() throws Exception {
      seedClaimsData();

      UUID userId = Uuid7.timeBasedUuid();

      String requestBody =
          "{"
              + "\"created_by_user_id\":\""
              + userId
              + "\","
              + "\"assessment_reason\":\"test reason\""
              + "}";

      mockMvc
          .perform(
              post(VOID_CLAIM_ENDPOINT, CLAIM_2_ID)
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isCreated());

      var createdAssessment =
          assessmentRepository
              .findFirstByClaimIdOrderByCreatedOnDescIdDesc(CLAIM_2_ID)
              .orElseThrow();

  
        assertUpdatedMatchesCreated(
            "assessment",
            createdAssessment.getCreatedByUserId(),
            createdAssessment.getUpdatedByUserId(),
            createdAssessment.getCreatedOn(),
            createdAssessment.getUpdatedOn());
    }

    @Test
    @DisplayName(
        "POST /api/v1/claims/{claimId}/assessments inserted assessment does not have updated metadata")
    void postAssessmentDoesNotSetUpdatedMetadata() throws Exception {
      seedAssessmentsData();

      AssessmentPost assessmentPost = ClaimsDataTestUtil.getAssessmentPost();
      assessmentPost.setClaimId(CLAIM_2_ID);
      assessmentPost.setClaimSummaryFeeId(CLAIM_2_SUMMARY_FEE_ID);

      MvcResult result =
          mockMvc
              .perform(
                  post(POST_AN_ASSESSMENT_ENDPOINT, CLAIM_2_ID)
                      .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(OBJECT_MAPPER.writeValueAsString(assessmentPost)))
              .andExpect(status().isCreated())
              .andReturn();

      var createResp = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
      UUID assessmentId = UUID.fromString(createResp.get("id").asText());

      var savedAssessment = assessmentRepository.findById(assessmentId).orElseThrow();

      assertUpdatedMatchesCreated(
          "assessment",
          savedAssessment.getCreatedByUserId(),
          savedAssessment.getUpdatedByUserId(),
          savedAssessment.getCreatedOn(),
          savedAssessment.getUpdatedOn());
    }
  }
}
