package uk.gov.justice.laa.dstew.payments.claimsdata.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.client.reactive.ClientHttpResponseDecorator;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalApiCallLogEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit.ExternalSystemType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * {@link ClientHttpConnector} decorator that records outbound external API calls (DSTEW-2001)
 *
 * <p>Sits below the {@code WebClient}, so it sees the actual bytes written and read, not the mapped
 * model objects. It only sees calls that make it to the wire. Cache hits cannot get here, retries
 * do.
 *
 * <p>The claim and submission ids come from the supplied {@code ids} resolver, read once at connect
 * time on the calling thread and carried in the per call capture, so the response side may complete
 * on any thread. A retry that re-subscribes on another scheduler would resolve no ids. There are
 * nmo retries on this API.
 *
 * <p>One row is recorded per call. On body completion with a status and a body, or on error before
 * completion with a null response and status. Recording never throws and never alters the call
 */
@Slf4j
@NullMarked
public class AuditingClientHttpConnector implements ClientHttpConnector {
  private final ClientHttpConnector delegate;
  private final ExternalSystemType systemType;
  private final ExternalApiCallAuditService auditService;
  private final ObjectMapper objectMapper;
  private final Supplier<ExternalApiCallContext.Ids> ids;

  /** Public constructor for the {@code AuditingClientHttpConnector}. */
  public AuditingClientHttpConnector(
      ClientHttpConnector delegate,
      ExternalSystemType systemType,
      ExternalApiCallAuditService auditService,
      ObjectMapper objectMapper,
      Supplier<ExternalApiCallContext.Ids> ids) {
    this.delegate = delegate;
    this.systemType = systemType;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.ids = ids;
  }

  @Override
  public Mono<ClientHttpResponse> connect(
      HttpMethod method, URI uri, Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {
    return Mono.defer(
        () -> {
          CallCapture capture = new CallCapture(method, uri, ids.get());
          RecordingClientHttpRequest recorder =
              new RecordingClientHttpRequest(method, uri, capture::appendRequest);

          return requestCallback
              .apply(recorder)
              .then(delegate.connect(method, uri, requestCallback))
              .map(capture::wrap)
              .doOnError(e -> capture.recordNoResponse());
        });
  }

  private final class CallCapture {
    private final HttpMethod method;
    private final URI uri;
    private final ExternalApiCallContext.Ids callIds;
    private final Instant startedAt = Instant.now();
    private final ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
    private final AtomicBoolean recorded = new AtomicBoolean();

    private CallCapture(HttpMethod method, URI uri, ExternalApiCallContext.Ids callIds) {
      this.method = method;
      this.uri = uri;
      this.callIds = callIds;
    }

    /** Receives the request body as rendered by the recording request, before any connection. */
    void appendRequest(byte[] bytes) {
      requestBytes.writeBytes(bytes);
    }

    ClientHttpResponse wrap(ClientHttpResponse response) {
      return new ClientHttpResponseDecorator(response) {
        @Override
        public Flux<DataBuffer> getBody() {
          return super.getBody()
              .doOnNext(b -> copy(b, responseBytes))
              .doOnComplete(() -> recordResponse(response.getStatusCode().value()))
              .doOnError(e -> recordNoResponse());
        }
      };
    }

    void recordResponse(int status) {
      record(responseBytes.toString(StandardCharsets.UTF_8), status);
    }

    void recordNoResponse() {
      record(null, null);
    }

    private void record(@Nullable String responsePayload, @Nullable Integer status) {
      if (!recorded.compareAndSet(false, true)) {
        return;
      }
      try {
        auditService.record(
            new ExternalApiCallLogEntry(
                Uuid7.timeBasedUuid(),
                systemType,
                endpoint(),
                method.name(),
                requestPayload(),
                responsePayload,
                status,
                callIds.submissionId(),
                callIds.claimId(),
                startedAt));
      } catch (RuntimeException e) {
        log.error("External API audit capture failed for {} {}", method, uri, e);
      }
    }

    private String endpoint() {
      String query = uri.getRawQuery();
      return query == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
    }

    private String requestPayload() {
      if (requestBytes.size() > 0) {
        return requestBytes.toString(StandardCharsets.UTF_8);
      }
      ObjectNode node = objectMapper.createObjectNode();
      node.put("path", uri.getRawPath());
      ObjectNode query = node.putObject("query");
      UriComponentsBuilder.fromUri(uri)
          .build()
          .getQueryParams()
          .forEach((k, v) -> query.put(k, v.size() == 1 ? v.getFirst() : String.join(",", v)));
      return node.toString();
    }

    private static void copy(DataBuffer buffer, ByteArrayOutputStream target) {
      try (var buffers = buffer.readableByteBuffers()) {
        buffers.forEachRemaining(
            bb -> {
              byte[] bytes = new byte[bb.remaining()];
              bb.get(bytes);
              target.writeBytes(bytes);
            });
      }
    }
  }
}
