package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class BulkSubmissionPublisherServiceTest {

  @Mock
  private SqsClient sqsClient;

  @InjectMocks
  private BulkSubmissionPublisherService bulkSubmissionPublisherService; // replace with your actual class name

  @BeforeEach
  void setUp() {
    bulkSubmissionPublisherService = new BulkSubmissionPublisherService(
        sqsClient,
        new ObjectMapper()
    );
  }

  @Test
  void publish_sendsMessageWithCorrectPayload() {
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
    bulkSubmissionPublisherService.publish(bulkSubmissionId, List.of(submissionId1, submissionId2));

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

    // also verify getQueueUrl was called
    verify(sqsClient).getQueueUrl(any(GetQueueUrlRequest.class));
    verifyNoMoreInteractions(sqsClient);
  }
}