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

  private static final DockerImageName LOCALSTACK_IMAGE =
      DockerImageName.parse("localstack/localstack:4.14.0");

  @Bean
  public static LocalStackContainer localStack() {
    LocalStackContainer localStack =
        new LocalStackContainer(LOCALSTACK_IMAGE).withServices(SQS, SNS).withStartupAttempts(3);

    localStack.start(); // start it before the SqsClient bean is created
    return localStack;
  }

  @Bean
  public SqsClient sqsClient(@Autowired LocalStackContainer localStack) {
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

    return SnsClient.builder()
        .endpointOverride(localStack.getEndpointOverride(SNS))
        .region(Region.of(localStack.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
        .build();
  }
}
