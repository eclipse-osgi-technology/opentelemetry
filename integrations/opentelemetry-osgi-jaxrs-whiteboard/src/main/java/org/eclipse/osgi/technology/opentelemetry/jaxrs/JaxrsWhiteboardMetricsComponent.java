package org.eclipse.osgi.technology.opentelemetry.jaxrs;

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
import org.osgi.service.jaxrs.runtime.JaxrsServiceRuntime;
import org.osgi.service.jaxrs.runtime.dto.ApplicationDTO;
import org.osgi.service.jaxrs.runtime.dto.BaseApplicationDTO;
import org.osgi.service.jaxrs.runtime.dto.RuntimeDTO;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Registers JAX-RS Whiteboard runtime metrics as OpenTelemetry async gauges.
 * <p>
 * Dynamically tracks all available {@link JaxrsServiceRuntime} instances and
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
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.jaxrs";
    private static final AttributeKey<Long> SERVICE_ID_KEY = AttributeKey.longKey("osgi.service.id");

    private final OpenTelemetry openTelemetry;
    private final ConcurrentHashMap<JaxrsServiceRuntime, JaxrsWhiteboardMetricsState> services = new ConcurrentHashMap<>();

    @Activate
    public JaxrsWhiteboardMetricsComponent(@Reference OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        LOG.info("JaxrsWhiteboardMetricsComponent activated");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindJaxrsServiceRuntime(JaxrsServiceRuntime runtime, Map<String, Object> properties) {
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

        JaxrsWhiteboardMetricsState state = new JaxrsWhiteboardMetricsState(serviceId,
            applicationsGauge, resourcesGauge, extensionsGauge, resourceMethodsGauge, failedGauge);
        services.put(runtime, state);
        LOG.info("Bound JaxrsServiceRuntime (service.id=" + serviceId + ") — JAX-RS Whiteboard metrics registered");
    }

    void unbindJaxrsServiceRuntime(JaxrsServiceRuntime runtime) {
        JaxrsWhiteboardMetricsState state = services.remove(runtime);
        if (state != null) {
            state.close();
            LOG.info("Unbound JaxrsServiceRuntime (service.id=" + state.serviceId() + ") — metrics removed");
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
}
