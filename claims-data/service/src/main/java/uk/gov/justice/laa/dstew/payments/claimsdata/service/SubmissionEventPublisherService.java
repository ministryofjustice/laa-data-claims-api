package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionQueuePublishException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionValidationQueuePublishException;
import uk.gov.justice.laa.dstew.payments.claimsevent.model.BulkSubmissionMessage;
import uk.gov.justice.laa.dstew.payments.claimsevent.model.SubmissionEventType;
import uk.gov.justice.laa.dstew.payments.claimsevent.model.SubmissionValidationMessage;

/** Service responsible for publishing submission events to the Amazon SNS topic. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionEventPublisherService {

  private final SnsClient snsClient;
  private final ObjectMapper objectMapper;

  @Value("${aws.sns.topic-arn}")
  private String topicArn;

  /**
   * Publishes a bulk submission identifier and its associated submission identifiers to an Amazon
   * SNS topic.
   *
   * @param bulkSubmissionId the unique identifier for the bulk submission
   * @param submissionIds the list of unique identifiers for the individual submissions
   */
  public void publishBulkSubmissionEvent(UUID bulkSubmissionId, List<UUID> submissionIds) {
    BulkSubmissionMessage bulkSubmissionMessage =
        new BulkSubmissionMessage(bulkSubmissionId, submissionIds);

    try {
      publishEvent(bulkSubmissionMessage, SubmissionEventType.PARSE_BULK_SUBMISSION);
    } catch (JsonProcessingException e) {
      throw new BulkSubmissionQueuePublishException(
          "Error when creating parse bulk submission message for bulk submission id ["
              + bulkSubmissionId
              + "] : "
              + e.getMessage(),
          e);
    }
  }

  /**
   * Publishes a submission id for validation to an Amazon SNS topic.
   *
   * @param submissionId the unique identifier for the submission
   */
  public void publishSubmissionValidationEvent(UUID submissionId) {
    SubmissionValidationMessage submissionValidationMessage =
        new SubmissionValidationMessage(submissionId);

    try {
      publishEvent(submissionValidationMessage, SubmissionEventType.VALIDATE_SUBMISSION);
    } catch (JsonProcessingException e) {
      throw new SubmissionValidationQueuePublishException(
          "Error when creating validate submission message for submission id ["
              + submissionId
              + "] : "
              + e.getMessage(),
          e);
    }
  }

  /**
   * Publishes a submission id for validation succeeded event to an Amazon SNS topic.
   *
   * @param submissionId the unique identifier for the submission
   */
  public void publishSubmissionValidationSucceededEvent(UUID submissionId) {
    SubmissionValidationMessage submissionValidationSucceededMessage =
        new SubmissionValidationMessage(submissionId);
    try {
      publishEvent(
          submissionValidationSucceededMessage,
          SubmissionEventType.SUBMISSION_VALIDATION_SUCCEEDED);
    } catch (Exception e) {
      log.error(
          "Failed to publish SUBMISSION_VALIDATION_SUCCEEDED event for submission id [{}]",
          submissionId,
          e);
    }
  }

  /**
   * Publish a submission event with a message attribute describing the type of submission event.
   *
   * @param message the representation of the message to send
   * @param submissionEventType the type of submission event
   * @throws JsonProcessingException when the message could not be serialized
   */
  private void publishEvent(Object message, SubmissionEventType submissionEventType)
      throws JsonProcessingException {
    String messageBody = objectMapper.writeValueAsString(message);

    Map<String, MessageAttributeValue> messageAttributes =
        Map.of("SubmissionEventType", getSubmissionEventTypeAttribute(submissionEventType));

    snsClient.publish(
        PublishRequest.builder()
            .topicArn(topicArn)
            .message(messageBody)
            .messageAttributes(messageAttributes)
            .build());
  }

  private MessageAttributeValue getSubmissionEventTypeAttribute(
      SubmissionEventType submissionEventType) {
    return MessageAttributeValue.builder()
        .dataType("String")
        .stringValue(submissionEventType.toString())
        .build();
  }
}
