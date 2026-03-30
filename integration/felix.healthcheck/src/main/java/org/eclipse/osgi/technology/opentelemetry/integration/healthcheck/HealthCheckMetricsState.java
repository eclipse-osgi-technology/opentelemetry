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

package org.eclipse.osgi.technology.opentelemetry.integration.healthcheck;

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
