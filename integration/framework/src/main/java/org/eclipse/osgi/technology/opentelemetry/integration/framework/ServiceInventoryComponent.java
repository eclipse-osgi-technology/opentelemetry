package org.eclipse.osgi.technology.opentelemetry.integration.framework;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;

/**
 * Maintains a live view of the OSGi service registry as OpenTelemetry log records.
 * <p>
 * On activation, emits a full snapshot of all registered services.
 * Thereafter, listens for {@link ServiceEvent}s and emits a log record for every
 * registration, unregistration, and property modification.
 * <p>
 * Emitted log records include:
 * <ul>
 *   <li>{@code osgi.service.id} — the service id</li>
 *   <li>{@code osgi.service.interfaces} — the registered interface names</li>
 *   <li>{@code osgi.service.scope} — singleton, bundle, or prototype</li>
 *   <li>{@code osgi.service.ranking} — the service ranking</li>
 *   <li>{@code osgi.service.provider.bundle} — the providing bundle's symbolic name</li>
 *   <li>{@code osgi.service.provider.bundle_id} — the providing bundle's id</li>
 *   <li>{@code osgi.service.using_bundles} — symbolic names of bundles using this service</li>
 *   <li>{@code osgi.service.event} — the event type (REGISTERED, UNREGISTERING, MODIFIED)</li>
 *   <li>{@code osgi.inventory.type} — {@code snapshot} for the initial dump, {@code change} for live events</li>
 * </ul>
 */
@Component(immediate = true)
public class ServiceInventoryComponent implements ServiceListener {

    private static final Logger LOG = Logger.getLogger(ServiceInventoryComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.integration.framework";

    @Reference(policy = ReferencePolicy.DYNAMIC)
    private volatile OpenTelemetry openTelemetry;

    private BundleContext bundleContext;
    private io.opentelemetry.api.logs.Logger otelLogger;

    @Activate
    public void activate(BundleContext context) {
        this.bundleContext = context;
        this.otelLogger = openTelemetry.getLogsBridge().loggerBuilder(INSTRUMENTATION_SCOPE)
            .setInstrumentationVersion("0.1.0")
            .build();

        context.addServiceListener(this);

        emitSnapshot(context);
        LOG.info("ServiceInventoryComponent activated — tracking service registry");
    }

    @Deactivate
    public void deactivate() {
        bundleContext.removeServiceListener(this);
        LOG.info("ServiceInventoryComponent deactivated");
    }

    @Override
    public void serviceChanged(ServiceEvent event) {
        try {
            ServiceReference<?> ref = event.getServiceReference();
            String eventType = FrameworkEventComponent.serviceEventTypeToString(event.getType());

            Severity severity = switch (event.getType()) {
                case ServiceEvent.REGISTERED -> Severity.INFO;
                case ServiceEvent.UNREGISTERING -> Severity.WARN;
                default -> Severity.DEBUG;
            };

            var builder = otelLogger.logRecordBuilder()
                .setSeverity(severity)
                .setBody("Service " + eventType.toLowerCase() + ": " + getInterfaces(ref))
                .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "change")
                .setAttribute(AttributeKey.stringKey("osgi.service.event"), eventType);

            populateServiceAttributes(builder, ref);
            builder.emit();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Error logging service change", e);
        }
    }

    private void emitSnapshot(BundleContext context) {
        try {
            ServiceReference<?>[] refs = context.getAllServiceReferences(null, null);
            int count = refs != null ? refs.length : 0;

            otelLogger.logRecordBuilder()
                .setSeverity(Severity.INFO)
                .setBody("OSGi service inventory snapshot: " + count + " services")
                .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                .setAttribute(AttributeKey.longKey("osgi.service.total"), (long) count)
                .emit();

            if (refs != null) {
                for (ServiceReference<?> ref : refs) {
                    var builder = otelLogger.logRecordBuilder()
                        .setSeverity(Severity.DEBUG)
                        .setBody("Service: " + getInterfaces(ref))
                        .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot");

                    populateServiceAttributes(builder, ref);
                    builder.emit();
                }
            }

            LOG.info("Emitted service inventory snapshot: " + count + " services");
        } catch (InvalidSyntaxException e) {
            LOG.log(Level.WARNING, "Failed to emit service inventory snapshot", e);
        }
    }

    private void populateServiceAttributes(
            io.opentelemetry.api.logs.LogRecordBuilder builder,
            ServiceReference<?> ref) {

        Object serviceId = ref.getProperty("service.id");
        if (serviceId instanceof Long id) {
            builder.setAttribute(AttributeKey.longKey("osgi.service.id"), id);
        }

        builder.setAttribute(AttributeKey.stringKey("osgi.service.interfaces"), getInterfaces(ref));

        Object scope = ref.getProperty("service.scope");
        builder.setAttribute(AttributeKey.stringKey("osgi.service.scope"),
            scope != null ? scope.toString() : "singleton");

        Object ranking = ref.getProperty("service.ranking");
        if (ranking instanceof Integer rank) {
            builder.setAttribute(AttributeKey.longKey("osgi.service.ranking"), rank.longValue());
        }

        Bundle provider = ref.getBundle();
        if (provider != null) {
            builder.setAttribute(AttributeKey.stringKey("osgi.service.provider.bundle"),
                provider.getSymbolicName() != null ? provider.getSymbolicName() : "null");
            builder.setAttribute(AttributeKey.longKey("osgi.service.provider.bundle_id"),
                provider.getBundleId());
        }

        Bundle[] usingBundles = ref.getUsingBundles();
        if (usingBundles != null && usingBundles.length > 0) {
            String using = Arrays.stream(usingBundles)
                .map(b -> b.getSymbolicName() != null ? b.getSymbolicName() : String.valueOf(b.getBundleId()))
                .collect(Collectors.joining(", "));
            builder.setAttribute(AttributeKey.stringKey("osgi.service.using_bundles"), using);
            builder.setAttribute(AttributeKey.longKey("osgi.service.using_count"),
                (long) usingBundles.length);
        }
    }

    private static String getInterfaces(ServiceReference<?> ref) {
        Object objectClass = ref.getProperty("objectClass");
        if (objectClass instanceof String[] interfaces) {
            return String.join(", ", interfaces);
        }
        return "unknown";
    }
}
