package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

@DisplayName("RateLimitUtils tests")
class RateLimitUtilsTest {

  @Test
  @DisplayName("get429Response returns 429 and a plain text body")
  void get429Response_returns429() {
    ResponseEntity<String> resp = RateLimitUtils.get429Response();
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(429));
    assertThat(resp.getHeaders().getContentType().toString()).contains("text/plain");
    assertThat(resp.getBody()).containsIgnoringCase("Too many requests");
  }
}
