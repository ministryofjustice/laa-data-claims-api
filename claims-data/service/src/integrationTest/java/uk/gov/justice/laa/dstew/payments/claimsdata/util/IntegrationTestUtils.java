package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

public class IntegrationTestUtils {

  private static final String SUBMISSION_EVENT_TYPE_ATTRIBUTE = "SubmissionEventType";
  private static final int MAX_MESSAGES_PER_RECEIVE = 10;

  public static ReceiveMessageResponse receiveMessageResponse(
      SqsClient sqsClient, String queueUrl) {
    return sqsClient.receiveMessage(
        ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .maxNumberOfMessages(1)
            .messageAttributeNames("All")
            .waitTimeSeconds(2)
            .build());
  }

  /**
   * Reads every message currently on the queue, deleting each one as it is read, and returns those
   * published with the given {@code SubmissionEventType} message attribute.
   *
   * <p>Assertions on how many events an action published must read the queue to exhaustion,
   * otherwise a duplicate publication is indistinguishable from a single one. The queue is shared
   * by every test in a class, so messages left behind by earlier tests are filtered out by event
   * type rather than assumed absent.
   *
   * @param sqsClient the client to read from the queue with
   * @param queueUrl the queue subscribed to the topic under test
   * @param submissionEventType the event type to return messages for
   * @return the drained messages carrying the requested event type, in the order they were read
   */
  public static List<Message> drainMessagesOfType(
      SqsClient sqsClient, String queueUrl, String submissionEventType) {
    List<Message> drained = new ArrayList<>();
    List<Message> received;

    do {
      received =
          sqsClient
              .receiveMessage(
                  ReceiveMessageRequest.builder()
                      .queueUrl(queueUrl)
                      .maxNumberOfMessages(MAX_MESSAGES_PER_RECEIVE)
                      .messageAttributeNames("All")
                      .waitTimeSeconds(2)
                      .build())
              .messages();

      received.forEach(
          message -> {
            if (submissionEventType.equals(submissionEventTypeOf(message))) {
              drained.add(message);
            }
            deleteMessageFromQueue(sqsClient, queueUrl, message);
          });
    } while (!received.isEmpty());

    return drained;
  }

  private static String submissionEventTypeOf(Message message) {
    return message.messageAttributes().containsKey(SUBMISSION_EVENT_TYPE_ATTRIBUTE)
        ? message.messageAttributes().get(SUBMISSION_EVENT_TYPE_ATTRIBUTE).stringValue()
        : null;
  }

  public static void deleteMessagesFromQueue(
      SqsClient sqsClient, String queueUrl, ReceiveMessageResponse receiveResp) {
    deleteMessageFromQueue(sqsClient, queueUrl, receiveResp.messages().getFirst());
  }

  private static void deleteMessageFromQueue(
      SqsClient sqsClient, String queueUrl, Message message) {
    sqsClient.deleteMessage(
        DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(message.receiptHandle())
            .build());
  }
}
