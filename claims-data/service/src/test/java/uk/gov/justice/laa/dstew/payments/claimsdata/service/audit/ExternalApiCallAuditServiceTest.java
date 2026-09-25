package uk.gov.justice.laa.dstew.payments.claimsdata.service.audit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalApiCallLogEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalApiCallLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalSystemType;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalApiCallAuditService unit tests")
class ExternalApiCallAuditServiceTest {
  @Mock private ExternalApiCallLogRepository repository;

  private ExternalApiCallAuditService service;

  @BeforeEach
  void setUp() {
    service = new ExternalApiCallAuditService(repository, new ObjectMapper());
  }

  @Test
  @DisplayName("Passes valid JSON payloads through untouched")
  void passValidJsonThrough() {
    String request = "{\"feeCode\":\"CAPA\", \"boltOns\": {\"a\": \"b\"}}";
    String response = "{\"feeCalculation\":{\"totalAmount\":120.60}}";

    service.record(entry(request, response, 200));

    ExternalApiCallLogEntry written = captureWrittenEntry();
    assertThat(written.requestPayload()).isEqualTo(request);
    assertThat(written.responsePayload()).isEqualTo(response);
    assertThat(written.httpStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("Wraps a non-JSON request payload in a JSON object")
  void wrapsNonJsonRequestPayload() {
    String html = "<html><body>502 Bad Gateway</body></html>";

    service.record(entry("{}", html, 502));

    ExternalApiCallLogEntry written = captureWrittenEntry();
    assertThat(written.requestPayload()).isEqualTo("{}");
    assertThat(written.responsePayload())
        .isEqualTo("{\"raw\":\"<html><body>502 Bad Gateway</body></html>\"}");
    assertThat(written.httpStatus()).isEqualTo(502);
  }

  @Test
  @DisplayName("Stores a blank response payload as null")
  void storesBlankResponseAsNull() {
    service.record(entry("{}", "   ", null));

    ExternalApiCallLogEntry written = captureWrittenEntry();
    assertThat(written.responsePayload()).isNull();
    assertThat(written.httpStatus()).isNull();
  }

  @Test
  @DisplayName("Leaves a null response as null")
  void leavesNullResponseAsNull() {
    service.record(entry("{}", null, null));

    ExternalApiCallLogEntry written = captureWrittenEntry();
    assertThat(written.responsePayload()).isNull();
  }

  @Test
  @DisplayName("Swallows a repository failure and returns normally")
  void swallowsRepositoryFailure() {
    doThrow(new DataAccessResourceFailureException("Database down")).when(repository).insert(any());

    assertThatCode(() -> service.record(entry("{}", "{}", 200))).doesNotThrowAnyException();
  }

  private static ExternalApiCallLogEntry entry(String request, String response, Integer status) {
    return new ExternalApiCallLogEntry(
        UUID.randomUUID(),
        ExternalSystemType.FEE_SCHEME_PLATFORM,
        "/api/v1/fee-calculation",
        "POST",
        request,
        response,
        status,
        null,
        null,
        Instant.now());
  }

  private ExternalApiCallLogEntry captureWrittenEntry() {
    ArgumentCaptor<ExternalApiCallLogEntry> captor =
        ArgumentCaptor.forClass(ExternalApiCallLogEntry.class);
    verify(repository).insert(captor.capture());
    return captor.getValue();
  }
}
