/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 * 
 */

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
