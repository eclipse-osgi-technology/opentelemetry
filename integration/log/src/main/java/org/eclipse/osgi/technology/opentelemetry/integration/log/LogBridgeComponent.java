package org.eclipse.osgi.technology.opentelemetry.integration.log;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;

/**
 * Bridges the OSGi Log Service to OpenTelemetry by registering a {@link LogListener}
 * with the {@link LogReaderService} and forwarding every {@link LogEntry} as an
 * OpenTelemetry log record.
 * <p>
 * Each log entry is enriched with OSGi-specific attributes:
 * <ul>
 *   <li>{@code osgi.log.logger_name} — the logger name from the entry</li>
 *   <li>{@code osgi.log.bundle.symbolic_name} — the originating bundle</li>
 *   <li>{@code osgi.log.bundle.id} — the originating bundle's id</li>
 *   <li>{@code osgi.log.bundle.version} — the originating bundle's version</li>
 *   <li>{@code osgi.log.sequence} — the monotonic sequence number</li>
 *   <li>{@code osgi.log.thread} — thread information</li>
 *   <li>{@code osgi.log.service.objectClass} — service interface (if associated)</li>
 *   <li>{@code exception.type} / {@code exception.message} — exception info (if present)</li>
 * </ul>
 * <p>
 * The OSGi {@link LogLevel} is mapped to OpenTelemetry {@link Severity} values.
 */
@Component(immediate = true)
public class LogBridgeComponent implements LogListener {

    private static final Logger LOG = Logger.getLogger(LogBridgeComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.integration.log";

    @Reference(policy = ReferencePolicy.DYNAMIC)
    private volatile OpenTelemetry openTelemetry;

    private final ConcurrentHashMap<LogReaderService, Long> services = new ConcurrentHashMap<>();

    @Activate
    public void activate() {
        LOG.info("LogBridgeComponent activated — forwarding OSGi Log Service to OpenTelemetry");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindLogReaderService(LogReaderService service, Map<String, Object> properties) {
        long serviceId = (Long) properties.get(Constants.SERVICE_ID);
        services.put(service, serviceId);
        service.addLogListener(this);
        LOG.info("Bound LogReaderService service.id=" + serviceId);
    }

    void unbindLogReaderService(LogReaderService service) {
        Long serviceId = services.remove(service);
        try {
            service.removeLogListener(this);
        } catch (Exception e) {
            // service may already be gone
        }
        if (serviceId != null) {
            LOG.info("Unbound LogReaderService service.id=" + serviceId);
        }
    }

    @Deactivate
    public void deactivate() {
        services.forEach((service, serviceId) -> {
            try {
                service.removeLogListener(this);
            } catch (Exception e) {
                // service may already be gone
            }
        });
        services.clear();
        LOG.info("LogBridgeComponent deactivated");
    }

    @Override
    public void logged(LogEntry entry) {
        try {
            forwardLogEntry(entry);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Error forwarding log entry to OpenTelemetry", e);
        }
    }

    private void forwardLogEntry(LogEntry entry) {
        io.opentelemetry.api.logs.Logger otelLogger = openTelemetry.getLogsBridge()
            .loggerBuilder(INSTRUMENTATION_SCOPE)
            .setInstrumentationVersion("0.1.0")
            .build();
        var builder = otelLogger.logRecordBuilder()
            .setSeverity(mapLogLevel(entry.getLogLevel()))
            .setBody(entry.getMessage() != null ? entry.getMessage() : "")
            .setTimestamp(Instant.ofEpochMilli(entry.getTime()));

        // Logger name
        String loggerName = entry.getLoggerName();
        if (loggerName != null) {
            builder.setAttribute(AttributeKey.stringKey("osgi.log.logger_name"), loggerName);
        }

        // Originating bundle
        Bundle bundle = entry.getBundle();
        if (bundle != null) {
            builder.setAttribute(AttributeKey.stringKey("osgi.log.bundle.symbolic_name"),
                bundle.getSymbolicName() != null ? bundle.getSymbolicName() : "null");
            builder.setAttribute(AttributeKey.longKey("osgi.log.bundle.id"), bundle.getBundleId());
            builder.setAttribute(AttributeKey.stringKey("osgi.log.bundle.version"),
                bundle.getVersion().toString());
        }

        // Sequence number
        builder.setAttribute(AttributeKey.longKey("osgi.log.sequence"), entry.getSequence());

        // Thread info
        String threadInfo = entry.getThreadInfo();
        if (threadInfo != null) {
            builder.setAttribute(AttributeKey.stringKey("osgi.log.thread"), threadInfo);
        }

        // Associated service reference
        ServiceReference<?> ref = entry.getServiceReference();
        if (ref != null) {
            Object objectClass = ref.getProperty("objectClass");
            if (objectClass instanceof String[] interfaces) {
                builder.setAttribute(AttributeKey.stringKey("osgi.log.service.objectClass"),
                    String.join(", ", interfaces));
            }
            Object serviceId = ref.getProperty("service.id");
            if (serviceId instanceof Long id) {
                builder.setAttribute(AttributeKey.longKey("osgi.log.service.id"), id);
            }
        }

        // Exception
        Throwable exception = entry.getException();
        if (exception != null) {
            builder.setAttribute(AttributeKey.stringKey("exception.type"),
                exception.getClass().getName());
            if (exception.getMessage() != null) {
                builder.setAttribute(AttributeKey.stringKey("exception.message"),
                    exception.getMessage());
            }
        }

        // Source location (if available)
        StackTraceElement location = entry.getLocation();
        if (location != null) {
            builder.setAttribute(AttributeKey.stringKey("code.filepath"), location.getFileName());
            builder.setAttribute(AttributeKey.stringKey("code.function"), location.getMethodName());
            builder.setAttribute(AttributeKey.longKey("code.lineno"), (long) location.getLineNumber());
            builder.setAttribute(AttributeKey.stringKey("code.namespace"), location.getClassName());
        }

        builder.emit();
    }

    /**
     * Maps OSGi {@link LogLevel} to OpenTelemetry {@link Severity}.
     */
    static Severity mapLogLevel(LogLevel level) {
        if (level == null) {
            return Severity.UNDEFINED_SEVERITY_NUMBER;
        }
        return switch (level) {
            case AUDIT -> Severity.INFO;
            case ERROR -> Severity.ERROR;
            case WARN -> Severity.WARN;
            case INFO -> Severity.INFO;
            case DEBUG -> Severity.DEBUG;
            case TRACE -> Severity.TRACE;
        };
    }
}
