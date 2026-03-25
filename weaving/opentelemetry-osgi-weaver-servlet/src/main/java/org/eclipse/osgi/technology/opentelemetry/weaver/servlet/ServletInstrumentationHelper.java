package org.eclipse.osgi.technology.opentelemetry.weaver.servlet;

import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.osgi.technology.opentelemetry.weaving.OpenTelemetryProxy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Static helper methods called from woven servlet bytecode.
 * <p>
 * This class holds a reference to the {@link OpenTelemetryProxy} which
 * transparently provides noop implementations when the OpenTelemetry service
 * is not available and switches to the real implementation when it arrives.
 */
public final class ServletInstrumentationHelper {

    static final String SCOPE = "org.eclipse.osgi.technology.opentelemetry.weaver.servlet";
    static final String VERSION = "0.1.0";

    private static final AtomicReference<OpenTelemetryProxy> PROXY = new AtomicReference<>();

    private ServletInstrumentationHelper() {}

    /**
     * Called by the weaving infrastructure to set the OpenTelemetry proxy.
     */
    static void setProxy(OpenTelemetryProxy proxy) {
        PROXY.set(proxy);
    }

    /**
     * Called at the beginning of {@code HttpServlet.service()}.
     *
     * @return an array of {@code [Span, Scope, startTimeMillis]} for use in exit/error
     */
    public static Object[] onServiceEnter(HttpServletRequest request, String servletClassName) {
        OpenTelemetryProxy proxy = PROXY.get();
        if (proxy == null) {
            return null;
        }

        Tracer tracer = proxy.getTracer(SCOPE);
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String spanName = method + " " + uri;

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(AttributeKey.stringKey("http.method"), method)
                .setAttribute(AttributeKey.stringKey("http.url"), uri)
                .setAttribute(AttributeKey.stringKey("http.servlet.class"), servletClassName)
                .startSpan();

        String queryString = request.getQueryString();
        if (queryString != null) {
            span.setAttribute(AttributeKey.stringKey("http.query_string"), queryString);
        }

        Scope scope = span.makeCurrent();
        return new Object[] { span, scope, System.currentTimeMillis() };
    }

    /**
     * Called when {@code HttpServlet.service()} throws an exception.
     */
    public static void onServiceError(Object[] spanAndScope, Throwable error) {
        if (spanAndScope == null) {
            return;
        }
        Span span = (Span) spanAndScope[0];
        span.setStatus(StatusCode.ERROR, error.getMessage());
        span.recordException(error);
    }

    /**
     * Called at the end of {@code HttpServlet.service()} (normal or exceptional).
     */
    public static void onServiceExit(Object[] spanAndScope, HttpServletResponse response) {
        if (spanAndScope == null) {
            return;
        }
        Span span = (Span) spanAndScope[0];
        Scope scope = (Scope) spanAndScope[1];
        long startTime = (long) spanAndScope[2];

        try {
            int statusCode = response.getStatus();
            span.setAttribute(AttributeKey.longKey("http.status_code"), statusCode);

            if (statusCode >= 400) {
                span.setStatus(StatusCode.ERROR);
            }

            long duration = System.currentTimeMillis() - startTime;

            OpenTelemetryProxy proxy = PROXY.get();
            if (proxy != null) {
                Meter meter = proxy.getMeter(SCOPE);
                Attributes metricAttrs = Attributes.of(
                        AttributeKey.longKey("http.status_code"), (long) statusCode);

                LongCounter counter = meter.counterBuilder("http.server.requests")
                        .setDescription("Total HTTP servlet requests")
                        .build();
                counter.add(1, metricAttrs);

                LongHistogram histogram = meter.histogramBuilder("http.server.duration")
                        .setDescription("HTTP servlet request duration")
                        .setUnit("ms")
                        .ofLongs()
                        .build();
                histogram.record(duration, metricAttrs);
            }
        } finally {
            span.end();
            scope.close();
        }
    }
}
