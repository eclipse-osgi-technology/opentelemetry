package org.eclipse.osgi.technology.opentelemetry.healthcheck;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.ResultLog;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckExecutor;
import org.apache.felix.hc.api.execution.HealthCheckMetadata;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Periodically executes all registered Felix Health Checks and creates
 * OpenTelemetry traces for the results.
 * <p>
 * Supports multiple {@link HealthCheckExecutor} services dynamically.
 * Every 30 seconds per executor, this component creates a parent span
 * {@code osgi.hc.execution} with a child span per health check result.
 * Health checks with non-OK status produce error or warning spans.
 * This makes health check failures visible in distributed trace backends.
 */
@Component(immediate = true)
public class HealthCheckTracingComponent {

    private static final Logger LOG = Logger.getLogger(HealthCheckTracingComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.healthcheck.tracing";
    private static final AttributeKey<Long> SERVICE_ID_KEY = AttributeKey.longKey("osgi.service.id");

    private final OpenTelemetry openTelemetry;
    private final ConcurrentHashMap<HealthCheckExecutor, ScheduledExecutorService> services = new ConcurrentHashMap<>();

    @Activate
    public HealthCheckTracingComponent(@Reference OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        LOG.info("HealthCheckTracingComponent activated — starting periodic health check traces");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindHealthCheckExecutor(HealthCheckExecutor executor, Map<String, Object> properties) {
        long serviceId = (Long) properties.get(Constants.SERVICE_ID);
        LOG.info("Binding HealthCheckExecutor service.id=" + serviceId + " for tracing");

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "otel-hc-tracing-" + serviceId);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> runHealthChecks(executor, serviceId), 10, 30, TimeUnit.SECONDS);
        services.put(executor, scheduler);
    }

    void unbindHealthCheckExecutor(HealthCheckExecutor executor) {
        ScheduledExecutorService scheduler = services.remove(executor);
        if (scheduler != null) {
            LOG.info("Unbinding HealthCheckExecutor for tracing");
            scheduler.shutdown();
        }
    }

    @Deactivate
    public void deactivate() {
        services.forEach((executor, scheduler) -> scheduler.shutdown());
        services.clear();
        LOG.info("HealthCheckTracingComponent deactivated");
    }

    private void runHealthChecks(HealthCheckExecutor executor, long serviceId) {
        try {
            Tracer tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE, "0.1.0");
            List<HealthCheckExecutionResult> results = executor.execute(HealthCheckSelector.empty());

            long okCount = results.stream()
                .filter(r -> r.getHealthCheckResult().isOk())
                .count();
            long problemCount = results.size() - okCount;

            Span parentSpan = tracer.spanBuilder("osgi.hc.execution")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(SERVICE_ID_KEY, serviceId)
                .setAttribute("hc.total", (long) results.size())
                .setAttribute("hc.ok", okCount)
                .setAttribute("hc.problems", problemCount)
                .startSpan();

            try (Scope parentScope = parentSpan.makeCurrent()) {
                for (HealthCheckExecutionResult result : results) {
                    traceResult(tracer, result);
                }

                if (problemCount > 0) {
                    parentSpan.setStatus(StatusCode.ERROR,
                        problemCount + " health check(s) not OK");
                } else {
                    parentSpan.addEvent("All " + results.size() + " health checks OK");
                }
            } finally {
                parentSpan.end();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Health check tracing failed", e);
        }
    }

    private void traceResult(Tracer tracer, HealthCheckExecutionResult execResult) {
        HealthCheckMetadata metadata = execResult.getHealthCheckMetadata();
        Result result = execResult.getHealthCheckResult();
        String name = metadata.getName() != null ? metadata.getName() : "unknown";
        String status = result.getStatus().name();

        Span span = tracer.spanBuilder("osgi.hc.result." + status.toLowerCase())
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(AttributeKey.stringKey("hc.name"), name)
            .setAttribute(AttributeKey.stringKey("hc.status"), status)
            .setAttribute(AttributeKey.longKey("hc.duration_ms"), execResult.getElapsedTimeInMs())
            .setAttribute(AttributeKey.booleanKey("hc.timed_out"), execResult.hasTimedOut())
            .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            if (metadata.getTitle() != null) {
                span.setAttribute("hc.title", metadata.getTitle());
            }

            List<String> tags = metadata.getTags();
            if (tags != null && !tags.isEmpty()) {
                span.setAttribute(AttributeKey.stringArrayKey("hc.tags"), tags);
            }

            for (ResultLog.Entry entry : result) {
                span.addEvent(entry.getStatus().name() + ": " + entry.getMessage());
            }

            if (!result.isOk()) {
                span.setStatus(StatusCode.ERROR, "Health check " + name + ": " + status);
            }
        } finally {
            span.end();
        }
    }
}
