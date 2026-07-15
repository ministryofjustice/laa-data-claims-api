package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;

/** Configuration properties specific to the Fee Scheme Platform API. */
@ConfigurationProperties(prefix = "laa.fee-scheme-platform-api")
public class FeeSchemePlatformApiProperties extends ApiProperties {

  public FeeSchemePlatformApiProperties(String url, String accessToken) {
    super(url, accessToken, HttpHeaders.AUTHORIZATION);
  }
}
