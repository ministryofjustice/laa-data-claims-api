package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;

/** Responsible for ensuring the SNS topic exists and exposing its ARN for publishing. */
@Component
@Slf4j
public class TopicManager {

  private final SnsClient snsClient;
  @Getter private String topicArn;

  public TopicManager(SnsClient snsClient) {
    this.snsClient = snsClient;
  }

  /** Initialize the SNS topic on application startup. */
  @PostConstruct
  public void init() {
    try {
      CreateTopicResponse response =
          snsClient.createTopic(CreateTopicRequest.builder().name("pubsub-demo").build());
      this.topicArn = response.topicArn();
      log.info("SNS Topic Ready: {}", topicArn);
    } catch (software.amazon.awssdk.core.exception.SdkClientException e) {
      // In test environments AWS credentials or a LocalStack endpoint may not be available.
      // Fail gracefully by setting an empty topic ARN so publishing is skipped.
      this.topicArn = "";
      log.warn("Could not create SNS topic - proceeding without SNS. Reason: {}", e.getMessage());
    }
  }
}
