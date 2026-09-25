package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.VALID_CRIME_SCHEDULE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.VALID_OFFICE_ACCOUNT_NUMBER;

import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.IntegrationTestUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.dstew.payments.claimsevent.model.SubmissionEventType;

/**
 * End-to-end coverage for the TEMPORARY (DSTEW-2173) legacy-status compatibility behaviour. With
 * the validated-pending-approval lifecycle disabled by default, a NIL submission that would
 * otherwise be assigned the intermediate status (VALIDATED_PENDING_APPROVAL) is coerced back to the
 * legacy accepted status.
 */
class SubmissionCoercionIntegrationTest extends AbstractAwsIntegrationTest {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  // must match application-test.yml for test-runner token
  private static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";
  private static final String SUBMISSIONS_ENDPOINT = API_URI_PREFIX + "/submissions";
  private static final String SUBMISSION_BY_ID_ENDPOINT = SUBMISSIONS_ENDPOINT + "/{submissionId}";
  private static final String PERIOD_APR_2025 = "APR-2025";

  @Test
  @DisplayName(
      "With the validated-pending-approval lifecycle disabled, a valid NIL submission is stored as "
          + "VALIDATION_SUCCEEDED rather than VALIDATED_PENDING_APPROVAL and publishes "
          + "SUBMISSION_VALIDATION_SUCCEEDED")
  void nilSubmissionUsesLegacyStatusWhenLifecycleDisabled() throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    submissionRepository.deleteAll();

    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .submissionId(submissionId)
            .bulkSubmissionId(null)
            .createdByUserId(USER_ID)
            .numberOfClaims(0)
            .crimeLowerScheduleNumber(VALID_CRIME_SCHEDULE_NUMBER)
            .isNilSubmission(true)
            .officeAccountNumber(VALID_OFFICE_ACCOUNT_NUMBER)
            .providerUserId(USER_ID)
            .status(SubmissionStatus.READY_FOR_VALIDATION)
            .submissionPeriod(PERIOD_APR_2025)
            .submitted(CREATED_ON.atOffset(ZoneOffset.UTC))
            .build();

    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isCreated());

    Submission stored = submissionRepository.findById(submissionId).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(SubmissionStatus.VALIDATION_SUCCEEDED);

    // then: SQS has received a message and the SubmissionEventType attribute equals
    // SUBMISSION_VALIDATION_SUCCEEDED
    ReceiveMessageResponse receiveResp =
        IntegrationTestUtils.receiveMessageResponse(sqsClient, this.queueUrl);

    assertThat(receiveResp.messages()).hasSize(1);
    var message = receiveResp.messages().getFirst();
    assertThat(message.body()).contains(submissionId.toString());
    assertThat(message.messageAttributes()).containsKey("SubmissionEventType");
    assertThat(message.messageAttributes().get("SubmissionEventType").stringValue())
        .isEqualTo(SubmissionEventType.SUBMISSION_VALIDATION_SUCCEEDED.toString());

    IntegrationTestUtils.deleteMessagesFromQueue(sqsClient, this.queueUrl, receiveResp);
  }

  @Test
  @DisplayName(
      "With the validated-pending-approval lifecycle disabled, updating a submission to "
          + "VALIDATED_PENDING_APPROVAL coerces the status and publishes the legacy validation event")
  void submissionPatchUsesLegacyStatusWhenLifecycleDisabled() throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    submissionRepository.saveAndFlush(
        Submission.builder()
            .id(submissionId)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .officeAccountNumber(VALID_OFFICE_ACCOUNT_NUMBER)
            .submissionPeriod(PERIOD_APR_2025)
            .createdByUserId(USER_ID)
            .providerUserId(USER_ID)
            .status(SubmissionStatus.VALIDATION_SUCCEEDED)
            .createdOn(CREATED_ON)
            .build());

    SubmissionPatch patch =
        SubmissionPatch.builder().status(SubmissionStatus.VALIDATED_PENDING_APPROVAL).build();

    mockMvc
        .perform(
            patch(SUBMISSION_BY_ID_ENDPOINT, submissionId)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent());

    Submission updated = submissionRepository.findById(submissionId).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(SubmissionStatus.VALIDATION_SUCCEEDED);

    ReceiveMessageResponse receiveResp =
        IntegrationTestUtils.receiveMessageResponse(sqsClient, this.queueUrl);

    assertThat(receiveResp.messages()).hasSize(1);
    var message = receiveResp.messages().getFirst();
    assertThat(message.body()).contains(submissionId.toString());
    assertThat(message.messageAttributes().get("SubmissionEventType").stringValue())
        .isEqualTo(SubmissionEventType.SUBMISSION_VALIDATION_SUCCEEDED.toString());

    IntegrationTestUtils.deleteMessagesFromQueue(sqsClient, this.queueUrl, receiveResp);
  }

  @Test
  @DisplayName(
      "With the lifecycle disabled, an existing non-NIL held submission is coerced to the legacy "
          + "status")
  void existingNonNilHeldSubmissionUsesLegacyStatusWhenLifecycleDisabled() throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    submissionRepository.saveAndFlush(
        Submission.builder()
            .id(submissionId)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .officeAccountNumber(VALID_OFFICE_ACCOUNT_NUMBER)
            .submissionPeriod(PERIOD_APR_2025)
            .createdByUserId(USER_ID)
            .providerUserId(USER_ID)
            .isNilSubmission(false)
            .numberOfClaims(0)
            .status(SubmissionStatus.VALIDATED_PENDING_APPROVAL)
            .createdOn(CREATED_ON)
            .build());

    SubmissionPatch patch =
        SubmissionPatch.builder().status(SubmissionStatus.VALIDATED_PENDING_APPROVAL).build();

    mockMvc
        .perform(
            patch(SUBMISSION_BY_ID_ENDPOINT, submissionId)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent());

    assertThat(submissionRepository.findById(submissionId).orElseThrow().getStatus())
        .isEqualTo(SubmissionStatus.VALIDATION_SUCCEEDED);

    ReceiveMessageResponse receiveResp =
        IntegrationTestUtils.receiveMessageResponse(sqsClient, this.queueUrl);
    assertThat(receiveResp.messages()).hasSize(1);
    assertThat(receiveResp.messages().getFirst().messageAttributes().get("SubmissionEventType"))
        .extracting(MessageAttributeValue::stringValue)
        .isEqualTo(SubmissionEventType.SUBMISSION_VALIDATION_SUCCEEDED.toString());
    IntegrationTestUtils.deleteMessagesFromQueue(sqsClient, this.queueUrl, receiveResp);
  }
}
