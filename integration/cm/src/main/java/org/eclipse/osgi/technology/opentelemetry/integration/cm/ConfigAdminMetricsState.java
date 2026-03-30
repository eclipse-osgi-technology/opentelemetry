package org.eclipse.osgi.technology.opentelemetry.integration.cm;

import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Holds the per-{@link org.osgi.service.cm.ConfigurationAdmin} metric state for
 * {@link ConfigAdminMetricsComponent}.
 */
record ConfigAdminMetricsState(long serviceId, ObservableLongGauge configCountGauge,
		ObservableLongGauge factoryCountGauge) implements AutoCloseable {

	@Override
	public void close() {
		closeQuietly(configCountGauge);
		closeQuietly(factoryCountGauge);
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
