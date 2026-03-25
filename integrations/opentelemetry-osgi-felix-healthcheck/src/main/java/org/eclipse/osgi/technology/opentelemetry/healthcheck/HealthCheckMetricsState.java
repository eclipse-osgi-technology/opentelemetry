package org.eclipse.osgi.technology.opentelemetry.healthcheck;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;

import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Holds per-executor state for health check metrics collection.
 */
record HealthCheckMetricsState(
        long serviceId,
        ScheduledExecutorService scheduler,
        AtomicReference<List<HealthCheckExecutionResult>> lastResults,
        ObservableLongGauge countGauge,
        ObservableLongGauge statusGauge,
        ObservableLongGauge durationGauge) implements AutoCloseable {

    @Override
    public void close() {
        scheduler.shutdown();
        closeQuietly(countGauge);
        closeQuietly(statusGauge);
        closeQuietly(durationGauge);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
