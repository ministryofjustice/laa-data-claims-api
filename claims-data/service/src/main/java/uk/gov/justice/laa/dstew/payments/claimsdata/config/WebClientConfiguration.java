package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.netty.http.client.HttpClient;
import uk.gov.justice.laa.dstew.payments.claimsdata.client.FeeSchemePlatformRestClient;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalSystemType;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.config.FeeSchemePlatformApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.audit.AuditingClientHttpConnector;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.audit.ExternalApiCallAuditService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.audit.ExternalApiCallContext;

/**
 * Configuration class for creating and configuring WebClient instances.
 *
 * <p>Uses {@link HttpServiceProxyFactory} to build strongly-typed HTTP clients.
 *
 * @author Andrew Johnys
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
      final FeeSchemePlatformApiProperties properties,
      final ExternalApiCallAuditService auditService,
      final ObjectMapper objectMapper) {
    final ClientHttpConnector connector =
        new AuditingClientHttpConnector(
            reactorConnector(properties),
            ExternalSystemType.FEE_SCHEME_PLATFORM,
            auditService,
            objectMapper,
            new ExternalApiCallContext());
    final WebClient webClient = createWebClient(properties, connector);
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
  public static WebClient createWebClient(
      final ApiProperties apiProperties, final ClientHttpConnector connector) {

    return WebClient.builder()
        .baseUrl(apiProperties.getUrl())
        .defaultHeader(apiProperties.getAuthHeader(), apiProperties.getAccessToken())
        .clientConnector(connector)
        .build();
  }

  /**
   * Creates a ReactorClientHttpConnector with configured connector and read timeouts.
   *
   * @param apiProperties timeouts
   * @return the reactor connector
   */
  public static ClientHttpConnector reactorConnector(final ApiProperties apiProperties) {
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, apiProperties.getConnectTimeoutMs())
            .responseTimeout(Duration.ofMillis(apiProperties.getReadTimeoutMs()));

    return new ReactorClientHttpConnector(httpClient);
  }
}
