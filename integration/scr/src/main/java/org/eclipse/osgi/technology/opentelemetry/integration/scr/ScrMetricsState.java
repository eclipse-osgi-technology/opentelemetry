package org.eclipse.osgi.technology.opentelemetry.integration.scr;

import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Holds the per-{@link org.osgi.service.component.runtime.ServiceComponentRuntime}
 * metric gauges created by {@link ScrMetricsComponent}.
 */
record ScrMetricsState(
    long serviceId,
    ObservableLongGauge componentCountGauge,
    ObservableLongGauge componentStatesGauge,
    ObservableLongGauge activeGauge,
    ObservableLongGauge satisfiedRefGauge,
    ObservableLongGauge unsatisfiedRefGauge
) implements AutoCloseable {

    @Override
    public void close() {
        closeQuietly(componentCountGauge);
        closeQuietly(componentStatesGauge);
        closeQuietly(activeGauge);
        closeQuietly(satisfiedRefGauge);
        closeQuietly(unsatisfiedRefGauge);
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
