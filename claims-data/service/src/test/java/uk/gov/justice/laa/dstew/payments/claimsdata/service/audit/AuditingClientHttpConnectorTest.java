package uk.gov.justice.laa.dstew.payments.claimsdata.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalApiCallLogEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalSystemType;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditingClientHttpConnector unit tests")
public class AuditingClientHttpConnectorTest {
  private static final String FEE_CALCULATION = "http://fsp/api/v1/fee-calculation";

  @Mock private ExternalApiCallAuditService auditService;
  @Captor private ArgumentCaptor<ExternalApiCallLogEntry> captor;

  @Test
  @DisplayName("Records a POST with verbatim request and response, status and ids")
  void recordsPost() {
    UUID claimId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    String requestJson = "{\"feeCode\": \"CAPA\", \"netProfitCosts\":100.5}";
    String responseJson = "{\"feeCalculation\":{\"totalAmount\":1}}";

    webClient(
            respondingWith(200, responseJson),
            new ExternalApiCallContext.Ids(claimId, submissionId))
        .post()
        .uri(FEE_CALCULATION)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestJson)
        .retrieve()
        .bodyToMono(String.class)
        .block();

    ExternalApiCallLogEntry e = written();
    assertThat(e.systemType()).isEqualTo(ExternalSystemType.FEE_SCHEME_PLATFORM);
    assertThat(e.endpoint()).isEqualTo("/api/v1/fee-calculation");
    assertThat(e.httpMethod()).isEqualTo("POST");
    assertThat(e.requestPayload()).isEqualTo(requestJson);
    assertThat(e.responsePayload()).isEqualTo(responseJson);
    assertThat(e.httpStatus()).isEqualTo(200);
    assertThat(e.claimId()).isEqualTo(claimId);
    assertThat(e.submissionId()).isEqualTo(submissionId);
    assertThat(e.id()).isNotNull();
    assertThat(e.createdOn()).isNotNull();
  }

  @Test
  @DisplayName("Records an error response body and status (AC2)")
  void recordsErrorResponse() {
    String errorJson = "{\"detail\":\"Bad fee code\"}";

    assertThatThrownBy(
            () ->
                webClient(respondingWith(400, errorJson), ExternalApiCallContext.Ids.NONE)
                    .post()
                    .uri(FEE_CALCULATION)
                    .bodyValue("{}")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block())
        .isInstanceOf(WebClientResponseException.BadRequest.class);

    ExternalApiCallLogEntry e = written();
    assertThat(e.httpStatus()).isEqualTo(400);
    assertThat(e.responsePayload()).isEqualTo(errorJson);
    assertThat(e.requestPayload()).isEqualTo("{}");
  }

  @Test
  @DisplayName("Records a GET with path and query as the request payload")
  void recordsGetAsPathAndQuery() {
    webClient(respondingWith(200, "{}"), ExternalApiCallContext.Ids.NONE)
        .get()
        .uri("http://pda/api/v1/provider-offices/0A123B/schedules?effectiveDate=2026-09-01")
        .retrieve()
        .bodyToMono(String.class)
        .block();

    ExternalApiCallLogEntry e = written();
    assertThat(e.httpMethod()).isEqualTo("GET");
    assertThat(e.endpoint())
        .isEqualTo("/api/v1/provider-offices/0A123B/schedules?effectiveDate=2026-09-01");
    assertThat(e.requestPayload())
        .isEqualTo(
            "{\"path\":\"/api/v1/provider-offices/0A123B/schedules\","
                + "\"query\":{\"effectiveDate\":\"2026-09-01\"}}");
  }

  @Test
  @DisplayName("Records null response and status with connection fails")
  void recordsNoResponseOnConnectFailure() {
    ClientHttpConnector failing = (_, _, _) -> Mono.error(new ConnectException("refused"));

    assertThatThrownBy(
            () ->
                webClient(failing, ExternalApiCallContext.Ids.NONE)
                    .post()
                    .uri(FEE_CALCULATION)
                    .bodyValue("{\"feeCode\":\"CAPA\"}")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block())
        .isInstanceOf(WebClientRequestException.class);

    ExternalApiCallLogEntry e = written();
    assertThat(e.responsePayload()).isNull();
    assertThat(e.httpStatus()).isNull();
  }

  @Test
  @DisplayName("Records null ids with the resolver has none")
  void recordsNullIdsWithoutContext() {
    webClient(respondingWith(200, "{}"), ExternalApiCallContext.Ids.NONE)
        .get()
        .uri("http://x/y")
        .retrieve()
        .bodyToMono(String.class)
        .block();

    ExternalApiCallLogEntry e = written();
    assertThat(e.claimId()).isNull();
    assertThat(e.submissionId()).isNull();
  }

  @Test
  @DisplayName("Records once per call")
  void recordsOnce() {
    webClient(respondingWith(200, "{}"), ExternalApiCallContext.Ids.NONE)
        .get()
        .uri("http://x/y")
        .retrieve()
        .bodyToMono(String.class)
        .block();

    verify(auditService).record(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);
  }

  @Test
  @DisplayName("Does not record with no subscription")
  void doesNotRecordWithoutSubscription() {
    webClient(respondingWith(200, "{}"), ExternalApiCallContext.Ids.NONE)
        .get()
        .uri("http://x/y")
        .retrieve()
        .bodyToMono(String.class);

    verifyNoInteractions(auditService);
  }

  private WebClient webClient(ClientHttpConnector delegate, ExternalApiCallContext.Ids ids) {
    return WebClient.builder()
        .clientConnector(
            new AuditingClientHttpConnector(
                delegate,
                ExternalSystemType.FEE_SCHEME_PLATFORM,
                auditService,
                new ObjectMapper(),
                () -> ids))
        .build();
  }

  private ExternalApiCallLogEntry written() {
    verify(auditService).record(captor.capture());
    return captor.getValue();
  }

  private static ClientHttpConnector respondingWith(int status, String body) {
    return (method, uri, callback) -> {
      MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
      return callback
          .apply(request)
          .then(
              Mono.fromSupplier(
                  () -> {
                    MockClientHttpResponse response = new MockClientHttpResponse(status);
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    response.setBody(body);
                    return response;
                  }));
    };
  }
}
