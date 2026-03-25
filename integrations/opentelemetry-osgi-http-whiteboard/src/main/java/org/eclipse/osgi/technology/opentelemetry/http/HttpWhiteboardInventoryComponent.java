package org.eclipse.osgi.technology.opentelemetry.http;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.http.runtime.HttpServiceRuntime;
import org.osgi.service.http.runtime.dto.ErrorPageDTO;
import org.osgi.service.http.runtime.dto.FilterDTO;
import org.osgi.service.http.runtime.dto.ListenerDTO;
import org.osgi.service.http.runtime.dto.ResourceDTO;
import org.osgi.service.http.runtime.dto.RuntimeDTO;
import org.osgi.service.http.runtime.dto.ServletContextDTO;
import org.osgi.service.http.runtime.dto.ServletDTO;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;

/**
 * Emits a structured inventory of the HTTP Whiteboard runtime as OpenTelemetry log records.
 * <p>
 * Dynamically tracks all available {@link HttpServiceRuntime} instances and emits
 * a full snapshot on each bind showing the hierarchy: servlet contexts → servlets,
 * filters, listeners, resources, error pages.
 * <p>
 * Log record attributes include:
 * <ul>
 *   <li>{@code osgi.inventory.type} — {@code snapshot} for the inventory dump</li>
 *   <li>{@code http.whiteboard.context.name} — servlet context name</li>
 *   <li>{@code http.whiteboard.context.path} — servlet context path</li>
 *   <li>{@code http.whiteboard.component.type} — type (servlet, filter, listener, resource, error_page)</li>
 *   <li>{@code http.whiteboard.component.name} — component name</li>
 *   <li>{@code http.whiteboard.url.patterns} — URL patterns</li>
 * </ul>
 */
@Component(immediate = true)
public class HttpWhiteboardInventoryComponent {

