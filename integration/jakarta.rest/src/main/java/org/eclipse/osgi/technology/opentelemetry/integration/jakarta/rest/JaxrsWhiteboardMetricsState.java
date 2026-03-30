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
