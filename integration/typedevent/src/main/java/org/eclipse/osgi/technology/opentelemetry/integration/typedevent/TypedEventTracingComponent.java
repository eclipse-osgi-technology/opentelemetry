package org.eclipse.osgi.technology.opentelemetry.integration.typedevent;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.typedevent.UntypedEventHandler;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Traces all events flowing through the OSGi Typed Event Bus by creating
 * OpenTelemetry spans.
 * <p>
 * Registers as an {@link UntypedEventHandler} with {@code event.topics=*}
 * to observe every event. Creates a span named {@code osgi.typedevent.deliver}
 * for each event with attributes for the topic and event data fields.
 */
@Component(immediate = true, service = UntypedEventHandler.class,
    property = "event.topics=*")
public class TypedEventTracingComponent implements UntypedEventHandler {

    private static final Logger LOG = Logger.getLogger(TypedEventTracingComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.typedevent.tracing";
    private static final int MAX_DATA_ATTRIBUTES = 20;

    @Reference(policy = ReferencePolicy.DYNAMIC)
    private volatile OpenTelemetry openTelemetry;

    @Activate
    public void activate() {
        LOG.info("TypedEventTracingComponent activated — tracing typed events");
    }

    @Deactivate
    public void deactivate() {
        LOG.info("TypedEventTracingComponent deactivated");
    }

    @Override
    public void notifyUntyped(String topic, Map<String, Object> event) {
        try {
            Tracer tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE, "0.1.0");

            Span span = tracer.spanBuilder("osgi.typedevent.deliver")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AttributeKey.stringKey("typedevent.topic"), topic)
                .setAttribute(AttributeKey.longKey("typedevent.data.field_count"),
                    (long) (event != null ? event.size() : 0))
                .startSpan();

            try (Scope ignored = span.makeCurrent()) {
                if (event != null) {
                    int count = 0;
                    for (Map.Entry<String, Object> entry : event.entrySet()) {
                        if (count >= MAX_DATA_ATTRIBUTES) {
                            span.setAttribute("typedevent.data.truncated", true);
                            break;
                        }
                        Object value = entry.getValue();
                        if (value instanceof String s) {
                            span.setAttribute("typedevent.data." + entry.getKey(), s);
                        } else if (value instanceof Number n) {
                            span.setAttribute("typedevent.data." + entry.getKey(), n.longValue());
                        } else if (value instanceof Boolean b) {
                            span.setAttribute("typedevent.data." + entry.getKey(), b);
                        } else if (value != null) {
                            span.setAttribute("typedevent.data." + entry.getKey(),
                                value.toString());
                        }
                        count++;
                    }
                }
            } finally {
                span.end();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to trace typed event", e);
        }
    }
}
