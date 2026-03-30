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

package org.eclipse.osgi.technology.opentelemetry.example.app.telemetry;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Active telemetry producer that creates custom business metrics using the
 * OpenTelemetry Metrics API.
 *
 * <p>
 * Uses an <b>optional, dynamic</b> reference to {@link OpenTelemetry} with
 * explicit bind/unbind methods. This is the recommended OSGi pattern for
 * telemetry because:
 * <ul>
 * <li>Telemetry can be expensive — the service may be deactivated at
 * runtime</li>
 * <li>The component remains functional even without telemetry</li>
 * <li>bind/unbind track the coming and going of the OpenTelemetry service</li>
 * <li>When OpenTelemetry arrives, instruments are created; when it leaves, the
 * component falls back to no-ops</li>
 * </ul>
 */
@Component(service = MetricsProducerService.class, immediate = true)
public class MetricsProducerService {

	private static final Logger LOG = Logger.getLogger(MetricsProducerService.class.getName());
	private static final String SCOPE = "org.eclipse.osgi.technology.opentelemetry.example.telemetry";

	private volatile LongCounter requestCounter;
	private volatile DoubleHistogram durationHistogram;
	private volatile LongUpDownCounter activeTasksCounter;
	private volatile ObservableLongGauge storeGauge;
	private final AtomicLong activeTasks = new AtomicLong(0);
	private final AtomicLong storeSize = new AtomicLong(0);

	@Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
	void bindOpenTelemetry(OpenTelemetry openTelemetry) {
		LOG.info("OpenTelemetry service bound — creating metric instruments");
		Meter meter = openTelemetry.getMeter(SCOPE);

		requestCounter = meter.counterBuilder("example.requests.total")
				.setDescription("Total number of processed requests").setUnit("{requests}").build();

		durationHistogram = meter.histogramBuilder("example.request.duration")
				.setDescription("Request processing duration").setUnit("ms").build();

		activeTasksCounter = meter.upDownCounterBuilder("example.tasks.active").setDescription("Currently active tasks")
				.setUnit("{tasks}").build();

		storeGauge = meter.gaugeBuilder("example.store.size").setDescription("Number of items in the store")
				.setUnit("{items}").ofLongs().buildWithCallback(m -> m.record(storeSize.get()));
	}

	void unbindOpenTelemetry(OpenTelemetry openTelemetry) {
		LOG.info("OpenTelemetry service unbound — falling back to no-ops");
		requestCounter = null;
		durationHistogram = null;
		activeTasksCounter = null;
		ObservableLongGauge gauge = storeGauge;
		storeGauge = null;
		if (gauge != null) {
			gauge.close();
		}
	}

	public void recordRequest(String type, String path) {
		LongCounter c = requestCounter;
		if (c != null) {
			c.add(1, Attributes.of(AttributeKey.stringKey("request.type"), type, AttributeKey.stringKey("request.path"),
					path));
		}
	}

	public void recordDuration(double millis) {
		DoubleHistogram h = durationHistogram;
		if (h != null) {
			h.record(millis, Attributes.of(AttributeKey.stringKey("operation"), "demo"));
		}
	}

	public void incrementActive() {
		LongUpDownCounter c = activeTasksCounter;
		if (c != null)
			c.add(1);
		activeTasks.incrementAndGet();
	}

	public void decrementActive() {
		LongUpDownCounter c = activeTasksCounter;
		if (c != null)
			c.add(-1);
		activeTasks.decrementAndGet();
	}

	public void setStoreSize(long size) {
		storeSize.set(size);
	}
}
