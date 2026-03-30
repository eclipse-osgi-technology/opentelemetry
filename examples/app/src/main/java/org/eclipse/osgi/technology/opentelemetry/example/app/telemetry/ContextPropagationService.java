package org.eclipse.osgi.technology.opentelemetry.example.app.telemetry;

import java.util.HashMap;
import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

/**
 * Demonstrates W3C TraceContext propagation between a simulated producer and
 * consumer using the OpenTelemetry Context API.
 *
 * <p>
 * Demonstrates:
 * <ul>
 * <li>Context injection into a carrier (simulating HTTP headers)</li>
 * <li>Context extraction from a carrier</li>
 * <li>Parent-child span linkage across context boundaries</li>
 * </ul>
 */
@Component(service = ContextPropagationService.class, immediate = true)
public class ContextPropagationService {

	private static final String SCOPE = "org.eclipse.osgi.technology.opentelemetry.example.telemetry";

	private Tracer tracer;

	private static final TextMapSetter<Map<String, String>> SETTER = Map::put;

	private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
		@Override
		public Iterable<String> keys(Map<String, String> carrier) {
			return carrier.keySet();
		}

		@Override
		public String get(Map<String, String> carrier, String key) {
			return carrier == null ? null : carrier.get(key);
		}
	};

	private volatile OpenTelemetry openTelemetry;

	@Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
	void bindOpenTelemetry(OpenTelemetry openTelemetry) {
		this.openTelemetry = openTelemetry;
		this.tracer = openTelemetry.getTracer(SCOPE, "0.1.0");
	}

	void unbindOpenTelemetry(OpenTelemetry openTelemetry) {
		this.tracer = null;
		this.openTelemetry = null;
	}

	public void demonstratePropagation() {
		Tracer t = tracer;
		OpenTelemetry otel = openTelemetry;
		if (t == null || otel == null)
			return;

		// Producer side: create span and inject context
		Span producerSpan = t.spanBuilder("example.producer").setSpanKind(SpanKind.CLIENT).startSpan();

		Map<String, String> carrier = new HashMap<>();
		try (Scope scope = producerSpan.makeCurrent()) {
			producerSpan.addEvent("Injecting context");
			otel.getPropagators().getTextMapPropagator().inject(Context.current(), carrier, SETTER);
		} finally {
			producerSpan.end();
		}

		// Consumer side: extract context and create child span
		Context extractedContext = otel.getPropagators().getTextMapPropagator().extract(Context.current(), carrier,
				GETTER);

		Span consumerSpan = t.spanBuilder("example.consumer").setSpanKind(SpanKind.SERVER).setParent(extractedContext)
				.startSpan();
		try (Scope scope = consumerSpan.makeCurrent()) {
			consumerSpan.addEvent("Processing received message");
			simulateWork(30);
			consumerSpan.addEvent("Message processed");
		} finally {
			consumerSpan.end();
		}
	}

	private void simulateWork(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
