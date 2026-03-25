package org.eclipse.osgi.technology.opentelemetry.healthcheck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckExecutor;
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
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Exposes Apache Felix Health Check results as OpenTelemetry metrics.
 * <p>
 * Supports multiple {@link HealthCheckExecutor} services dynamically.
 * For each bound executor, periodically executes all registered health checks and publishes:
 * <ul>
 *   <li>{@code osgi.hc.count} — total number of registered health checks</li>
 *   <li>{@code osgi.hc.status} — number of health checks per result status
 *       (OK, WARN, TEMPORARILY_UNAVAILABLE, CRITICAL, HEALTH_CHECK_ERROR)</li>
 *   <li>{@code osgi.hc.duration.milliseconds} — last execution duration per health check</li>
 *   <li>{@code osgi.hc.executions.total} — counter of total health check executions</li>
 * </ul>
 */
@Component(immediate = true)
public class HealthCheckMetricsComponent {

    private static final Logger LOG = Logger.getLogger(HealthCheckMetricsComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.healthcheck";
    private static final AttributeKey<Long> SERVICE_ID_KEY = AttributeKey.longKey("osgi.service.id");

    private final OpenTelemetry openTelemetry;
    private final LongCounter executionsCounter;
    private final ConcurrentHashMap<HealthCheckExecutor, HealthCheckMetricsState> services = new ConcurrentHashMap<>();

    @Activate
    public HealthCheckMetricsComponent(@Reference OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);
        this.executionsCounter = meter.counterBuilder("osgi.hc.executions.total")
            .setDescription("Total number of health check executions")
            .setUnit("{executions}")
            .build();
        LOG.info("HealthCheckMetricsComponent activated — registering health check metrics");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindHealthCheckExecutor(HealthCheckExecutor executor, Map<String, Object> properties) {
        long serviceId = (Long) properties.get(Constants.SERVICE_ID);
        LOG.info("Binding HealthCheckExecutor service.id=" + serviceId);

        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);
        AtomicReference<List<HealthCheckExecutionResult>> lastResults = new AtomicReference<>(List.of());

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "otel-hc-metrics-" + serviceId);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<HealthCheckExecutionResult> results = executor.execute(HealthCheckSelector.empty());
                lastResults.set(results);
                executionsCounter.add(results.size(), Attributes.of(SERVICE_ID_KEY, serviceId));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to execute health checks for metrics (service.id=" + serviceId + ")", e);
            }
        }, 10, 30, TimeUnit.SECONDS);

        ObservableLongGauge countGauge = meter.gaugeBuilder("osgi.hc.count")
            .setDescription("Total number of registered health checks")
            .setUnit("{checks}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    measurement.record(lastResults.get().size(), Attributes.of(SERVICE_ID_KEY, serviceId));
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read health check count", e);
                }
            });

        ObservableLongGauge statusGauge = meter.gaugeBuilder("osgi.hc.status")
            .setDescription("Number of health checks per result status")
            .setUnit("{checks}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    Map<String, Long> statusCounts = new HashMap<>();
                    for (HealthCheckExecutionResult result : lastResults.get()) {
                        String status = result.getHealthCheckResult().getStatus().name();
                        statusCounts.merge(status, 1L, Long::sum);
                    }
                    statusCounts.forEach((status, count) ->
                        measurement.record(count, Attributes.of(
                            AttributeKey.stringKey("hc.status"), status,
                            SERVICE_ID_KEY, serviceId
                        ))
                    );
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read health check status counts", e);
                }
            });

        ObservableLongGauge durationGauge = meter.gaugeBuilder("osgi.hc.duration.milliseconds")
            .setDescription("Last execution duration per health check in milliseconds")
            .setUnit("ms")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    for (HealthCheckExecutionResult result : lastResults.get()) {
                        String name = result.getHealthCheckMetadata().getName();
                        if (name != null) {
                            measurement.record(result.getElapsedTimeInMs(), Attributes.of(
                                AttributeKey.stringKey("hc.name"), name,
                                SERVICE_ID_KEY, serviceId
                            ));
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read health check durations", e);
                }
            });

        HealthCheckMetricsState state = new HealthCheckMetricsState(
            serviceId, scheduler, lastResults, countGauge, statusGauge, durationGauge);
        services.put(executor, state);
    }

    void unbindHealthCheckExecutor(HealthCheckExecutor executor) {
        HealthCheckMetricsState state = services.remove(executor);
        if (state != null) {
            LOG.info("Unbinding HealthCheckExecutor service.id=" + state.serviceId());
            state.close();
        }
    }

    @Deactivate
    public void deactivate() {
        services.forEach((executor, state) -> state.close());
        services.clear();
        LOG.info("HealthCheckMetricsComponent deactivated");
    }

    static String statusToString(Result.Status status) {
        return switch (status) {
            case OK -> "OK";
            case WARN -> "WARN";
            case TEMPORARILY_UNAVAILABLE -> "TEMPORARILY_UNAVAILABLE";
            case CRITICAL -> "CRITICAL";
            case HEALTH_CHECK_ERROR -> "HEALTH_CHECK_ERROR";
        };
    }
}
