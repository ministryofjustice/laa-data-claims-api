package uk.gov.justice.laa.dstew.payments.claimsdata.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Flux;

@DisplayName("RecordingClientHttpRequest unit tests")
public class RecordingClientHttpRequestTest {
  private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

  @Test
  @DisplayName("Hands every written buffer to the sink, in order")
  void capturesWrittenBuffers() {
    RecordingClientHttpRequest request =
        new RecordingClientHttpRequest(
            HttpMethod.POST, URI.create("http://x/y"), captured::writeBytes);
    DefaultDataBufferFactory factory = DefaultDataBufferFactory.sharedInstance;

    request
        .writeWith(
            Flux.just(
                factory.wrap("{\"a\":".getBytes(StandardCharsets.UTF_8)),
                factory.wrap("1}".getBytes(StandardCharsets.UTF_8))))
        .block();

    assertThat(captured.toString(StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
  }

  @Test
  @DisplayName("Completes without body")
  void completesWithoutBody() {
    RecordingClientHttpRequest request =
        new RecordingClientHttpRequest(
            HttpMethod.GET, URI.create("http://x/y"), captured::writeBytes);

    request.setComplete().block();

    assertThat(captured.size()).isZero();
    assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
    assertThat(request.getURI()).isEqualTo(URI.create("http://x/y"));
  }
}
