package org.eclipse.osgi.technology.opentelemetry.integration.jakarta.servlet;

import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Holds the OpenTelemetry gauge registrations for a single {@code HttpServiceRuntime} instance.
 * <p>
 * Closing this record tears down all associated gauges.
 */
record HttpWhiteboardMetricsState(
    long serviceId,
    ObservableLongGauge contextsGauge,
    ObservableLongGauge servletsGauge,
    ObservableLongGauge filtersGauge,
    ObservableLongGauge listenersGauge,
    ObservableLongGauge resourcesGauge,
    ObservableLongGauge errorPagesGauge,
    ObservableLongGauge failedGauge,
    ObservableLongGauge servletInfoGauge,
    ObservableLongGauge filterInfoGauge,
    ObservableLongGauge listenerInfoGauge,
    ObservableLongGauge resourceInfoGauge
) implements AutoCloseable {

    @Override
    public void close() {
        closeQuietly(contextsGauge);
        closeQuietly(servletsGauge);
        closeQuietly(filtersGauge);
        closeQuietly(listenersGauge);
        closeQuietly(resourcesGauge);
        closeQuietly(errorPagesGauge);
        closeQuietly(failedGauge);
        closeQuietly(servletInfoGauge);
        closeQuietly(filterInfoGauge);
        closeQuietly(listenerInfoGauge);
        closeQuietly(resourceInfoGauge);
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
