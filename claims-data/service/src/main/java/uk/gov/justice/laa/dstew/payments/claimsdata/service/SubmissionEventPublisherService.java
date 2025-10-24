package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionQueuePublishException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionValidationQueuePublishException;
import uk.gov.justice.laa.dstew.payments.claimsevent.model.BulkSubmissionMessage;
import uk.gov.justice.laa.dstew.payments.claimsevent.model.SubmissionEventType;
import uk.gov.justice.laa.dstew.payments.claimsevent.model.SubmissionValidationMessage;

/** Service responsible for publishing submission events to the Amazon SQS queue. */
@Service
@RequiredArgsConstructor
public class SubmissionEventPublisherService {

  private final SqsClient sqsClient;
  private final ObjectMapper objectMapper;

  @Value("${aws.sqs.queue-name}")
  private String queueName;

  /**
   * Publishes a bulk submission identifier and its associated submission identifiers to an Amazon
   * SQS queue.
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
   * Publishes a submission id for validation to an Amazon SQS queue.
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
   * Publish a submission event with a message attribute describing the type of submission event.
   *
   * @param message the representation of the message to send
   * @param submissionEventType the type of submission event
   * @throws JsonProcessingException when the message could not be serialized
   */
  private void publishEvent(Object message, SubmissionEventType submissionEventType)
      throws JsonProcessingException {
    String queueUrl =
        sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();

    String messageBody = objectMapper.writeValueAsString(message);

    Map<String, MessageAttributeValue> messageAttributes =
        Map.of("SubmissionEventType", getSubmissionEventTypeAttribute(submissionEventType));

    sqsClient.sendMessage(
        SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(messageBody)
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
