package org.eclipse.osgi.technology.opentelemetry.sender.http.impl.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.osgi.technology.opentelemetry.sender.http.api.Constants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.annotation.Property;
import org.osgi.test.common.annotation.config.WithConfiguration;
import org.osgi.test.common.service.ServiceAware;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

public class HttpSenderIntegrationTest {

    static final int PORT = 18_319;
    static HttpServer server;
    static final CopyOnWriteArrayList<String> receivedPaths = new CopyOnWriteArrayList<>();
    static final CopyOnWriteArrayList<String> receivedContentTypes = new CopyOnWriteArrayList<>();
    static volatile CountDownLatch traceLatch;

    @BeforeAll
    static void startMockServer() throws IOException {
    //    traceLatch = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", (HttpExchange exchange) -> {
            String path = exchange.getRequestURI().getPath();
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            receivedPaths.add(path);
            if (contentType != null) {
                receivedContentTypes.add(contentType);
            }
            if (path.contains("/v1/traces")) {
                traceLatch.countDown();
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopMockServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void noServiceWithoutConfiguration(
            @InjectService(cardinality = 0) ServiceAware<OpenTelemetry> otelAware) {
        assertThat(otelAware.size()).isZero();
    }

    @Test
    @WithConfiguration(
        pid = Constants.PID,
        properties = {
            @Property(key = "serviceName", value = "test-http-sender"),
            @Property(key = "endpoint", value = "http://localhost:18319")
        }
    )
    void serviceIsRegisteredAfterConfiguration(
            @InjectService(timeout = 3000) OpenTelemetry openTelemetry) {
        assertThat(openTelemetry).isNotNull();
    }

    @Test
    @WithConfiguration(
        pid = Constants.PID,
        properties = {
            @Property(key = "serviceName", value = "test-http-traces"),
            @Property(key = "endpoint", value = "http://localhost:18319")
        }
    )
    void sendsTracesToEndpoint(
            @InjectService(timeout = 3000) OpenTelemetry openTelemetry) throws Exception {
        receivedPaths.clear();
        traceLatch = new CountDownLatch(1);

        Tracer tracer = openTelemetry.getTracer("http-integration-test");
        Span span = tracer.spanBuilder("test-span").startSpan();
        span.end();

        boolean received = traceLatch.await(15, TimeUnit.SECONDS);
        assertThat(received)
                .as("Expected trace data at /v1/traces within 15s")
                .isTrue();
        assertThat(receivedPaths).anyMatch(p -> p.contains("/v1/traces"));
    }

    @Test
    @WithConfiguration(
        pid = Constants.PID,
        properties = {
            @Property(key = "serviceName", value = "test-http-content-type"),
            @Property(key = "endpoint", value = "http://localhost:18319")
        }
    )
    void sendsProtobufContentType(
            @InjectService(timeout = 3000) OpenTelemetry openTelemetry) throws Exception {
        receivedContentTypes.clear();
        traceLatch = new CountDownLatch(1);

        Tracer tracer = openTelemetry.getTracer("http-content-type-test");
        Span span = tracer.spanBuilder("content-type-span").startSpan();
        span.end();

        boolean received = traceLatch.await(15, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(receivedContentTypes).anyMatch(ct -> ct.contains("protobuf"));
    }

    @Test
    void pidConstantIsCorrect() {
        assertThat(Constants.PID)
                .isEqualTo("org.eclipse.osgi.technology.opentelemetry.sender.http");
    }
}
