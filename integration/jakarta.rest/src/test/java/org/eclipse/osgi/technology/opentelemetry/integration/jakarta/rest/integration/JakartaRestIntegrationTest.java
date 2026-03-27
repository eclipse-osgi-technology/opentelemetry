package org.eclipse.osgi.technology.opentelemetry.integration.jakarta.rest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.annotation.Property;
import org.osgi.test.common.annotation.config.WithConfiguration;

import io.opentelemetry.api.OpenTelemetry;

@WithConfiguration(
    pid = "org.eclipse.osgi.technology.opentelemetry.sender.log",
    properties = {
        @Property(key = "serviceName", value = "test-jakarta.rest")
    }
)
public class JakartaRestIntegrationTest {

    @Test
    void openTelemetryServiceIsAvailable(
            @InjectService(timeout = 3000) OpenTelemetry openTelemetry) {
        assertThat(openTelemetry).isNotNull();
    }

    @Test
    void canCreateTracer(
            @InjectService(timeout = 3000) OpenTelemetry openTelemetry) {
        var tracer = openTelemetry.getTracer("integration-test-jakarta.rest");
        assertThat(tracer).isNotNull();
        var span = tracer.spanBuilder("test").startSpan();
        span.end();
    }

    @Test
    void canCreateMeter(
            @InjectService(timeout = 3000) OpenTelemetry openTelemetry) {
        var meter = openTelemetry.getMeter("integration-test-jakarta.rest");
        assertThat(meter).isNotNull();
        meter.counterBuilder("test.counter").build().add(1);
    }
}
