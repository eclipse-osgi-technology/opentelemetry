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
