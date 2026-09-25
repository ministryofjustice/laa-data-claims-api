package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * Base class for integration tests that exercise the SQS/SNS messaging path. Wires the LocalStack
 * queue and topic exactly once per test class ({@link TestInstance.Lifecycle#PER_CLASS}): it
 * creates the queue, resolves its URL and ARN, creates the SNS topic and subscribes the queue with
 * raw message delivery so published events are received with their original attributes intact.
 *
 * <p>Subclasses read {@link #queueUrl} and use the injected {@link #sqsClient} to assert on the
 * messages that reach the queue. Tests that do not touch messaging should extend {@link
 * AbstractIntegrationTest} directly so they do not pay for this wiring.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractAwsIntegrationTest extends AbstractIntegrationTest {

  private static final String CLAIMS_EVENTS_TOPIC_NAME = "claims-events";

  @Autowired protected SqsClient sqsClient;
  @Autowired private SnsClient snsClient;

  @Value("${aws.sqs.queue-name}")
  private String queueName;

  @Value("${aws.sns.topic-arn}")
  private String topicArn;

  protected String queueUrl;

  @BeforeAll
  void setUpAwsMessaging() {
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

    snsClient.createTopic(CreateTopicRequest.builder().name(CLAIMS_EVENTS_TOPIC_NAME).build());
    snsClient.subscribe(
        SubscribeRequest.builder()
            .topicArn(topicArn)
            .protocol("sqs")
            .endpoint(queueArn)
            .attributes(Map.of("RawMessageDelivery", "true"))
            .build());
  }
}
