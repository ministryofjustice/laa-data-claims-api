package uk.gov.justice.laa.dstew.payments.claimsdata.controller.versioning;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller for testing Spring Boot 4 API versioning. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class VersionProbeController {

  @GetMapping(path = "/{version}/_version-proof", version = "1")
  public ResponseEntity<Map<String, Object>> probeV1() {
    return ResponseEntity.ok(Map.of("proof", "v1 handler was selected", "version", 1));
  }

  @GetMapping(path = "/{version}/_version-proof", version = "2")
  public ResponseEntity<Map<String, Object>> probeV2() {
    return ResponseEntity.ok(Map.of("proof", "v2 handler was selected", "version", 2));
  }
}
