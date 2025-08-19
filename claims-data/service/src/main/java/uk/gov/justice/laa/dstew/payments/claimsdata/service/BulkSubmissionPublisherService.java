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
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionQueuePublishException;

/**
 * Service responsible for publishing bulk submission identifiers
 * and their associated submission identifiers to the Amazon SQS queue.
 */

@Service
@RequiredArgsConstructor
public class BulkSubmissionPublisherService {

  private final SqsClient sqsClient;
  private final ObjectMapper objectMapper;

  @Value("${aws.sqs.queue-name}")
  private String queueName;

  /**
   * Publishes a bulk submission identifier and its associated submission identifiers to an Amazon SQS queue.
   *
   * @param bulkSubmissionId the unique identifier for the bulk submission
   * @param submissionIds the list of unique identifiers for the individual submissions
   */
  public void publish(UUID bulkSubmissionId, List<UUID> submissionIds) {
    String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
            .queueName(queueName)
            .build())
        .queueUrl();

    String messageBody;
    try {
      messageBody = objectMapper.writeValueAsString(
          Map.of(
              "bulk_submission_id", bulkSubmissionId,
              "submission_ids", submissionIds
          )
      );
    } catch (JsonProcessingException e) {
      throw new BulkSubmissionQueuePublishException(
          "Error when creating JSON message for bulk submission id [" + bulkSubmissionId + "] : " + e.getMessage());
    }

    sqsClient.sendMessage(SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageBody(messageBody)
        .build());

  }
}