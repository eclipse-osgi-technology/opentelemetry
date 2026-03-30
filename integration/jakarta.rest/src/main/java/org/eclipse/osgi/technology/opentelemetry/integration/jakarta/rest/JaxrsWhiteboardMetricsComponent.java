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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.jakartars.runtime.dto.ApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.BaseApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.ExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceMethodInfoDTO;
import org.osgi.service.jakartars.runtime.dto.RuntimeDTO;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Registers JAX-RS Whiteboard runtime metrics as OpenTelemetry async gauges.
 * <p>
 * Dynamically tracks all available {@link JakartarsServiceRuntime} instances and
 * queries their DTOs on every metric collection cycle to reflect the live state:
 * <ul>
 *   <li>{@code osgi.jaxrs.applications} — Number of active JAX-RS applications</li>
 *   <li>{@code osgi.jaxrs.resources} — Number of resources per application</li>
 *   <li>{@code osgi.jaxrs.extensions} — Number of extensions per application</li>
 *   <li>{@code osgi.jaxrs.resource.methods} — Number of resource methods per application</li>
 *   <li>{@code osgi.jaxrs.failed} — Total failed registrations across all types</li>
 * </ul>
 */
@Component(immediate = true)
public class JaxrsWhiteboardMetricsComponent {

    private static final Logger LOG = Logger.getLogger(JaxrsWhiteboardMetricsComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.integration.jakarta.rest";
    private static final AttributeKey<Long> SERVICE_ID_KEY = AttributeKey.longKey("osgi.service.id");

    @Reference(policy = ReferencePolicy.DYNAMIC)
    private volatile OpenTelemetry openTelemetry;

    private final ConcurrentHashMap<JakartarsServiceRuntime, JaxrsWhiteboardMetricsState> services = new ConcurrentHashMap<>();

    @Activate
    public void activate() {
        LOG.info("JaxrsWhiteboardMetricsComponent activated");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindJakartarsServiceRuntime(JakartarsServiceRuntime runtime, Map<String, Object> properties) {
        long serviceId = (Long) properties.getOrDefault("service.id", 0L);
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);

        ObservableLongGauge applicationsGauge = meter.gaugeBuilder("osgi.jaxrs.applications")
            .setDescription("Number of active JAX-RS Whiteboard applications")
            .setUnit("{applications}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    long count = dto.applicationDTOs != null ? dto.applicationDTOs.length : 0;
                    if (dto.defaultApplication != null) count++;
                    measurement.record(count, Attributes.of(SERVICE_ID_KEY, serviceId));
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read application count", e);
                }
            });

