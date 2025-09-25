package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

public class IntegrationTestUtils {

  public static ReceiveMessageResponse receiveMessageResponse(
      SqsClient sqsClient, String queueUrl) {
    return sqsClient.receiveMessage(
        ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .maxNumberOfMessages(1)
            .waitTimeSeconds(2)
            .build());
  }

  public static void deleteMessagesFromQueue(
      SqsClient sqsClient, String queueUrl, ReceiveMessageResponse receiveResp) {
    sqsClient.deleteMessage(
        DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiveResp.messages().getFirst().receiptHandle())
            .build());
  }
}