    private static final Logger LOG = Logger.getLogger(HttpWhiteboardInventoryComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.http.inventory";

    private final OpenTelemetry openTelemetry;
    private final ConcurrentHashMap<HttpServiceRuntime, Long> services = new ConcurrentHashMap<>();

    @Activate
    public HttpWhiteboardInventoryComponent(@Reference OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        LOG.info("HttpWhiteboardInventoryComponent activated");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindHttpServiceRuntime(HttpServiceRuntime runtime, Map<String, Object> properties) {
        long serviceId = (Long) properties.getOrDefault("service.id", 0L);
        services.put(runtime, serviceId);

        io.opentelemetry.api.logs.Logger otelLogger = openTelemetry.getLogsBridge().loggerBuilder(INSTRUMENTATION_SCOPE)
            .setInstrumentationVersion("0.1.0")
            .build();
        emitSnapshot(runtime, otelLogger);
        LOG.info("Bound HttpServiceRuntime (service.id=" + serviceId + ") — inventory emitted");
    }

    void unbindHttpServiceRuntime(HttpServiceRuntime runtime) {
        Long serviceId = services.remove(runtime);
        if (serviceId != null) {
            LOG.info("Unbound HttpServiceRuntime (service.id=" + serviceId + ")");
        }
    }

    @Deactivate
    void deactivate() {
        services.clear();
        LOG.info("HttpWhiteboardInventoryComponent deactivated");
    }

    private void emitSnapshot(HttpServiceRuntime runtime, io.opentelemetry.api.logs.Logger otelLogger) {
        try {
            RuntimeDTO dto = runtime.getRuntimeDTO();
            int contextCount = dto.servletContextDTOs != null ? dto.servletContextDTOs.length : 0;

            otelLogger.logRecordBuilder()
                .setSeverity(Severity.INFO)
                .setBody("HTTP Whiteboard inventory snapshot: " + contextCount + " servlet contexts")
                .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                .setAttribute(AttributeKey.longKey("http.whiteboard.context.count"), (long) contextCount)
                .emit();

            if (dto.servletContextDTOs != null) {
                for (ServletContextDTO ctx : dto.servletContextDTOs) {
                    emitContextInventory(ctx, otelLogger);
                }
            }

            emitFailedRegistrations(dto, otelLogger);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to emit HTTP Whiteboard inventory", e);
        }
    }

    private void emitContextInventory(ServletContextDTO ctx, io.opentelemetry.api.logs.Logger otelLogger) {
        int servletCount = ctx.servletDTOs != null ? ctx.servletDTOs.length : 0;
        int filterCount = ctx.filterDTOs != null ? ctx.filterDTOs.length : 0;
        int listenerCount = ctx.listenerDTOs != null ? ctx.listenerDTOs.length : 0;
        int resourceCount = ctx.resourceDTOs != null ? ctx.resourceDTOs.length : 0;
        int errorPageCount = ctx.errorPageDTOs != null ? ctx.errorPageDTOs.length : 0;

        otelLogger.logRecordBuilder()
            .setSeverity(Severity.INFO)
            .setBody("Servlet context: " + ctx.name + " [" + ctx.contextPath + "] — "
                + servletCount + " servlets, " + filterCount + " filters, "
                + listenerCount + " listeners, " + resourceCount + " resources, "
                + errorPageCount + " error pages")
            .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
            .setAttribute(AttributeKey.stringKey("http.whiteboard.context.name"), ctx.name != null ? ctx.name : "unknown")
            .setAttribute(AttributeKey.stringKey("http.whiteboard.context.path"), ctx.contextPath != null ? ctx.contextPath : "/")
            .setAttribute(AttributeKey.longKey("http.whiteboard.servlet.count"), (long) servletCount)
            .setAttribute(AttributeKey.longKey("http.whiteboard.filter.count"), (long) filterCount)
            .setAttribute(AttributeKey.longKey("http.whiteboard.listener.count"), (long) listenerCount)
            .setAttribute(AttributeKey.longKey("http.whiteboard.resource.count"), (long) resourceCount)
            .setAttribute(AttributeKey.longKey("http.whiteboard.error_page.count"), (long) errorPageCount)
            .emit();

        if (ctx.servletDTOs != null) {
            for (ServletDTO servlet : ctx.servletDTOs) {
                otelLogger.logRecordBuilder()
                    .setSeverity(Severity.DEBUG)
                    .setBody("Servlet: " + servlet.name + " → " + formatPatterns(servlet.patterns))
                    .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.context.name"), ctx.name)
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.component.type"), "servlet")
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.component.name"), servlet.name)
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.url.patterns"), formatPatterns(servlet.patterns))
                    .setAttribute(AttributeKey.booleanKey("http.whiteboard.async_supported"), servlet.asyncSupported)
                    .setAttribute(AttributeKey.longKey("http.whiteboard.service.id"), servlet.serviceId)
                    .emit();
            }
        }

        if (ctx.filterDTOs != null) {
            for (FilterDTO filter : ctx.filterDTOs) {
                otelLogger.logRecordBuilder()
                    .setSeverity(Severity.DEBUG)
                    .setBody("Filter: " + filter.name + " → " + formatPatterns(filter.patterns))
                    .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.context.name"), ctx.name)
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.component.type"), "filter")
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.component.name"), filter.name)
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.url.patterns"), formatPatterns(filter.patterns))
                    .setAttribute(AttributeKey.longKey("http.whiteboard.service.id"), filter.serviceId)
                    .emit();
            }
        }

        if (ctx.listenerDTOs != null) {
            for (ListenerDTO listener : ctx.listenerDTOs) {
                String types = listener.types != null ? String.join(", ", listener.types) : "unknown";
                otelLogger.logRecordBuilder()
                    .setSeverity(Severity.DEBUG)
                    .setBody("Listener: " + types)
                    .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.context.name"), ctx.name)
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.component.type"), "listener")
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.listener.types"), types)
                    .setAttribute(AttributeKey.longKey("http.whiteboard.service.id"), listener.serviceId)
                    .emit();
            }
        }

        if (ctx.resourceDTOs != null) {
            for (ResourceDTO resource : ctx.resourceDTOs) {
                otelLogger.logRecordBuilder()
                    .setSeverity(Severity.DEBUG)
                    .setBody("Resource: " + formatPatterns(resource.patterns) + " → " + resource.prefix)
                    .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.context.name"), ctx.name)
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.component.type"), "resource")
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.url.patterns"), formatPatterns(resource.patterns))
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.resource.prefix"), resource.prefix != null ? resource.prefix : "")
                    .setAttribute(AttributeKey.longKey("http.whiteboard.service.id"), resource.serviceId)
                    .emit();
            }
        }

        if (ctx.errorPageDTOs != null) {
            for (ErrorPageDTO errorPage : ctx.errorPageDTOs) {
                String exceptions = errorPage.exceptions != null ? String.join(", ", errorPage.exceptions) : "";
                String errorCodes = errorPage.errorCodes != null
                    ? Arrays.stream(errorPage.errorCodes).mapToObj(String::valueOf).collect(Collectors.joining(", "))
                    : "";
                otelLogger.logRecordBuilder()
                    .setSeverity(Severity.DEBUG)
                    .setBody("Error page: exceptions=[" + exceptions + "] codes=[" + errorCodes + "]")
                    .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.context.name"), ctx.name)
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.component.type"), "error_page")
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.error_page.exceptions"), exceptions)
                    .setAttribute(AttributeKey.stringKey("http.whiteboard.error_page.codes"), errorCodes)
                    .setAttribute(AttributeKey.longKey("http.whiteboard.service.id"), errorPage.serviceId)
                    .emit();
            }
        }
    }

    private static void emitFailedRegistrations(RuntimeDTO dto, io.opentelemetry.api.logs.Logger otelLogger) {
        long failedCount = 0;
        if (dto.failedServletDTOs != null) failedCount += dto.failedServletDTOs.length;
        if (dto.failedFilterDTOs != null) failedCount += dto.failedFilterDTOs.length;
        if (dto.failedListenerDTOs != null) failedCount += dto.failedListenerDTOs.length;
        if (dto.failedResourceDTOs != null) failedCount += dto.failedResourceDTOs.length;
        if (dto.failedErrorPageDTOs != null) failedCount += dto.failedErrorPageDTOs.length;
        if (dto.failedServletContextDTOs != null) failedCount += dto.failedServletContextDTOs.length;

        if (failedCount > 0) {
            otelLogger.logRecordBuilder()
                .setSeverity(Severity.WARN)
                .setBody("HTTP Whiteboard has " + failedCount + " failed registrations")
                .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                .setAttribute(AttributeKey.longKey("http.whiteboard.failed.total"), failedCount)
                .emit();
        }
    }

    private static String formatPatterns(String[] patterns) {
        if (patterns == null || patterns.length == 0) return "[]";
        return String.join(", ", patterns);
    }
}
