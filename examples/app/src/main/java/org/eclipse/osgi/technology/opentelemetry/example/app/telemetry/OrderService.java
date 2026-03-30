package org.eclipse.osgi.technology.opentelemetry.example.app.telemetry;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Core business service demonstrating an end-to-end trace flow.
 *
 * <p>
 * A single call to {@link #createOrder} produces:
 * <ul>
 * <li>A manual INTERNAL span ({@code order.process}) that is a child of the
 * JAX-RS weaver span</li>
 * <li>An OTel log record correlated to the active trace via trace/span IDs</li>
 * <li>Business metrics (counter + histogram)</li>
 * <li>A JDBC call that the JDBC weaver wraps in a CLIENT span</li>
 * </ul>
 *
 * <p>
 * All of this ends up in <b>one trace with multiple spans</b> because the
 * context propagates through {@link Span#current()}.
 */
@Component(service = OrderService.class, immediate = true)
public class OrderService {

	private static final String SCOPE = "org.eclipse.osgi.technology.opentelemetry.example.order";

	@Reference
	private JdbcTelemetryService jdbc;

	private volatile Tracer tracer;
	private volatile io.opentelemetry.api.logs.Logger otelLogger;
	private volatile LongCounter orderCounter;
	private volatile DoubleHistogram orderDuration;

	@Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
	void bindOpenTelemetry(OpenTelemetry otel) {
		tracer = otel.getTracer(SCOPE, "0.1.0");
		otelLogger = otel.getLogsBridge().loggerBuilder(SCOPE).build();
		Meter meter = otel.getMeter(SCOPE);
		orderCounter = meter.counterBuilder("order.created").setDescription("Number of orders created")
				.setUnit("{orders}").build();
		orderDuration = meter.histogramBuilder("order.processing.duration").setDescription("Time to process an order")
				.setUnit("ms").build();
	}

	void unbindOpenTelemetry(OpenTelemetry otel) {
		tracer = null;
		otelLogger = null;
		orderCounter = null;
		orderDuration = null;
	}

	/**
	 * Creates an order — the full end-to-end flow.
	 *
	 * <p>
	 * Trace structure when called from the JAX-RS resource:
	 * 
	 * <pre>
	 * [SERVER]   servlet weaver
	 *  └─[INTERNAL] JAX-RS weaver  (POST /orders)
	 *     └─[INTERNAL] order.process  (this method)
	 *        ├─ LOG  "Processing order: {name}"
	 *        ├─ METRIC  order.created +1
	 *        └─[CLIENT] JDBC weaver  (INSERT INTO example_events …)
	 * </pre>
	 */
	public Map<String, Object> createOrder(String name, String data) throws SQLException {
		long start = System.currentTimeMillis();

		Tracer t = tracer;
		if (t == null) {
			return doCreate(name, data, start);
		}

		Span span = t.spanBuilder("order.process").setSpanKind(SpanKind.INTERNAL).setAttribute("order.name", name)
				.setAttribute("order.data.length", data != null ? data.length() : 0)
				.setAttribute("order.source", "rest-api").startSpan();
		try (Scope scope = span.makeCurrent()) {

			// Event: request received with input details
			span.addEvent("order.received",
					Attributes.builder().put(AttributeKey.stringKey("order.name"), name)
							.put(AttributeKey.longKey("order.data.size"), data != null ? data.length() : 0)
							.put(AttributeKey.stringKey("order.thread"), Thread.currentThread().getName()).build());

			// Log correlated to this trace via traceId/spanId
			emitLog("Processing order: " + name, Severity.INFO,
					Attributes.of(AttributeKey.stringKey("order.name"), name));

			// Event: validation passed
			span.addEvent("order.validated", Attributes.of(AttributeKey.booleanKey("order.valid"), true,
					AttributeKey.stringKey("order.validation.rule"), "name-not-empty"));

			// Event: before persistence
			span.addEvent("order.persisting", Attributes.of(AttributeKey.stringKey("db.table"), "example_events",
					AttributeKey.stringKey("db.operation"), "INSERT"));

			Map<String, Object> result = doCreate(name, data, start);

			long orderId = (Long) result.get("id");
			long duration = System.currentTimeMillis() - start;

			// Event: after persistence with result
			span.addEvent("order.persisted",
					Attributes.builder().put(AttributeKey.longKey("order.id"), orderId)
							.put(AttributeKey.stringKey("db.table"), "example_events")
							.put(AttributeKey.longKey("db.rows.affected"), 1L).build());

			// Event: metrics recorded
			span.addEvent("order.metrics.recorded",
					Attributes.of(AttributeKey.stringKey("metric.counter"), "order.created",
							AttributeKey.stringKey("metric.histogram"), "order.processing.duration",
							AttributeKey.doubleKey("metric.duration.ms"), (double) duration));

			// Log: order completed
			emitLog("Order created: id=" + orderId + " name=" + name + " duration=" + duration + "ms", Severity.INFO,
					Attributes.builder().put(AttributeKey.longKey("order.id"), orderId)
							.put(AttributeKey.stringKey("order.name"), name)
							.put(AttributeKey.longKey("order.duration.ms"), duration).build());

			// Event: processing complete
			span.addEvent("order.completed",
					Attributes.builder().put(AttributeKey.longKey("order.id"), orderId)
							.put(AttributeKey.longKey("order.duration.ms"), duration)
							.put(AttributeKey.stringKey("order.status"), "created").build());

			// Enrich span with final attributes
			span.setAttribute("order.id", orderId);
			span.setAttribute("order.duration.ms", duration);
			span.setStatus(StatusCode.OK);

			return result;

		} catch (Exception e) {
			span.setStatus(StatusCode.ERROR, e.getMessage());
			span.recordException(e);
			span.addEvent("order.failed",
					Attributes.of(AttributeKey.stringKey("exception.type"), e.getClass().getName(),
							AttributeKey.stringKey("exception.message"), e.getMessage() != null ? e.getMessage() : ""));
			emitLog("Order failed: " + e.getMessage(), Severity.ERROR,
					Attributes.of(AttributeKey.stringKey("order.name"), name, AttributeKey.stringKey("exception.type"),
							e.getClass().getName()));
			throw e;
		} finally {
			span.end();
		}
	}

	public List<Map<String, Object>> listOrders() throws SQLException {
		Tracer t = tracer;
		if (t == null) {
			return jdbc.queryEvents();
		}

		Span span = t.spanBuilder("order.list").setSpanKind(SpanKind.INTERNAL).setAttribute("order.operation", "list")
				.startSpan();
		try (Scope scope = span.makeCurrent()) {
			span.addEvent("order.query.started", Attributes.of(AttributeKey.stringKey("db.table"), "example_events",
					AttributeKey.stringKey("db.operation"), "SELECT"));

			List<Map<String, Object>> orders = jdbc.queryEvents();

			span.addEvent("order.query.completed", Attributes.of(AttributeKey.longKey("order.count"),
					(long) orders.size(), AttributeKey.stringKey("db.table"), "example_events"));
			span.setAttribute("order.count", orders.size());

			emitLog("Listed " + orders.size() + " orders", Severity.INFO,
					Attributes.of(AttributeKey.longKey("order.count"), (long) orders.size()));
			return orders;
		} finally {
			span.end();
		}
	}

	public Map<String, Object> getOrder(long id) throws SQLException {
		Tracer t = tracer;
		if (t == null) {
			return jdbc.queryById(id);
		}

		Span span = t.spanBuilder("order.get").setSpanKind(SpanKind.INTERNAL).setAttribute("order.id", id)
				.setAttribute("order.operation", "get").startSpan();
		try (Scope scope = span.makeCurrent()) {
			span.addEvent("order.lookup.started",
					Attributes.of(AttributeKey.longKey("order.id"), id, AttributeKey.stringKey("db.table"),
							"example_events", AttributeKey.stringKey("db.operation"), "SELECT"));

			Map<String, Object> order = jdbc.queryById(id);
			boolean found = !order.isEmpty();

			span.addEvent("order.lookup.completed",
					Attributes.of(AttributeKey.longKey("order.id"), id, AttributeKey.booleanKey("order.found"), found));
			span.setAttribute("order.found", found);

			emitLog(found ? "Order found: id=" + id : "Order not found: id=" + id,
					found ? Severity.INFO : Severity.WARN,
					Attributes.of(AttributeKey.longKey("order.id"), id, AttributeKey.booleanKey("order.found"), found));
			return order;
		} finally {
			span.end();
		}
	}

	// --- internal helpers ---

	private Map<String, Object> doCreate(String name, String data, long start) throws SQLException {
		// JDBC call — the JDBC weaver creates a CLIENT span here
		long id = jdbc.insertEvent(name, data);

		// Metrics
		LongCounter counter = orderCounter;
		if (counter != null) {
			counter.add(1, Attributes.of(AttributeKey.stringKey("order.name"), name));
		}
		DoubleHistogram histogram = orderDuration;
		if (histogram != null) {
			histogram.record(System.currentTimeMillis() - start,
					Attributes.of(AttributeKey.stringKey("order.name"), name));
		}

		return Map.of("id", id, "name", name, "data", data, "status", "created");
	}

	private void emitLog(String body, Severity severity, Attributes attrs) {
		io.opentelemetry.api.logs.Logger logger = otelLogger;
		if (logger != null) {
			logger.logRecordBuilder().setSeverity(severity).setBody(body).setAllAttributes(attrs).emit();
		}
	}
}
