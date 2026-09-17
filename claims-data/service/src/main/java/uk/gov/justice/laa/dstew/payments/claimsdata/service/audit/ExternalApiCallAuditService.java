package uk.gov.justice.laa.dstew.payments.claimsdata.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalApiCallLogEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalApiCallLogRepository;

/** Records outbound external API calls to the {@code audit.external_api_call_log} table. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalApiCallAuditService {
  static final String RAW_WRAPPER_FIELD = "raw";

  private final ExternalApiCallLogRepository repository;
  private final ObjectMapper objectMapper;

  /**
   * Records an external AP call. Never throws. If the request or response payloads are not valid
   * JSON, they will be wrapped in a JSON object with a single field named {@value
   * #RAW_WRAPPER_FIELD}.
   *
   * @param entry the call to record.
   */
  public void record(ExternalApiCallLogEntry entry) {
    try {
      repository.insert(
          new ExternalApiCallLogEntry(
              entry.id(),
              entry.systemType(),
              entry.endpoint(),
              entry.httpMethod(),
              asJsonOrWrapped(entry.requestPayload()),
              blankToNull(asJsonOrWrapped(entry.responsePayload())),
              entry.httpStatus(),
              entry.submissionId(),
              entry.claimId(),
              entry.createdOn()));
    } catch (RuntimeException e) {
      log.error(
          "External API audit write failed: system={} endpoint={} claimId={} submissionId={}",
          entry.systemType(),
          entry.endpoint(),
          entry.claimId(),
          entry.submissionId(),
          e);
    }
  }

  private String asJsonOrWrapped(String body) {
    if (body == null || body.isBlank()) {
      return body;
    }

    try {
      objectMapper.readTree(body);
      return body;
    } catch (JsonProcessingException e) {
      return objectMapper.createObjectNode().put(RAW_WRAPPER_FIELD, body).toString();
    }
  }

  private String blankToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }
}
