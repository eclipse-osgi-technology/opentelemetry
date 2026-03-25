package org.eclipse.osgi.technology.opentelemetry.runtime;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;

/**
 * Registers the individual OpenTelemetry provider interfaces as separate OSGi
 * services for each bound {@link OpenTelemetry} instance.
 * <p>
 * Each registered provider service includes an {@code opentelemetry.name} property
 * derived from the originating service's {@code component.name}, allowing consumers
 * to target a specific exporter type:
 * <pre>
 * &#64;Reference(target = "(opentelemetry.name=otlp-opentelemetry)")
 * private TracerProvider tracerProvider;
 * </pre>
 * <p>
 * The following services are registered per {@link OpenTelemetry} instance:
 * <ul>
 *   <li>{@link TracerProvider}</li>
 *   <li>{@link MeterProvider}</li>
 *   <li>{@link LoggerProvider}</li>
 *   <li>{@link ContextPropagators}</li>
 * </ul>
 */
@Component(immediate = true)
public class OpenTelemetryProviderRegistration {

    private static final Logger LOG = Logger.getLogger(OpenTelemetryProviderRegistration.class.getName());

    private final BundleContext context;
    private final ConcurrentHashMap<OpenTelemetry, List<ServiceRegistration<?>>> registrations = new ConcurrentHashMap<>();

    @Activate
    public OpenTelemetryProviderRegistration(BundleContext context) {
        this.context = context;
        LOG.info("OpenTelemetryProviderRegistration activated");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindOpenTelemetry(OpenTelemetry openTelemetry, Map<String, Object> properties) {
        String name = (String) properties.getOrDefault("component.name", "unknown");
        Object ranking = properties.get("service.ranking");

        Dictionary<String, Object> props = new Hashtable<>();
        props.put("opentelemetry.name", name);
        if (ranking != null) {
            props.put("service.ranking", ranking);
        }

        List<ServiceRegistration<?>> regs = new ArrayList<>();
        regs.add(context.registerService(TracerProvider.class, openTelemetry.getTracerProvider(), props));
        regs.add(context.registerService(MeterProvider.class, openTelemetry.getMeterProvider(), props));
        regs.add(context.registerService(LoggerProvider.class, openTelemetry.getLogsBridge(), props));
        regs.add(context.registerService(ContextPropagators.class, openTelemetry.getPropagators(), props));

        registrations.put(openTelemetry, regs);
        LOG.info("Registered provider services for " + name
            + (ranking != null ? " (service.ranking=" + ranking + ")" : ""));
    }

    void unbindOpenTelemetry(OpenTelemetry openTelemetry) {
        List<ServiceRegistration<?>> regs = registrations.remove(openTelemetry);
        if (regs != null) {
            for (ServiceRegistration<?> reg : regs) {
                try {
                    reg.unregister();
                } catch (IllegalStateException e) {
                    // Already unregistered
                }
            }
            LOG.info("Unregistered provider services for OpenTelemetry instance");
        }
    }

    @Deactivate
    void deactivate() {
        registrations.forEach((otel, regs) -> {
            for (ServiceRegistration<?> reg : regs) {
                try {
                    reg.unregister();
                } catch (IllegalStateException e) {
                    // Already unregistered
                }
            }
        });
        registrations.clear();
        LOG.info("OpenTelemetryProviderRegistration deactivated");
    }
}
