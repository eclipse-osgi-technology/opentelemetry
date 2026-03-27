package org.eclipse.osgi.technology.opentelemetry.example.app.servlet;

import java.io.IOException;
import java.time.Instant;
import java.util.logging.Logger;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Servlet that explicitly targets the <b>Logging OpenTelemetry</b> service
 * using a {@code @Reference} target filter. While all other components use
 * the higher-ranked OTLP/HTTP exporter (service.ranking=100), this servlet
 * binds exclusively to the logging exporter via
 * {@code (component.name=logging-opentelemetry)}.
 *
 * <p>This demonstrates how to use OSGi service targeting to route specific
 * telemetry to a different backend. In production, you might use this to
 * send debug-level telemetry to a local log while operational telemetry
 * goes to the central collector.
 *
 * <p>The reference is optional and dynamic — the servlet remains functional
 * even when the logging OpenTelemetry service is not configured.
 */
@Component(
    service = jakarta.servlet.Servlet.class,
    immediate = true,
    property = {
        "osgi.http.whiteboard.servlet.pattern=/logonly/*",
        "osgi.http.whiteboard.servlet.name=LogOnlyServlet",
        "osgi.http.whiteboard.target=(id=http1)"
    }
)
public class LogOnlyServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(LogOnlyServlet.class.getName());
    private static final String SCOPE =
            "org.eclipse.osgi.technology.opentelemetry.example.servlet.logonly";

    private volatile OpenTelemetry loggingOtel;

    /**
     * Binds exclusively to the logging-opentelemetry component, ignoring
     * the higher-ranked OTLP/HTTP exporter. The target filter ensures only
     * the logging exporter is used.
     */
    @Reference(
        target = "(component.name=logging-opentelemetry)",
        cardinality = ReferenceCardinality.OPTIONAL,
        policy = ReferencePolicy.DYNAMIC
    )
    void bindOpenTelemetry(OpenTelemetry openTelemetry) {
        LOG.info("LogOnlyServlet: bound to logging-opentelemetry");
        this.loggingOtel = openTelemetry;
    }

    void unbindOpenTelemetry(OpenTelemetry openTelemetry) {
        LOG.info("LogOnlyServlet: unbound from logging-opentelemetry");
        this.loggingOtel = null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        OpenTelemetry otel = loggingOtel;
        String path = req.getPathInfo() != null ? req.getPathInfo() : "/";

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        if (otel != null) {
            // Create a span — this goes ONLY to the logging exporter (stdout)
            Tracer tracer = otel.getTracer(SCOPE);
            Span span = tracer.spanBuilder("GET /logonly" + path)
                    .setSpanKind(SpanKind.SERVER)
                    .setAttribute("http.method", "GET")
                    .setAttribute("http.path", path)
                    .startSpan();

            try (Scope scope = span.makeCurrent()) {
                // Emit a log record — also goes only to logging exporter
                otel.getLogsBridge().loggerBuilder(SCOPE).build()
                        .logRecordBuilder()
                        .setSeverity(Severity.INFO)
                        .setBody("LogOnly request: GET " + path)
                        .setAttribute(AttributeKey.stringKey("servlet"), "LogOnlyServlet")
                        .emit();

                span.addEvent("Request processed");
            } finally {
                span.end();
            }

            resp.getWriter().write(
                    "{\"service\":\"logonly\",\"exporter\":\"logging\","
                    + "\"message\":\"This telemetry goes ONLY to stdout, not to the OTLP collector\","
                    + "\"path\":\"" + path + "\","
                    + "\"timestamp\":\"" + Instant.now() + "\"}");
        } else {
            resp.getWriter().write(
                    "{\"service\":\"logonly\",\"exporter\":\"none\","
                    + "\"message\":\"Logging OpenTelemetry service not available\","
                    + "\"timestamp\":\"" + Instant.now() + "\"}");
        }
    }
}
