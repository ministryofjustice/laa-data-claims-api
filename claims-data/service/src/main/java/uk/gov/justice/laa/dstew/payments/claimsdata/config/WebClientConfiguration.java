package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import uk.gov.justice.laa.dstew.payments.claimsdata.client.FeeSchemePlatformRestClient;

/**
 * Configuration class for creating and configuring WebClient instances.
 *
 * <p>Uses {@link HttpServiceProxyFactory} to build strongly-typed HTTP clients.
 *
 * @author Jamie Briggs
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({FeeSchemePlatformApiProperties.class})
public class WebClientConfiguration {

  /**
   * Creates a {@link FeeSchemePlatformRestClient} bean to communicate with the Fee Scheme Platform
   * API using a WebClient instance.
   *
   * @param properties The configuration properties required to initialize the WebClient, including
   *     the base URL and access token for the Fee Scheme Platform API.
   * @return An instance of {@link FeeSchemePlatformRestClient} for interacting with the Fee Scheme
   *     Platform API.
   */
  @Bean
  public FeeSchemePlatformRestClient feeSchemePlatformRestClient(
      final FeeSchemePlatformApiProperties properties) {
    final WebClient webClient = createWebClient(properties);
    final WebClientAdapter webClientAdapter = WebClientAdapter.create(webClient);
    HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(webClientAdapter).build();

    return factory.createClient(FeeSchemePlatformRestClient.class);
  }

  /**
   * Creates a WebClient instance using the provided configuration properties.
   *
   * @param apiProperties The configuration properties for the API.
   * @return A WebClient instance.
   */
  public static WebClient createWebClient(final ApiProperties apiProperties) {
    final ExchangeStrategies strategies =
        ExchangeStrategies.builder()
            .codecs(
                configurer ->
                    configurer
                        .defaultCodecs()
                        .maxInMemorySize(
                            50 * 1024 * 1024) // 50 MB to cope with large bulk upload responses
                )
            .build();

    return WebClient.builder()
        .baseUrl(apiProperties.getUrl())
        .defaultHeader(apiProperties.getAuthHeader(), apiProperties.getAccessToken())
        .exchangeStrategies(strategies)
        .build();
  }
}
