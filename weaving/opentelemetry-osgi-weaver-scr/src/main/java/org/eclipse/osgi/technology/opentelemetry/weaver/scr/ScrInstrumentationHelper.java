package org.eclipse.osgi.technology.opentelemetry.weaver.scr;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.osgi.technology.opentelemetry.weaving.OpenTelemetryProxy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Static helper methods called from woven DS component bytecode.
 * <p>
 * This class holds a reference to the {@link OpenTelemetryProxy} which
 * transparently provides noop implementations when the OpenTelemetry service
 * is not available and switches to the real implementation when it arrives.
 * <p>
 * Each lifecycle operation (activate, deactivate, modified, constructor)
 * produces an OpenTelemetry span and records metrics for the operation count
 * and duration.
 */
public final class ScrInstrumentationHelper {

    static final String SCOPE =
            "org.eclipse.osgi.technology.opentelemetry.weaver.scr";
    static final String VERSION = "0.1.0";

    private static final AtomicReference<OpenTelemetryProxy> PROXY = new AtomicReference<>();

    private ScrInstrumentationHelper() {}

    /**
     * Called by the weaving infrastructure to set the OpenTelemetry proxy.
     */
    static void setProxy(OpenTelemetryProxy proxy) {
        PROXY.set(proxy);
    }

    /**
     * Called at the beginning of a DS lifecycle method.
     *
     * @param componentName the DS component name from the XML descriptor
     * @param componentClass the fully qualified component implementation class
     * @param methodName the lifecycle method name
     * @param action the lifecycle action (activate, deactivate, modified, constructor)
     * @return an array of {@code [Span, Scope, startTimeMillis, action, componentClass]}
     */
    public static Object[] onLifecycleEnter(String componentName, String componentClass,
            String methodName, String action) {
        OpenTelemetryProxy proxy = PROXY.get();
        if (proxy == null) {
            return null;
        }

        Tracer tracer = proxy.getTracer(SCOPE);
        String simpleClassName = componentClass.substring(
                componentClass.lastIndexOf('.') + 1);
        String spanName = "scr." + action + " " + simpleClassName;

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AttributeKey.stringKey("scr.component.name"), componentName)
                .setAttribute(AttributeKey.stringKey("scr.component.class"), componentClass)
                .setAttribute(AttributeKey.stringKey("scr.lifecycle.action"), action)
                .setAttribute(AttributeKey.stringKey("scr.method.name"), methodName)
                .startSpan();

        Scope scope = span.makeCurrent();
        return new Object[] { span, scope, System.currentTimeMillis(), action, componentClass };
    }

    /**
     * Called when a DS lifecycle method throws an exception.
     */
    public static void onLifecycleError(Object[] context, Throwable error) {
        if (context == null) {
            return;
        }
        Span span = (Span) context[0];
        span.setStatus(StatusCode.ERROR, error.getMessage());
        span.recordException(error);
    }

    /**
     * Called at the end of a DS lifecycle method (normal or exceptional).
     */
    public static void onLifecycleExit(Object[] context) {
        if (context == null) {
            return;
        }
        Span span = (Span) context[0];
        Scope scope = (Scope) context[1];
        long startTime = (long) context[2];
        String action = (String) context[3];
        String componentClass = (String) context[4];

        try {
            long duration = System.currentTimeMillis() - startTime;

            OpenTelemetryProxy proxy = PROXY.get();
            if (proxy != null) {
                Meter meter = proxy.getMeter(SCOPE);
                Attributes attrs = Attributes.of(
                        AttributeKey.stringKey("scr.lifecycle.action"), action,
                        AttributeKey.stringKey("scr.component.class"), componentClass);

                meter.counterBuilder("scr.lifecycle.operations")
                        .setDescription("SCR component lifecycle operations")
                        .build()
                        .add(1, attrs);

                meter.histogramBuilder("scr.lifecycle.duration")
                        .setDescription("SCR component lifecycle method duration")
                        .setUnit("ms")
                        .ofLongs()
                        .build()
                        .record(duration, attrs);
            }
        } finally {
            span.end();
            scope.close();
        }
    }
}
