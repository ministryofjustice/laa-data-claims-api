package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SNS;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@TestConfiguration
public class AwsTestConfig {

  @Bean
  public static LocalStackContainer localStack() {
    LocalStackContainer localStack =
        new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.9"))
            .withServices(SQS, SNS);

    localStack.start(); // start it before the SqsClient bean is created
    return localStack;
  }

  @Bean
  public SqsClient sqsClient(@Autowired LocalStackContainer localStack) {
    localStack.start(); // ensure container is running

    return SqsClient.builder()
        .endpointOverride(localStack.getEndpointOverride(SQS))
        .region(Region.of(localStack.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
        .build();
  }

  @Bean
  public SnsClient snsClient(@Autowired LocalStackContainer localStack) {
    localStack.start(); // ensure container is running

    return SnsClient.builder()
        .endpointOverride(localStack.getEndpointOverride(SNS))
        .region(Region.of(localStack.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
        .build();
  }
}
