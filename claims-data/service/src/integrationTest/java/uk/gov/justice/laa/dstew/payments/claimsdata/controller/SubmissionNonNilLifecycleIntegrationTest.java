package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.IntegrationTestUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.dstew.payments.claimsevent.model.SubmissionEventType;

@TestPropertySource(properties = "laa.claims.api.validated-pending-approval.enabled=true")
@DisplayName("Non-NIL validated-pending-approval lifecycle")
class SubmissionNonNilLifecycleIntegrationTest extends AbstractAwsIntegrationTest {

  private static final String SUBMISSION_ENDPOINT = API_URI_PREFIX + "/submissions/{id}";
  private static final String BULK_SUBMISSION_ENDPOINT = API_URI_PREFIX + "/bulk-submissions/{id}";
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";

  @Test
  @DisplayName(
      "Persists VALIDATED_PENDING_APPROVAL for a non-NIL submission and keeps its bulk status aligned")
  void nonNilSubmissionAndBulkUseTheIntermediateStatus() throws Exception {
    UUID bulkSubmissionId = Uuid7.timeBasedUuid();
    UUID submissionId = Uuid7.timeBasedUuid();
    bulkSubmissionRepository.saveAndFlush(
        BulkSubmission.builder()
            .id(bulkSubmissionId)
            .data(new GetBulkSubmission200ResponseDetails())
            .status(BulkSubmissionStatus.READY_FOR_PARSING)
            .createdByUserId(USER_ID)
            .createdOn(CREATED_ON)
            .updatedOn(CREATED_ON)
            .build());
    submissionRepository.saveAndFlush(
        Submission.builder()
            .id(submissionId)
            .bulkSubmissionId(bulkSubmissionId)
            .officeAccountNumber("NONNIL-OFFICE")
            .submissionPeriod("APR-2025")
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .status(SubmissionStatus.CREATED)
            .isNilSubmission(false)
            .numberOfClaims(0)
            .createdByUserId(USER_ID)
            .providerUserId(USER_ID)
            .createdOn(CREATED_ON)
            .build());

    SubmissionPatch submissionPatch =
        SubmissionPatch.builder().status(SubmissionStatus.VALIDATED_PENDING_APPROVAL).build();
    BulkSubmissionPatch bulkPatch =
        new BulkSubmissionPatch().status(BulkSubmissionStatus.VALIDATED_PENDING_APPROVAL);

    mockMvc
        .perform(
            patch(SUBMISSION_ENDPOINT, submissionId)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPatch)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            patch(BULK_SUBMISSION_ENDPOINT, bulkSubmissionId)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(bulkPatch)))
        .andExpect(status().isNoContent());

    assertThat(submissionRepository.findById(submissionId).orElseThrow().getStatus())
        .isEqualTo(SubmissionStatus.VALIDATED_PENDING_APPROVAL);
    assertThat(bulkSubmissionRepository.findById(bulkSubmissionId).orElseThrow().getStatus())
        .isEqualTo(BulkSubmissionStatus.VALIDATED_PENDING_APPROVAL);

    ReceiveMessageResponse receiveResponse =
        IntegrationTestUtils.receiveMessageResponse(sqsClient, queueUrl);
    assertThat(receiveResponse.messages()).hasSize(1);
    assertThat(receiveResponse.messages().getFirst().messageAttributes())
        .containsKey("SubmissionEventType");
    assertThat(
            receiveResponse
                .messages()
                .getFirst()
                .messageAttributes()
                .get("SubmissionEventType")
                .stringValue())
        .isEqualTo(SubmissionEventType.INITIAL_SUBMISSION_VALIDATION_SUCCEEDED.toString());
    IntegrationTestUtils.deleteMessagesFromQueue(sqsClient, queueUrl, receiveResponse);
  }
}
