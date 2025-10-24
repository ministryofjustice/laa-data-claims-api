package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import java.net.URI;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * Configuration class for creating and configuring an Amazon SQS client. This class is annotated
 * with @Configuration, indicating that it is a source for bean definitions. It provides a
 * Spring-managed bean for the SQS client. The client is configured for the configured AWS region.
 */
@Configuration
@Profile("!test")
public class SqsConfig {

  /**
   * Configures and provides a Spring-managed {@link SqsClient} bean for interacting with Amazon
   * SQS. The client is configured using the specified AWS region
   *
   * @param region the AWS region to configure the SQS client for (e.g., "us-east-1").
   * @return a configured {@link SqsClient} instance.
   */
  @Bean
  public SqsClient sqsClient(
      @Value("${aws.region}") String region,
      @Value("${aws.sqs.endpoint:}") Optional<String> endpoint,
      Environment environment) {

    SqsClientBuilder builder = SqsClient.builder().region(Region.of(region));

    if (environment.acceptsProfiles(Profiles.of("default"))) {
      // Local dev: LocalStack endpoint + dummy creds
      builder.credentialsProvider(
          StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
      endpoint.ifPresent(e -> builder.endpointOverride(URI.create(e)));
    } else {
      // Deployed envs (main, preview): real AWS
      builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
    }

    return builder.build();
  }
}
