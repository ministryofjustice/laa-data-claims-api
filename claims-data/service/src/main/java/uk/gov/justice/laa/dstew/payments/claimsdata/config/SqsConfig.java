package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Configuration class for creating and configuring an Amazon SQS client.
 * This class is annotated with @Configuration, indicating that it is a source
 * for bean definitions. It provides a Spring-managed bean for the SQS client.
 * The client is configured for the configured AWS region.
 */
@Configuration
@Profile("!test")
public class SqsConfig {

  /**
   * Configures and provides a Spring-managed {@link SqsClient} bean for interacting with Amazon SQS.
   * The client is configured using the specified AWS region
   *
   * @param region the AWS region to configure the SQS client for (e.g., "us-east-1").
   * @return a configured {@link SqsClient} instance.
   */
  @Bean
  public SqsClient sqsClient(
      @Value("${aws.region}") String region
  ) {
    return SqsClient.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.builder().build())
        .build();
  }
}