package org.eclipse.osgi.technology.opentelemetry.weaving.jdbc;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.osgi.technology.opentelemetry.weaving.hook.OpenTelemetryProxy;

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
 * Static helper methods called from woven JDBC bytecode.
 * Holds a reference to the {@link OpenTelemetryProxy} which provides
 * noop implementations when the OpenTelemetry service is not available.
 */
public final class JdbcInstrumentationHelper {

    static final String SCOPE =
            "org.eclipse.osgi.technology.opentelemetry.weaving.jdbc";
    private static final int MAX_SQL_LENGTH = 1000;

    private static final AtomicReference<OpenTelemetryProxy> PROXY = new AtomicReference<>();

    private JdbcInstrumentationHelper() {}

    static void setProxy(OpenTelemetryProxy proxy) {
        PROXY.set(proxy);
    }

    /**
     * Called at the beginning of a JDBC execute method.
     *
     * @param sql the SQL statement (may be null for prepared statements)
     * @param operation the method name (execute, executeQuery, etc.)
     * @param statementClass the JDBC statement implementation class name
     * @return context array {@code [Span, Scope, startTimeMillis]} or null
     */
    public static Object[] onExecuteEnter(String sql, String operation, String statementClass) {
        OpenTelemetryProxy proxy = PROXY.get();
        if (proxy == null) {
            return null;
        }

        Tracer tracer = proxy.getTracer(SCOPE);
        String spanName = sql != null ? summarizeSql(sql) : "JDBC " + operation;

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(AttributeKey.stringKey("db.system"), "jdbc")
                .setAttribute(AttributeKey.stringKey("db.operation"), operation)
                .setAttribute(AttributeKey.stringKey("db.jdbc.driver_class"), statementClass)
                .startSpan();

        if (sql != null) {
            String truncated = sql.length() > MAX_SQL_LENGTH
                    ? sql.substring(0, MAX_SQL_LENGTH) + "..." : sql;
            span.setAttribute(AttributeKey.stringKey("db.statement"), truncated);
        }

        Scope scope = span.makeCurrent();
        return new Object[] { span, scope, System.currentTimeMillis() };
    }

    /**
     * Called when a JDBC execute method throws an exception.
     */
    public static void onExecuteError(Object[] context, Throwable error) {
        if (context == null) {
            return;
        }
        Span span = (Span) context[0];
        span.setStatus(StatusCode.ERROR, error.getMessage());
        span.recordException(error);
    }

    /**
     * Called at the end of a JDBC execute method (normal or exceptional).
     */
    public static void onExecuteExit(Object[] context) {
        if (context == null) {
            return;
        }
        Span span = (Span) context[0];
        Scope scope = (Scope) context[1];
        long startTime = (long) context[2];

        try {
            long duration = System.currentTimeMillis() - startTime;

            OpenTelemetryProxy proxy = PROXY.get();
            if (proxy != null) {
                Meter meter = proxy.getMeter(SCOPE);
                Attributes attrs = Attributes.of(
                        AttributeKey.stringKey("db.system"), "jdbc");

                LongCounter counter = meter.counterBuilder("db.client.operations")
                        .setDescription("Total JDBC operations")
                        .build();
                counter.add(1, attrs);

                LongHistogram histogram = meter.histogramBuilder("db.client.duration")
                        .setDescription("JDBC operation duration")
                        .setUnit("ms")
                        .ofLongs()
                        .build();
                histogram.record(duration, attrs);
            }
        } finally {
            span.end();
            scope.close();
        }
    }

    private static String summarizeSql(String sql) {
        String trimmed = sql.strip();
        if (trimmed.isEmpty()) {
            return "JDBC";
        }
        String firstWord = trimmed.split("\\s+", 2)[0].toUpperCase();
        return switch (firstWord) {
            case "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER" -> firstWord;
            default -> "JDBC";
        };
    }
}
