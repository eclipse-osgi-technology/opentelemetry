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

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Exposes OSGi Configuration Admin state as OpenTelemetry metrics.
 * <p>
 * Publishes:
 * <ul>
 * <li>{@code osgi.cm.configuration.count} — total number of configurations</li>
 * <li>{@code osgi.cm.events.total} — counter of configuration events by type
 * (CM_UPDATED, CM_DELETED, CM_LOCATION_CHANGED)</li>
 * <li>{@code osgi.cm.factory.count} — number of factory configurations</li>
 * </ul>
 */
@Component(immediate = true, service = ConfigurationListener.class)
public class ConfigAdminMetricsComponent implements ConfigurationListener {

	private static final Logger LOG = Logger.getLogger(ConfigAdminMetricsComponent.class.getName());
	private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.integration.cm";
	private static final AttributeKey<Long> SERVICE_ID_KEY = AttributeKey.longKey("osgi.service.id");

	@Reference(policy = ReferencePolicy.DYNAMIC)
	private volatile OpenTelemetry openTelemetry;

	private LongCounter eventsCounter;
	private final ConcurrentHashMap<ConfigurationAdmin, ConfigAdminMetricsState> services = new ConcurrentHashMap<>();

	@Activate
	public void activate() {
		Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);
		this.eventsCounter = meter.counterBuilder("osgi.cm.events.total")
				.setDescription("Total number of configuration events").setUnit("{events}").build();
		LOG.info("ConfigAdminMetricsComponent activated — registering Config Admin metrics");
	}

	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
	void bindConfigurationAdmin(ConfigurationAdmin configAdmin, Map<String, Object> properties) {
		long serviceId = (Long) properties.get(Constants.SERVICE_ID);
		Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);

		ObservableLongGauge configCountGauge = meter.gaugeBuilder("osgi.cm.configuration.count")
				.setDescription("Total number of configurations").setUnit("{configurations}").ofLongs()
				.buildWithCallback(measurement -> {
					try {
						Configuration[] configs = configAdmin.listConfigurations(null);
						measurement.record(configs != null ? configs.length : 0,
								Attributes.of(SERVICE_ID_KEY, serviceId));
					} catch (IOException | org.osgi.framework.InvalidSyntaxException e) {
						LOG.log(Level.FINE, "Failed to count configurations", e);
					}
				});

		ObservableLongGauge factoryCountGauge = meter.gaugeBuilder("osgi.cm.factory.count")
				.setDescription("Number of factory configurations").setUnit("{configurations}").ofLongs()
				.buildWithCallback(measurement -> {
					try {
						Configuration[] configs = configAdmin.listConfigurations(null);
						if (configs != null) {
							long factoryCount = 0;
							for (Configuration config : configs) {
								if (config.getFactoryPid() != null) {
									factoryCount++;
								}
							}
							measurement.record(factoryCount, Attributes.of(SERVICE_ID_KEY, serviceId));
						} else {
							measurement.record(0, Attributes.of(SERVICE_ID_KEY, serviceId));
						}
					} catch (IOException | org.osgi.framework.InvalidSyntaxException e) {
						LOG.log(Level.FINE, "Failed to count factory configurations", e);
					}
				});

		ConfigAdminMetricsState state = new ConfigAdminMetricsState(serviceId, configCountGauge, factoryCountGauge);
		services.put(configAdmin, state);
		LOG.info("Bound ConfigurationAdmin service.id=" + serviceId);
	}

	void unbindConfigurationAdmin(ConfigurationAdmin configAdmin) {
		ConfigAdminMetricsState state = services.remove(configAdmin);
		if (state != null) {
			state.close();
			LOG.info("Unbound ConfigurationAdmin service.id=" + state.serviceId());
		}
	}

	@Deactivate
	public void deactivate() {
		services.forEach((configAdmin, state) -> state.close());
		services.clear();
		LOG.info("ConfigAdminMetricsComponent deactivated");
	}

	@Override
	public void configurationEvent(ConfigurationEvent event) {
		try {
			String eventType = eventTypeToString(event.getType());
			eventsCounter.add(1, Attributes.of(AttributeKey.stringKey("cm.event.type"), eventType));
		} catch (Exception e) {
			LOG.log(Level.FINE, "Failed to record configuration event metric", e);
		}
	}

	static String eventTypeToString(int type) {
		return switch (type) {
		case ConfigurationEvent.CM_UPDATED -> "CM_UPDATED";
		case ConfigurationEvent.CM_DELETED -> "CM_DELETED";
		case ConfigurationEvent.CM_LOCATION_CHANGED -> "CM_LOCATION_CHANGED";
		default -> "UNKNOWN(" + type + ")";
		};
	}
}
