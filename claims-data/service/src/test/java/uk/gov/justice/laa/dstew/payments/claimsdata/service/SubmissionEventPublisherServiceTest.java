package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.dstew.payments.claimsevent.model.SubmissionEventType;

@ExtendWith(MockitoExtension.class)
class SubmissionEventPublisherServiceTest {

  @Mock private SnsClient snsClient;

  @InjectMocks
  private SubmissionEventPublisherService
      submissionEventPublisherService; // replace with your actual class name

  @BeforeEach
  void setUp() {
    submissionEventPublisherService =
        new SubmissionEventPublisherService(snsClient, new ObjectMapper());

    ReflectionTestUtils.setField(
        submissionEventPublisherService,
        "topicArn",
        "arn:aws:sns:us-east-1:000000000000:claims-events");
  }

  @Test
  void publish_BulkSubmissionEvent_sendsMessageWithCorrectPayload() {
    // given an Sns topic
    UUID bulkSubmissionId = Uuid7.timeBasedUuid();
    UUID submissionId1 = Uuid7.timeBasedUuid();
    UUID submissionId2 = Uuid7.timeBasedUuid();

    String topicArn = "arn:aws:sns:us-east-1:000000000000:claims-events";
    // when publish is called with some IDs
    submissionEventPublisherService.publishBulkSubmissionEvent(
        bulkSubmissionId, List.of(submissionId1, submissionId2));

    // then the correct message is published to the topic
    // Capture the actual Publish Request
    var captor = forClass(PublishRequest.class);
    verify(snsClient).publish(captor.capture());

    PublishRequest publishRequest = captor.getValue();

    assertThat(publishRequest.topicArn()).isEqualTo(topicArn);
    assertThat(publishRequest.message())
        .contains(bulkSubmissionId.toString())
        .contains(submissionId1.toString())
        .contains(submissionId2.toString());

    MessageAttributeValue expectedAttributeValue =
        MessageAttributeValue.builder()
            .dataType("String")
            .stringValue(SubmissionEventType.PARSE_BULK_SUBMISSION.toString())
            .build();
    assertThat(publishRequest.messageAttributes())
        .containsEntry("SubmissionEventType", expectedAttributeValue);

    verifyNoMoreInteractions(snsClient);
  }

  @Test
  void publish_ValidateSubmissionEvent_sendsMessageWithCorrectPayload() {
    // given an Sns queue
    UUID submissionId = Uuid7.timeBasedUuid();

    String topicArn = "arn:aws:sns:us-east-1:000000000000:claims-events";

    // when publish is called with some IDs
    submissionEventPublisherService.publishSubmissionValidationEvent(submissionId);

    // then the correct message is published to the topic
    // Capture the actual Publish Request
    var captor = forClass(PublishRequest.class);
    verify(snsClient).publish(captor.capture());

    PublishRequest publishRequest = captor.getValue();

    assertThat(publishRequest.topicArn()).isEqualTo(topicArn);
    assertThat(publishRequest.message()).contains(submissionId.toString());

    MessageAttributeValue expectedAttributeValue =
        MessageAttributeValue.builder()
            .dataType("String")
            .stringValue(SubmissionEventType.VALIDATE_SUBMISSION.toString())
            .build();
    assertThat(publishRequest.messageAttributes())
        .containsEntry("SubmissionEventType", expectedAttributeValue);

    verifyNoMoreInteractions(snsClient);
  }

  @Test
  void publish_ValidationSucceededEvent_sendsMessageWithCorrectPayload() {
    // given an Sns queue
    UUID submissionId = Uuid7.timeBasedUuid();

    String topicArn = "arn:aws:sns:us-east-1:000000000000:claims-events";

    // when publish is called with some IDs
    submissionEventPublisherService.publishSubmissionValidationSucceededEvent(submissionId);

    // then the correct message is published to the topic
    // Capture the actual Publish Request
    var captor = forClass(PublishRequest.class);
    verify(snsClient).publish(captor.capture());

    PublishRequest publishRequest = captor.getValue();

    assertThat(publishRequest.topicArn()).isEqualTo(topicArn);
    assertThat(publishRequest.message()).contains(submissionId.toString());

    MessageAttributeValue expectedAttributeValue =
        MessageAttributeValue.builder()
            .dataType("String")
            .stringValue(SubmissionEventType.SUBMISSION_VALIDATION_SUCCEEDED.toString())
            .build();
    assertThat(publishRequest.messageAttributes())
        .containsEntry("SubmissionEventType", expectedAttributeValue);

    verifyNoMoreInteractions(snsClient);
  }
}
