package org.eclipse.osgi.technology.opentelemetry.integration.jakarta.servlet;

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
import org.osgi.service.servlet.runtime.HttpServiceRuntime;
import org.osgi.service.servlet.runtime.dto.RuntimeDTO;
import org.osgi.service.servlet.runtime.dto.ServletContextDTO;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Registers HTTP Whiteboard runtime metrics as OpenTelemetry async gauges.
 * <p>
 * Dynamically tracks all available {@link HttpServiceRuntime} instances and
 * queries their DTOs on every metric collection cycle to reflect the live state:
 * <ul>
 *   <li>{@code osgi.http.whiteboard.contexts} — Number of active servlet contexts</li>
 *   <li>{@code osgi.http.whiteboard.servlets} — Number of servlets per context</li>
 *   <li>{@code osgi.http.whiteboard.filters} — Number of filters per context</li>
 *   <li>{@code osgi.http.whiteboard.listeners} — Number of listeners per context</li>
 *   <li>{@code osgi.http.whiteboard.resources} — Number of resources per context</li>
 *   <li>{@code osgi.http.whiteboard.error.pages} — Number of error pages per context</li>
 *   <li>{@code osgi.http.whiteboard.failed} — Total failed registrations across all types</li>
 * </ul>
 */
@Component(immediate = true)
public class HttpWhiteboardMetricsComponent {

    private static final Logger LOG = Logger.getLogger(HttpWhiteboardMetricsComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.servlet";
    private static final AttributeKey<Long> SERVICE_ID_KEY = AttributeKey.longKey("osgi.service.id");

    @Reference(policy = ReferencePolicy.DYNAMIC)
    private volatile OpenTelemetry openTelemetry;

    private final ConcurrentHashMap<HttpServiceRuntime, HttpWhiteboardMetricsState> services = new ConcurrentHashMap<>();

    @Activate
    public void activate() {
        LOG.info("HttpWhiteboardMetricsComponent activated");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindHttpServiceRuntime(HttpServiceRuntime runtime, Map<String, Object> properties) {
        long serviceId = (Long) properties.getOrDefault("service.id", 0L);
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);

        ObservableLongGauge contextsGauge = meter.gaugeBuilder("osgi.http.whiteboard.contexts")
            .setDescription("Number of active HTTP Whiteboard servlet contexts")
            .setUnit("{contexts}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    measurement.record(dto.servletContextDTOs != null ? dto.servletContextDTOs.length : 0,
                        Attributes.of(SERVICE_ID_KEY, serviceId));
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read servlet context count", e);
                }
            });

        ObservableLongGauge servletsGauge = meter.gaugeBuilder("osgi.http.whiteboard.servlets")
            .setDescription("Number of servlets registered per servlet context")
            .setUnit("{servlets}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    if (dto.servletContextDTOs != null) {
                        for (ServletContextDTO ctx : dto.servletContextDTOs) {
                            Attributes attrs = contextAttributes(ctx, serviceId);
                            measurement.record(ctx.servletDTOs != null ? ctx.servletDTOs.length : 0, attrs);
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read servlet count", e);
                }
            });

        ObservableLongGauge filtersGauge = meter.gaugeBuilder("osgi.http.whiteboard.filters")
            .setDescription("Number of filters registered per servlet context")
            .setUnit("{filters}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    if (dto.servletContextDTOs != null) {
                        for (ServletContextDTO ctx : dto.servletContextDTOs) {
                            Attributes attrs = contextAttributes(ctx, serviceId);
                            measurement.record(ctx.filterDTOs != null ? ctx.filterDTOs.length : 0, attrs);
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read filter count", e);
                }
            });

        ObservableLongGauge listenersGauge = meter.gaugeBuilder("osgi.http.whiteboard.listeners")
            .setDescription("Number of listeners registered per servlet context")
            .setUnit("{listeners}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    if (dto.servletContextDTOs != null) {
                        for (ServletContextDTO ctx : dto.servletContextDTOs) {
                            Attributes attrs = contextAttributes(ctx, serviceId);
                            measurement.record(ctx.listenerDTOs != null ? ctx.listenerDTOs.length : 0, attrs);
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read listener count", e);
                }
            });

        ObservableLongGauge resourcesGauge = meter.gaugeBuilder("osgi.http.whiteboard.resources")
            .setDescription("Number of resources registered per servlet context")
            .setUnit("{resources}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    if (dto.servletContextDTOs != null) {
                        for (ServletContextDTO ctx : dto.servletContextDTOs) {
                            Attributes attrs = contextAttributes(ctx, serviceId);
                            measurement.record(ctx.resourceDTOs != null ? ctx.resourceDTOs.length : 0, attrs);
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read resource count", e);
                }
            });

        ObservableLongGauge errorPagesGauge = meter.gaugeBuilder("osgi.http.whiteboard.error.pages")
            .setDescription("Number of error pages registered per servlet context")
            .setUnit("{error_pages}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    if (dto.servletContextDTOs != null) {
                        for (ServletContextDTO ctx : dto.servletContextDTOs) {
                            Attributes attrs = contextAttributes(ctx, serviceId);
                            measurement.record(ctx.errorPageDTOs != null ? ctx.errorPageDTOs.length : 0, attrs);
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read error page count", e);
                }
            });

        ObservableLongGauge failedGauge = meter.gaugeBuilder("osgi.http.whiteboard.failed")
            .setDescription("Total number of failed HTTP Whiteboard registrations")
            .setUnit("{registrations}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    RuntimeDTO dto = runtime.getRuntimeDTO();
                    long failed = 0;
                    if (dto.failedServletDTOs != null) failed += dto.failedServletDTOs.length;
                    if (dto.failedFilterDTOs != null) failed += dto.failedFilterDTOs.length;
                    if (dto.failedListenerDTOs != null) failed += dto.failedListenerDTOs.length;
                    if (dto.failedResourceDTOs != null) failed += dto.failedResourceDTOs.length;
                    if (dto.failedErrorPageDTOs != null) failed += dto.failedErrorPageDTOs.length;
                    if (dto.failedServletContextDTOs != null) failed += dto.failedServletContextDTOs.length;
                    measurement.record(failed, Attributes.of(SERVICE_ID_KEY, serviceId));
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read failed registration count", e);
                }
            });

        HttpWhiteboardMetricsState state = new HttpWhiteboardMetricsState(serviceId,
            contextsGauge, servletsGauge, filtersGauge, listenersGauge,
            resourcesGauge, errorPagesGauge, failedGauge);
        services.put(runtime, state);
        LOG.info("Bound HttpServiceRuntime (service.id=" + serviceId + ") — HTTP Whiteboard metrics registered");
    }

    void unbindHttpServiceRuntime(HttpServiceRuntime runtime) {
        HttpWhiteboardMetricsState state = services.remove(runtime);
        if (state != null) {
            state.close();
            LOG.info("Unbound HttpServiceRuntime (service.id=" + state.serviceId() + ") — metrics removed");
        }
    }

    @Deactivate
    void deactivate() {
        services.values().forEach(state -> {
            try { state.close(); } catch (Exception e) { }
        });
        services.clear();
        LOG.info("HttpWhiteboardMetricsComponent deactivated");
    }

    private static Attributes contextAttributes(ServletContextDTO ctx, long serviceId) {
        return Attributes.builder()
            .put(AttributeKey.stringKey("context.name"), ctx.name != null ? ctx.name : "unknown")
            .put(AttributeKey.stringKey("context.path"), ctx.contextPath != null ? ctx.contextPath : "/")
            .put(SERVICE_ID_KEY, serviceId)
            .build();
    }
}
