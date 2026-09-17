package uk.gov.justice.laa.dstew.payments.claimsdata.service.audit;

import org.jspecify.annotations.NullMarked;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.AbstractClientHttpRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Consumer;

/**
 * A {@code ClientHttpRequest} that sends nothing and hands every written buffer to the consumer.
 *
 * <p>Used by {@link AuditingClientHttpConnector} to render the outbound body before the real
 * connection is attempted, so the request payload is recorded even when the connection fails.
 */
@NullMarked
public class RecordingClientHttpRequest extends AbstractClientHttpRequest {
    private final HttpMethod method;
    private final URI uri;
    private final Consumer<byte[]> sink;

    RecordingClientHttpRequest(HttpMethod method, URI uri, Consumer<byte[]> sink) {
        this.method = method;
        this.uri = uri;
        this.sink = sink;
    }

    @Override
    public HttpMethod getMethod() {
        return method;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public DataBufferFactory bufferFactory() {
        return DefaultDataBufferFactory.sharedInstance;
    }

    @Override
    public <T> T getNativeRequest() {
        throw new UnsupportedOperationException("Recording request as no native request");
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        return doCommit(() ->
                Flux.from(body)
                        .doOnNext(buffer -> {
                            try {
                                byte[] bytes = new byte[buffer.readableByteCount()];
                                buffer.read(bytes);
                                sink.accept(bytes);
                            } finally {
                                DataBufferUtils.release(buffer);
                            }
                        })
                        .then());
    }

    @Override
    public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
        return writeWith(Flux.from(body).flatMap(Flux::from));
    }

    @Override
    public Mono<Void> setComplete() {
        return doCommit();
    }

    @Override
    public void applyHeaders() {}

    @Override
    public void applyCookies() {}

    @Override
    public void applyAttributes() {}
}
