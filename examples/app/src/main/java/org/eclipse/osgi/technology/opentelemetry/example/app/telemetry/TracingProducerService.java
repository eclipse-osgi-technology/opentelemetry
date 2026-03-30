package org.eclipse.osgi.technology.opentelemetry.example.app.telemetry;

import java.util.logging.Logger;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Active telemetry producer that creates custom trace spans.
 *
 * <p>
 * Uses an <b>optional, dynamic</b> reference to {@link OpenTelemetry} with
 * bind/unbind. When OpenTelemetry is not available, all tracing operations are
 * silently skipped. This allows the component to remain active even when
 * telemetry is disabled or the service is temporarily unavailable.
 */
@Component(service = TracingProducerService.class, immediate = true)
public class TracingProducerService {

	private static final Logger LOG = Logger.getLogger(TracingProducerService.class.getName());
	private static final String SCOPE = "org.eclipse.osgi.technology.opentelemetry.example.telemetry";

	private volatile Tracer tracer;

	@Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
	void bindOpenTelemetry(OpenTelemetry openTelemetry) {
		LOG.info("OpenTelemetry service bound — tracer created");
		tracer = openTelemetry.getTracer(SCOPE, "0.1.0");
	}

	void unbindOpenTelemetry(OpenTelemetry openTelemetry) {
		LOG.info("OpenTelemetry service unbound — tracer cleared");
		tracer = null;
	}

	public void executeWithTracing(String operationName, Runnable work) {
		Tracer t = tracer;
		if (t == null) {
			work.run();
			return;
		}
		Span span = t.spanBuilder(operationName).setSpanKind(SpanKind.INTERNAL)
				.setAttribute("example.operation", operationName).startSpan();
		try (Scope scope = span.makeCurrent()) {
			span.addEvent("Processing started");
			work.run();
			span.addEvent("Processing completed");
		} catch (Exception e) {
			span.setStatus(StatusCode.ERROR, e.getMessage());
			span.recordException(e);
			throw e;
		} finally {
			span.end();
		}
	}

	public void executeNestedOperation() {
		Tracer t = tracer;
		if (t == null)
			return;

		Span parentSpan = t.spanBuilder("example.parent-operation").setSpanKind(SpanKind.SERVER).startSpan();
		try (Scope parentScope = parentSpan.makeCurrent()) {
			parentSpan.addEvent("Parent started");

			Span childSpan = t.spanBuilder("example.child-operation").setSpanKind(SpanKind.CLIENT)
					.setAttribute("example.child.step", "lookup").startSpan();
			try (Scope childScope = childSpan.makeCurrent()) {
				childSpan.addEvent("Child processing", Attributes.of(AttributeKey.stringKey("detail"), "step-1"));
				simulateWork(50);
				childSpan.addEvent("Child completed");
			} finally {
				childSpan.end();
			}

			parentSpan.addEvent("Parent completed");
		} finally {
			parentSpan.end();
		}
	}

	public void executeWithError(String operationName) {
		Tracer t = tracer;
		if (t == null)
			return;

		Span span = t.spanBuilder(operationName).setSpanKind(SpanKind.INTERNAL).startSpan();
		try (Scope scope = span.makeCurrent()) {
			span.addEvent("About to fail");
			throw new RuntimeException("Simulated error in " + operationName);
		} catch (Exception e) {
			span.setStatus(StatusCode.ERROR, e.getMessage());
			span.recordException(e);
		} finally {
			span.end();
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
