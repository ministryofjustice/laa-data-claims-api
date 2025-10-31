package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class RateLimitUtils {

  public static ResponseEntity<?> get429Response() {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many requests");
  }
}
