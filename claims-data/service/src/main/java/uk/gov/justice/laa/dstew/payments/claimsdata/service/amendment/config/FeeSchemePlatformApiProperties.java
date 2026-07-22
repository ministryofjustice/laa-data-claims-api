package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ApiProperties;

/** Configuration properties specific to the Fee Scheme Platform API. */
@ConfigurationProperties(prefix = "laa.claims.api.amendments.fee-scheme-platform-api")
public class FeeSchemePlatformApiProperties extends ApiProperties {

  public FeeSchemePlatformApiProperties(String url, String accessToken, int readTimeoutMs) {
    super(url, accessToken, HttpHeaders.AUTHORIZATION, readTimeoutMs);
  }
}
