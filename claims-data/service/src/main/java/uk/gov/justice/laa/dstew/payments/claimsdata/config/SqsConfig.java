package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Configuration class for creating and configuring an Amazon SQS client.
 * This class is annotated with @Configuration, indicating that it is a source
 * for bean definitions. It provides a Spring-managed bean for the SQS client.
 * The client is configured for the configured AWS region and optionally an
 * SQS endpoint if specified.
 */
@Configuration
public class SqsConfig {

  @Bean
  public SqsClient sqsClient(
      @Value("${aws.region}") String region,
      @Value("${aws.sqs.endpoint:}") String endpoint) {
    var builder = SqsClient.builder()
        .region(Region.of(region));

    if (endpoint != null && !endpoint.isBlank()) {
      // LocalStack: override endpoint and use dummy credentials
      builder.endpointOverride(URI.create(endpoint))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create("test", "test")
              )
          );
    } else {
      // AWS: no endpoint override, use default credentials provider chain
      builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
    }

    return builder.build();
  }
}