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
import io.opentelemetry.api.logs.Severity;

/**
 * Emits a structured inventory of the JAX-RS Whiteboard runtime as OpenTelemetry log records.
 * <p>
 * Dynamically tracks all available {@link JakartarsServiceRuntime} instances and emits
 * a full snapshot on each bind showing the hierarchy: applications → resources →
 * resource methods, and extensions.
 * <p>
 * Log record attributes include:
 * <ul>
 *   <li>{@code osgi.inventory.type} — {@code snapshot} for the inventory dump</li>
 *   <li>{@code jaxrs.application.name} — application name</li>
 *   <li>{@code jaxrs.application.base} — application base path</li>
 *   <li>{@code jaxrs.component.type} — type (resource, extension, resource_method)</li>
 *   <li>{@code jaxrs.resource.method} — HTTP method (GET, POST, etc.)</li>
 *   <li>{@code jaxrs.resource.path} — resource path</li>
 * </ul>
 */
@Component(immediate = true)
public class JaxrsWhiteboardInventoryComponent {

    private static final Logger LOG = Logger.getLogger(JaxrsWhiteboardInventoryComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.jaxrs.inventory";

    @Reference(policy = ReferencePolicy.DYNAMIC)
    private volatile OpenTelemetry openTelemetry;

    private final ConcurrentHashMap<JakartarsServiceRuntime, Long> services = new ConcurrentHashMap<>();

    @Activate
    public void activate() {
        LOG.info("JaxrsWhiteboardInventoryComponent activated");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindJakartarsServiceRuntime(JakartarsServiceRuntime runtime, Map<String, Object> properties) {
        long serviceId = (Long) properties.getOrDefault("service.id", 0L);
        services.put(runtime, serviceId);

        io.opentelemetry.api.logs.Logger otelLogger = openTelemetry.getLogsBridge().loggerBuilder(INSTRUMENTATION_SCOPE)
            .setInstrumentationVersion("0.1.0")
            .build();
        emitSnapshot(runtime, otelLogger);
        LOG.info("Bound JakartarsServiceRuntime (service.id=" + serviceId + ") — inventory emitted");
    }

    void unbindJakartarsServiceRuntime(JakartarsServiceRuntime runtime) {
        Long serviceId = services.remove(runtime);
        if (serviceId != null) {
            LOG.info("Unbound JakartarsServiceRuntime (service.id=" + serviceId + ")");
        }
    }

    @Deactivate
    void deactivate() {
        services.clear();
        LOG.info("JaxrsWhiteboardInventoryComponent deactivated");
    }

    private void emitSnapshot(JakartarsServiceRuntime runtime, io.opentelemetry.api.logs.Logger otelLogger) {
        try {
            RuntimeDTO dto = runtime.getRuntimeDTO();
            int appCount = dto.applicationDTOs != null ? dto.applicationDTOs.length : 0;
            if (dto.defaultApplication != null) appCount++;

            otelLogger.logRecordBuilder()
                .setSeverity(Severity.INFO)
                .setBody("JAX-RS Whiteboard inventory snapshot: " + appCount + " applications")
                .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                .setAttribute(AttributeKey.longKey("jaxrs.application.count"), (long) appCount)
                .emit();

            if (dto.defaultApplication != null) {
                emitApplicationInventory(dto.defaultApplication, true, otelLogger);
            }
            if (dto.applicationDTOs != null) {
                for (ApplicationDTO app : dto.applicationDTOs) {
                    emitApplicationInventory(app, false, otelLogger);
                }
            }

            emitFailedRegistrations(dto, otelLogger);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to emit JAX-RS Whiteboard inventory", e);
        }
    }

    private void emitApplicationInventory(BaseApplicationDTO app, boolean isDefault,
            io.opentelemetry.api.logs.Logger otelLogger) {
        int resourceCount = app.resourceDTOs != null ? app.resourceDTOs.length : 0;
        int extensionCount = app.extensionDTOs != null ? app.extensionDTOs.length : 0;
        long methodCount = 0;
        if (app.resourceDTOs != null) {
            for (ResourceDTO resource : app.resourceDTOs) {
                if (resource.resourceMethods != null) {
                    methodCount += resource.resourceMethods.length;
                }
            }
        }

        String appName = app.name != null ? app.name : (isDefault ? ".default" : "unknown");
        String appBase = app.base != null ? app.base : "/";

        otelLogger.logRecordBuilder()
            .setSeverity(Severity.INFO)
            .setBody("JAX-RS application: " + appName + " [" + appBase + "] — "
                + resourceCount + " resources, " + extensionCount + " extensions, "
                + methodCount + " methods" + (isDefault ? " (default)" : ""))
            .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
            .setAttribute(AttributeKey.stringKey("jaxrs.application.name"), appName)
            .setAttribute(AttributeKey.stringKey("jaxrs.application.base"), appBase)
            .setAttribute(AttributeKey.booleanKey("jaxrs.application.default"), isDefault)
            .setAttribute(AttributeKey.longKey("jaxrs.resource.count"), (long) resourceCount)
            .setAttribute(AttributeKey.longKey("jaxrs.extension.count"), (long) extensionCount)
            .setAttribute(AttributeKey.longKey("jaxrs.resource_method.count"), methodCount)
            .emit();

        if (app.resourceDTOs != null) {
            for (ResourceDTO resource : app.resourceDTOs) {
                emitResourceInventory(appName, appBase, resource, otelLogger);
            }
        }

        if (app.extensionDTOs != null) {
            for (ExtensionDTO extension : app.extensionDTOs) {
                String types = extension.extensionTypes != null
                    ? String.join(", ", extension.extensionTypes) : "unknown";
                otelLogger.logRecordBuilder()
                    .setSeverity(Severity.DEBUG)
                    .setBody("Extension: " + extension.name + " types=[" + types + "]")
                    .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                    .setAttribute(AttributeKey.stringKey("jaxrs.application.name"), appName)
                    .setAttribute(AttributeKey.stringKey("jaxrs.component.type"), "extension")
                    .setAttribute(AttributeKey.stringKey("jaxrs.extension.name"), extension.name != null ? extension.name : "unknown")
                    .setAttribute(AttributeKey.stringKey("jaxrs.extension.types"), types)
                    .setAttribute(AttributeKey.longKey("jaxrs.service.id"), extension.serviceId)
                    .emit();
            }
        }
    }

    private static void emitResourceInventory(String appName, String appBase, ResourceDTO resource,
            io.opentelemetry.api.logs.Logger otelLogger) {
        int methodCount = resource.resourceMethods != null ? resource.resourceMethods.length : 0;

        otelLogger.logRecordBuilder()
            .setSeverity(Severity.DEBUG)
            .setBody("Resource: " + resource.name + " — " + methodCount + " methods")
            .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
            .setAttribute(AttributeKey.stringKey("jaxrs.application.name"), appName)
            .setAttribute(AttributeKey.stringKey("jaxrs.component.type"), "resource")
            .setAttribute(AttributeKey.stringKey("jaxrs.resource.name"), resource.name != null ? resource.name : "unknown")
            .setAttribute(AttributeKey.longKey("jaxrs.service.id"), resource.serviceId)
            .setAttribute(AttributeKey.longKey("jaxrs.resource_method.count"), (long) methodCount)
            .emit();

        if (resource.resourceMethods != null) {
            for (ResourceMethodInfoDTO method : resource.resourceMethods) {
                String httpMethod = method.method != null ? method.method : "*";
                String path = method.path != null ? method.path : appBase;
                otelLogger.logRecordBuilder()
                    .setSeverity(Severity.DEBUG)
                    .setBody("  " + httpMethod + " " + path)
                    .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                    .setAttribute(AttributeKey.stringKey("jaxrs.application.name"), appName)
                    .setAttribute(AttributeKey.stringKey("jaxrs.component.type"), "resource_method")
                    .setAttribute(AttributeKey.stringKey("jaxrs.resource.method"), httpMethod)
                    .setAttribute(AttributeKey.stringKey("jaxrs.resource.path"), path)
                    .setAttribute(AttributeKey.stringKey("jaxrs.resource.name"), resource.name != null ? resource.name : "unknown")
                    .emit();
            }
        }
    }

    private static void emitFailedRegistrations(RuntimeDTO dto, io.opentelemetry.api.logs.Logger otelLogger) {
        long failedCount = 0;
        if (dto.failedApplicationDTOs != null) failedCount += dto.failedApplicationDTOs.length;
        if (dto.failedResourceDTOs != null) failedCount += dto.failedResourceDTOs.length;
        if (dto.failedExtensionDTOs != null) failedCount += dto.failedExtensionDTOs.length;

        if (failedCount > 0) {
            otelLogger.logRecordBuilder()
                .setSeverity(Severity.WARN)
                .setBody("JAX-RS Whiteboard has " + failedCount + " failed registrations")
                .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                .setAttribute(AttributeKey.longKey("jaxrs.failed.total"), failedCount)
                .emit();
        }
    }
}
