package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.hooks;

import io.cucumber.java.Before;
import java.util.Map;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.MatterStartRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;

public class BddHooks {

  @Autowired private BddScenarioContext context;
  @Autowired private ValidationMessageLogRepository validationMessageLogRepository;
  @Autowired private AssessmentRepository assessmentRepository;
  @Autowired private CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  @Autowired private ClaimCaseRepository claimCaseRepository;
  @Autowired private ClientRepository clientRepository;
  @Autowired private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Autowired private MatterStartRepository matterStartRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private BulkSubmissionRepository bulkSubmissionRepository;
  @Autowired private SqsClient sqsClient;
  @Autowired private SnsClient snsClient;

  @Value("${aws.sqs.queue-name}")
  private String queueName;

  @Value("${aws.sns.topic-arn}")
  private String topicArn;

  @Before(order = 0)
  public void resetScenarioContextAndData() {
    context.clear();

    validationMessageLogRepository.deleteAll();
    assessmentRepository.deleteAll();
    calculatedFeeDetailRepository.deleteAll();
    claimCaseRepository.deleteAll();
    clientRepository.deleteAll();
    claimSummaryFeeRepository.deleteAll();
    matterStartRepository.deleteAll();
    claimRepository.deleteAll();
    submissionRepository.deleteAll();
    bulkSubmissionRepository.deleteAll();
  }

  @Before(order = 1)
  public void ensureQueueSubscription() {
    sqsClient.createQueue(builder -> builder.queueName(queueName));

    GetQueueUrlResponse queueUrlResponse =
        sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());

    GetQueueAttributesResponse queueAttributes =
        sqsClient.getQueueAttributes(
            GetQueueAttributesRequest.builder()
                .queueUrl(queueUrlResponse.queueUrl())
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build());

    String queueArn = queueAttributes.attributes().get(QueueAttributeName.QUEUE_ARN);

    snsClient.createTopic(CreateTopicRequest.builder().name("claims-events").build());
    snsClient.subscribe(
        SubscribeRequest.builder()
            .topicArn(topicArn)
            .protocol("sqs")
            .endpoint(queueArn)
            .attributes(Map.of("RawMessageDelivery", "true"))
            .build());
  }
}

