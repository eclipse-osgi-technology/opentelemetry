package org.eclipse.osgi.technology.opentelemetry.sender.logging.impl.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.osgi.technology.opentelemetry.core.sender.logging.api.Constants;
import org.junit.jupiter.api.Test;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.annotation.Property;
import org.osgi.test.common.annotation.config.WithConfiguration;
import org.osgi.test.common.service.ServiceAware;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

public class LogSenderIntegrationTest {

	@Test
	void noServiceWithoutConfiguration(@InjectService(cardinality = 0) ServiceAware<OpenTelemetry> otelAware) {
		assertThat(otelAware.size()).isZero();
	}

	@Test
	@WithConfiguration(pid = Constants.PID, properties = { @Property(key = "serviceName", value = "test-log-sender") })
	void serviceIsRegisteredAfterConfiguration(@InjectService(timeout = 300) OpenTelemetry openTelemetry) {
		assertThat(openTelemetry).isNotNull();
	}

	@Test
	@WithConfiguration(pid = Constants.PID, properties = { @Property(key = "serviceName", value = "test-log-sender") })
	void canCreateTracerAndSpan(@InjectService(timeout = 300) OpenTelemetry openTelemetry) {
		Tracer tracer = openTelemetry.getTracer("test");
		Span span = tracer.spanBuilder("test-span").startSpan();
		assertThat(span).isNotNull();
		span.end();
	}

	@Test
	@WithConfiguration(pid = Constants.PID, properties = { @Property(key = "serviceName", value = "test-log-sender") })
	void canCreateMeterAndRecord(@InjectService(timeout = 300) OpenTelemetry openTelemetry) {
		var meter = openTelemetry.getMeter("test");
		var counter = meter.counterBuilder("test.counter").build();
		counter.add(1);
	}
}
