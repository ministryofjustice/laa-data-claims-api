package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import uk.gov.justice.laa.dstew.payments.claimsevent.model.SubmissionEventType;

@ExtendWith(MockitoExtension.class)
class SubmissionEventPublisherServiceTest {

  @Mock private SqsClient sqsClient;

  @InjectMocks
  private SubmissionEventPublisherService
      submissionEventPublisherService; // replace with your actual class name

  @BeforeEach
  void setUp() {
    submissionEventPublisherService =
        new SubmissionEventPublisherService(sqsClient, new ObjectMapper());
  }

  @Test
  void publish_BulkSubmissionEvent_sendsMessageWithCorrectPayload() {
    // given an Sqs queue
    UUID bulkSubmissionId = UUID.randomUUID();
    UUID submissionId1 = UUID.randomUUID();
    UUID submissionId2 = UUID.randomUUID();

    String queueName = "test-queue";
    String queueUrl = "http://localhost:4566/000000000000/" + queueName;

    // Mock getQueueUrl response
    when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
        .thenReturn(GetQueueUrlResponse.builder().queueUrl(queueUrl).build());

    // when publish is called with some IDs
    submissionEventPublisherService.publishBulkSubmissionEvent(
        bulkSubmissionId, List.of(submissionId1, submissionId2));

    // then the correct message is sent to the queue
    // Capture the actual SendMessageRequest
    var captor = forClass(SendMessageRequest.class);
    verify(sqsClient).sendMessage(captor.capture());

    SendMessageRequest sentRequest = captor.getValue();

    assertThat(sentRequest.queueUrl()).isEqualTo(queueUrl);
    assertThat(sentRequest.messageBody())
        .contains(bulkSubmissionId.toString())
        .contains(submissionId1.toString())
        .contains(submissionId2.toString());

    MessageAttributeValue expectedAttributeValue =
        MessageAttributeValue.builder()
            .dataType("String")
            .stringValue(SubmissionEventType.PARSE_BULK_SUBMISSION.toString())
            .build();
    assertThat(sentRequest.messageAttributes())
        .containsEntry("SubmissionEventType", expectedAttributeValue);

    // also verify getQueueUrl was called
    verify(sqsClient).getQueueUrl(any(GetQueueUrlRequest.class));
    verifyNoMoreInteractions(sqsClient);
  }

  @Test
  void publish_ValidateSubmissionEvent_sendsMessageWithCorrectPayload() {
    // given an Sqs queue
    UUID submissionId = UUID.randomUUID();

    String queueName = "test-queue";
    String queueUrl = "http://localhost:4566/000000000000/" + queueName;

    // Mock getQueueUrl response
    when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
        .thenReturn(GetQueueUrlResponse.builder().queueUrl(queueUrl).build());

    // when publish is called with some IDs
    submissionEventPublisherService.publishSubmissionValidationEvent(submissionId);

    // then the correct message is sent to the queue
    // Capture the actual SendMessageRequest
    var captor = forClass(SendMessageRequest.class);
    verify(sqsClient).sendMessage(captor.capture());

    SendMessageRequest sentRequest = captor.getValue();

    assertThat(sentRequest.queueUrl()).isEqualTo(queueUrl);
    assertThat(sentRequest.messageBody()).contains(submissionId.toString());

    MessageAttributeValue expectedAttributeValue =
        MessageAttributeValue.builder()
            .dataType("String")
            .stringValue(SubmissionEventType.VALIDATE_SUBMISSION.toString())
            .build();
    assertThat(sentRequest.messageAttributes())
        .containsEntry("SubmissionEventType", expectedAttributeValue);

    // also verify getQueueUrl was called
    verify(sqsClient).getQueueUrl(any(GetQueueUrlRequest.class));
    verifyNoMoreInteractions(sqsClient);
  }
}
