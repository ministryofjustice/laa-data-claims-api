package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * Simple service for publishing events to an SNS topic.
 *
 * <p>Events are serialized to JSON and published to the topic managed by {@link TopicManager}.
 */
@Service
@Slf4j
public class EventPublisher {

  private final SnsClient snsClient;
  private final TopicManager topicManager;
  private final ObjectMapper mapper = new ObjectMapper();

  public EventPublisher(SnsClient snsClient, TopicManager topicManager) {
    this.snsClient = snsClient;
    this.topicManager = topicManager;
  }

  /**
   * Publish an event object to the configured SNS topic. The event will be serialized to JSON.
   *
   * @param event the event object to publish
   */
  public void publish(Object event) {
    try {
      String json = mapper.writeValueAsString(event);

      String topicArn = topicManager.getTopicArn();
      if (topicArn == null || topicArn.isBlank()) {
        log.debug("SNS topic ARN is not configured - skipping publish for event: {}", json);
        return;
      }

      try {
        snsClient.publish(PublishRequest.builder().topicArn(topicArn).message(json).build());
        log.info("Published event: {}", json);
      } catch (software.amazon.awssdk.core.exception.SdkClientException e) {
        // Don't fail the application when SNS isn't reachable in test environments.
        log.warn("Failed to publish event to SNS, skipping. Reason: {}", e.getMessage());
      }

    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize event", e);
    }
  }
}
