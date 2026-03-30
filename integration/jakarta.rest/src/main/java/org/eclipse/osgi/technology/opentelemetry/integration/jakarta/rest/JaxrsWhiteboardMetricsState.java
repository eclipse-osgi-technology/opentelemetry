package org.eclipse.osgi.technology.opentelemetry.integration.jakarta.rest;

import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Holds the OpenTelemetry gauge registrations for a single {@code JaxrsServiceRuntime} instance.
 * <p>
 * Closing this record tears down all associated gauges.
 */
record JaxrsWhiteboardMetricsState(
    long serviceId,
    ObservableLongGauge applicationsGauge,
    ObservableLongGauge resourcesGauge,
    ObservableLongGauge extensionsGauge,
    ObservableLongGauge resourceMethodsGauge,
    ObservableLongGauge failedGauge,
    ObservableLongGauge resourceInfoGauge,
    ObservableLongGauge extensionInfoGauge,
    ObservableLongGauge resourceMethodInfoGauge
) implements AutoCloseable {

    @Override
    public void close() {
        closeQuietly(applicationsGauge);
        closeQuietly(resourcesGauge);
        closeQuietly(extensionsGauge);
        closeQuietly(resourceMethodsGauge);
        closeQuietly(failedGauge);
        closeQuietly(resourceInfoGauge);
        closeQuietly(extensionInfoGauge);
        closeQuietly(resourceMethodInfoGauge);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // ignored
            }
        }
    }
}