        ObservableLongGauge resourcesGauge = meter.gaugeBuilder("osgi.jaxrs.resources")
            .setDescription("Number of JAX-RS resources per application")
            .setUnit("{resources}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    if (dto.defaultApplication != null) {
                        emitApplicationResources(measurement, dto.defaultApplication, serviceId);
                    }
                    if (dto.applicationDTOs != null) {
                        for (ApplicationDTO app : dto.applicationDTOs) {
                            emitApplicationResources(measurement, app, serviceId);
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read resource count", e);
                }
            });

        ObservableLongGauge extensionsGauge = meter.gaugeBuilder("osgi.jaxrs.extensions")
            .setDescription("Number of JAX-RS extensions per application")
            .setUnit("{extensions}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    if (dto.defaultApplication != null) {
                        emitApplicationExtensions(measurement, dto.defaultApplication, serviceId);
                    }
                    if (dto.applicationDTOs != null) {
                        for (ApplicationDTO app : dto.applicationDTOs) {
                            emitApplicationExtensions(measurement, app, serviceId);
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read extension count", e);
                }
            });

        ObservableLongGauge resourceMethodsGauge = meter.gaugeBuilder("osgi.jaxrs.resource.methods")
            .setDescription("Number of JAX-RS resource methods per application")
            .setUnit("{methods}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    if (dto.defaultApplication != null) {
                        emitApplicationResourceMethods(measurement, dto.defaultApplication, serviceId);
                    }
                    if (dto.applicationDTOs != null) {
                        for (ApplicationDTO app : dto.applicationDTOs) {
                            emitApplicationResourceMethods(measurement, app, serviceId);
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read resource method count", e);
                }
            });

        ObservableLongGauge failedGauge = meter.gaugeBuilder("osgi.jaxrs.failed")
            .setDescription("Total number of failed JAX-RS Whiteboard registrations")
            .setUnit("{registrations}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    long failed = 0;
                    if (dto.failedApplicationDTOs != null) failed += dto.failedApplicationDTOs.length;
                    if (dto.failedResourceDTOs != null) failed += dto.failedResourceDTOs.length;
                    if (dto.failedExtensionDTOs != null) failed += dto.failedExtensionDTOs.length;
                    measurement.record(failed, Attributes.of(SERVICE_ID_KEY, serviceId));
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read failed registration count", e);
                }
            });

        ObservableLongGauge resourceInfoGauge = meter.gaugeBuilder("osgi.jaxrs.resource.info")
            .setDescription("Per-resource metadata (always 1)")
            .setUnit("{info}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    emitResourceInfoForApp(measurement, dto.defaultApplication, serviceId);
                    if (dto.applicationDTOs != null) {
                        for (ApplicationDTO app : dto.applicationDTOs) {
                            emitResourceInfoForApp(measurement, app, serviceId);
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read resource info", e);
                }
            });

        ObservableLongGauge extensionInfoGauge = meter.gaugeBuilder("osgi.jaxrs.extension.info")
            .setDescription("Per-extension metadata (always 1)")
            .setUnit("{info}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    emitExtensionInfoForApp(measurement, dto.defaultApplication, serviceId);
                    if (dto.applicationDTOs != null) {
                        for (ApplicationDTO app : dto.applicationDTOs) {
                            emitExtensionInfoForApp(measurement, app, serviceId);
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read extension info", e);
                }
            });

        ObservableLongGauge resourceMethodInfoGauge = meter.gaugeBuilder("osgi.jaxrs.resource.method.info")
            .setDescription("Per-resource-method metadata (always 1)")
            .setUnit("{info}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    emitResourceMethodInfoForApp(measurement, dto.defaultApplication, serviceId);
                    if (dto.applicationDTOs != null) {
                        for (ApplicationDTO app : dto.applicationDTOs) {
                            emitResourceMethodInfoForApp(measurement, app, serviceId);
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read resource method info", e);
                }
            });

        JaxrsWhiteboardMetricsState state = new JaxrsWhiteboardMetricsState(serviceId,
            applicationsGauge, resourcesGauge, extensionsGauge, resourceMethodsGauge, failedGauge,
            resourceInfoGauge, extensionInfoGauge, resourceMethodInfoGauge);
        services.put(runtime, state);
        LOG.info("Bound JakartarsServiceRuntime (service.id=" + serviceId + ") — JAX-RS Whiteboard metrics registered");
    }

    void unbindJakartarsServiceRuntime(JakartarsServiceRuntime runtime) {
        JaxrsWhiteboardMetricsState state = services.remove(runtime);
        if (state != null) {
            state.close();
            LOG.info("Unbound JakartarsServiceRuntime (service.id=" + state.serviceId() + ") — metrics removed");
        }
    }

    @Deactivate
    void deactivate() {
        services.values().forEach(state -> {
            try { state.close(); } catch (Exception e) { }
        });
        services.clear();
        LOG.info("JaxrsWhiteboardMetricsComponent deactivated");
    }

    private static void emitApplicationResources(
            io.opentelemetry.api.metrics.ObservableLongMeasurement measurement,
            BaseApplicationDTO app, long serviceId) {
        Attributes attrs = applicationAttributes(app, serviceId);
        measurement.record(app.resourceDTOs != null ? app.resourceDTOs.length : 0, attrs);
    }

    private static void emitApplicationExtensions(
            io.opentelemetry.api.metrics.ObservableLongMeasurement measurement,
            BaseApplicationDTO app, long serviceId) {
        Attributes attrs = applicationAttributes(app, serviceId);
        measurement.record(app.extensionDTOs != null ? app.extensionDTOs.length : 0, attrs);
    }

    private static void emitApplicationResourceMethods(
            io.opentelemetry.api.metrics.ObservableLongMeasurement measurement,
            BaseApplicationDTO app, long serviceId) {
        Attributes attrs = applicationAttributes(app, serviceId);
        long methodCount = 0;
        if (app.resourceDTOs != null) {
            for (var resource : app.resourceDTOs) {
                if (resource.resourceMethods != null) {
                    methodCount += resource.resourceMethods.length;
                }
            }
        }
        measurement.record(methodCount, attrs);
    }

    private static Attributes applicationAttributes(BaseApplicationDTO app, long serviceId) {
        return Attributes.builder()
            .put(AttributeKey.stringKey("application.name"), app.name != null ? app.name : "default")
            .put(AttributeKey.stringKey("application.base"), app.base != null ? app.base : "/")
            .put(SERVICE_ID_KEY, serviceId)
            .build();
    }

    private static void emitResourceInfoForApp(
            io.opentelemetry.api.metrics.ObservableLongMeasurement measurement,
            BaseApplicationDTO app, long serviceId) {
        if (app == null || app.resourceDTOs == null) return;
        for (ResourceDTO resource : app.resourceDTOs) {
            Attributes attrs = Attributes.builder()
                .put(AttributeKey.stringKey("application.name"), app.name != null ? app.name : "default")
                .put(AttributeKey.stringKey("application.base"), app.base != null ? app.base : "/")
                .put(AttributeKey.stringKey("resource.name"), resource.name != null ? resource.name : "unknown")
                .put(AttributeKey.longKey("resource.method.count"),
                    resource.resourceMethods != null ? resource.resourceMethods.length : 0)
                .put(SERVICE_ID_KEY, serviceId)
                .build();
            measurement.record(1, attrs);
        }
    }

    private static void emitExtensionInfoForApp(
            io.opentelemetry.api.metrics.ObservableLongMeasurement measurement,
            BaseApplicationDTO app, long serviceId) {
        if (app == null || app.extensionDTOs == null) return;
        for (ExtensionDTO extension : app.extensionDTOs) {
            Attributes attrs = Attributes.builder()
                .put(AttributeKey.stringKey("application.name"), app.name != null ? app.name : "default")
                .put(AttributeKey.stringKey("application.base"), app.base != null ? app.base : "/")
                .put(AttributeKey.stringKey("extension.name"), extension.name != null ? extension.name : "unknown")
                .put(AttributeKey.stringKey("extension.types"),
                    extension.extensionTypes != null ? String.join(", ", extension.extensionTypes) : "")
                .put(SERVICE_ID_KEY, serviceId)
                .build();
            measurement.record(1, attrs);
        }
    }

    private static void emitResourceMethodInfoForApp(
            io.opentelemetry.api.metrics.ObservableLongMeasurement measurement,
            BaseApplicationDTO app, long serviceId) {
        if (app == null || app.resourceDTOs == null) return;
        for (ResourceDTO resource : app.resourceDTOs) {
            if (resource.resourceMethods == null) continue;
            for (ResourceMethodInfoDTO method : resource.resourceMethods) {
                Attributes attrs = Attributes.builder()
                    .put(AttributeKey.stringKey("application.name"), app.name != null ? app.name : "default")
                    .put(AttributeKey.stringKey("application.base"), app.base != null ? app.base : "/")
                    .put(AttributeKey.stringKey("resource.name"), resource.name != null ? resource.name : "unknown")
                    .put(AttributeKey.stringKey("method.http"), method.method != null ? method.method : "unknown")
                    .put(AttributeKey.stringKey("method.path"), method.path != null ? method.path : "/")
                    .put(SERVICE_ID_KEY, serviceId)
                    .build();
                measurement.record(1, attrs);
            }
        }
    }
}
