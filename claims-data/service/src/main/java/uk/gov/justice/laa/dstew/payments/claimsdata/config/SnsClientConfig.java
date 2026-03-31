package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

/** Configuration for creating an SNS client used to publish events. */
@Configuration
public class SnsClientConfig {

  /**
   * Create and configure an SNS client. The AWS_ENDPOINT environment variable may be set to
   * override the service endpoint (for example, a localstack URL).
   */
  @Bean
  public SnsClient snsClient() {

    String endpoint = System.getenv("AWS_ENDPOINT"); // e.g. http://localhost:4566

    var builder =
        SnsClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(EnvironmentVariableCredentialsProvider.create());

    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint));
    }

    return builder.build();
  }
}
