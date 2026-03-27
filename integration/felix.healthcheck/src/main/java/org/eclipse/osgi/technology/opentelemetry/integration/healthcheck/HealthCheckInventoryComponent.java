package org.eclipse.osgi.technology.opentelemetry.integration.healthcheck;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import io.opentelemetry.api.logs.Severity;

/**
 * Emits OpenTelemetry log records for all registered Felix Health Check results,
 * providing a detailed inventory of the system health status.
 * <p>
 * Supports multiple {@link HealthCheckExecutor} services dynamically.
 * When a new executor is bound, this component executes all health checks via
 * that executor and emits structured log records containing:
 * <ul>
 *   <li>Health check name, tags, and title</li>
 *   <li>Result status and execution duration</li>
 *   <li>Detailed result log entries</li>
 *   <li>Timeout information</li>
 * </ul>
 */
@Component(immediate = true)
public class HealthCheckInventoryComponent {

    private static final Logger LOG = Logger.getLogger(HealthCheckInventoryComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.healthcheck.inventory";
    private static final AttributeKey<Long> SERVICE_ID_KEY = AttributeKey.longKey("osgi.service.id");

    @Reference(policy = ReferencePolicy.DYNAMIC)
    private volatile OpenTelemetry openTelemetry;

    private final ConcurrentHashMap<HealthCheckExecutor, Long> services = new ConcurrentHashMap<>();

    @Activate
    public void activate() {
        LOG.info("HealthCheckInventoryComponent activated");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindHealthCheckExecutor(HealthCheckExecutor executor, Map<String, Object> properties) {
        long serviceId = (Long) properties.get(Constants.SERVICE_ID);
        LOG.info("Binding HealthCheckExecutor service.id=" + serviceId + " for inventory");
        services.put(executor, serviceId);

        try {
            io.opentelemetry.api.logs.Logger otelLogger =
                openTelemetry.getLogsBridge().loggerBuilder(INSTRUMENTATION_SCOPE)
                    .setInstrumentationVersion("0.1.0")
                    .build();

            List<HealthCheckExecutionResult> results = executor.execute(HealthCheckSelector.empty());

            long okCount = results.stream()
                .filter(r -> r.getHealthCheckResult().isOk())
                .count();

            otelLogger.logRecordBuilder()
                .setSeverity(Severity.INFO)
                .setBody("Health check inventory: " + results.size() + " checks ("
                    + okCount + " OK, " + (results.size() - okCount) + " with issues)")
                .setAttribute(AttributeKey.longKey("hc.total"), (long) results.size())
                .setAttribute(AttributeKey.longKey("hc.ok"), okCount)
                .setAttribute(AttributeKey.longKey("hc.problems"), (long) results.size() - okCount)
                .setAttribute(SERVICE_ID_KEY, serviceId)
                .emit();

            for (HealthCheckExecutionResult execResult : results) {
                emitResultLog(otelLogger, execResult, serviceId);
            }

            LOG.info("HealthCheckInventoryComponent — emitted " + results.size()
                + " health check log records for service.id=" + serviceId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to emit health check inventory for service.id=" + serviceId, e);
        }
    }

    void unbindHealthCheckExecutor(HealthCheckExecutor executor) {
        Long serviceId = services.remove(executor);
        if (serviceId != null) {
            LOG.info("Unbinding HealthCheckExecutor service.id=" + serviceId + " from inventory");
        }
    }

    @Deactivate
    public void deactivate() {
        services.clear();
        LOG.info("HealthCheckInventoryComponent deactivated");
    }

    private void emitResultLog(io.opentelemetry.api.logs.Logger otelLogger,
            HealthCheckExecutionResult execResult, long serviceId) {
        HealthCheckMetadata metadata = execResult.getHealthCheckMetadata();
        Result result = execResult.getHealthCheckResult();
        String name = metadata.getName() != null ? metadata.getName() : "unknown";
        String status = result.getStatus().name();

        Severity severity = switch (result.getStatus()) {
            case OK -> Severity.INFO;
            case WARN -> Severity.WARN;
            case TEMPORARILY_UNAVAILABLE -> Severity.WARN;
            case CRITICAL -> Severity.ERROR;
            case HEALTH_CHECK_ERROR -> Severity.ERROR;
        };

        var builder = otelLogger.logRecordBuilder()
            .setSeverity(severity)
            .setBody("Health check: " + name + " [" + status + "]")
            .setAttribute(AttributeKey.stringKey("hc.name"), name)
            .setAttribute(AttributeKey.stringKey("hc.status"), status)
            .setAttribute(AttributeKey.longKey("hc.duration_ms"), execResult.getElapsedTimeInMs())
            .setAttribute(AttributeKey.booleanKey("hc.timed_out"), execResult.hasTimedOut())
            .setAttribute(SERVICE_ID_KEY, serviceId);

        if (metadata.getTitle() != null) {
            builder.setAttribute(AttributeKey.stringKey("hc.title"), metadata.getTitle());
        }

        List<String> tags = metadata.getTags();
        if (tags != null && !tags.isEmpty()) {
            builder.setAttribute(AttributeKey.stringArrayKey("hc.tags"), tags);
        }

        StringBuilder messages = new StringBuilder();
        for (ResultLog.Entry entry : result) {
            if (!messages.isEmpty()) {
                messages.append("; ");
            }
            messages.append(entry.getStatus().name()).append(": ").append(entry.getMessage());
        }
        if (!messages.isEmpty()) {
            builder.setAttribute(AttributeKey.stringKey("hc.result_log"), messages.toString());
        }

        builder.emit();
    }
}
