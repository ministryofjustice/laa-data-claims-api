package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Utils class for Rate Limiter implementation. */
public final class RateLimitUtils {

  /**
   * Returns a generic 429 Too Many Requests response.
   *
   * @return 429 response
   */
  public static ResponseEntity<String> get429Response() {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .contentType(MediaType.TEXT_PLAIN)
        .body("Too many requests");
  }
}
