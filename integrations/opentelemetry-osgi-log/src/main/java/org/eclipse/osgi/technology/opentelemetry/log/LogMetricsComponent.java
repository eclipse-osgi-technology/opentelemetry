package org.eclipse.osgi.technology.opentelemetry.log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Constants;
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
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

/**
 * Exposes OSGi Log Service statistics as OpenTelemetry metrics.
 * <p>
 * Listens to the {@link LogReaderService} and maintains counters for log entries
 * grouped by level and originating bundle. Metrics include:
 * <ul>
 *   <li>{@code osgi.log.entries} — counter of log entries, with {@code log.level} attribute</li>
 *   <li>{@code osgi.log.errors} — counter of ERROR-level entries, with {@code bundle.symbolic_name} attribute</li>
 * </ul>
 */
@Component(immediate = true)
public class LogMetricsComponent implements LogListener {

    private static final Logger LOG = Logger.getLogger(LogMetricsComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.log.metrics";

    private final LongCounter logEntryCounter;
    private final LongCounter errorCounter;
    private final ConcurrentHashMap<LogReaderService, Long> services = new ConcurrentHashMap<>();

    @Activate
    public LogMetricsComponent(@Reference OpenTelemetry openTelemetry) {
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);
        this.logEntryCounter = meter.counterBuilder("osgi.log.entries")
            .setDescription("Number of OSGi log entries by level")
            .setUnit("{entries}")
            .build();
        this.errorCounter = meter.counterBuilder("osgi.log.errors")
            .setDescription("Number of OSGi ERROR log entries by bundle")
            .setUnit("{entries}")
            .build();
        LOG.info("LogMetricsComponent activated — counting OSGi log entries");
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
        LOG.info("LogMetricsComponent deactivated");
    }

    @Override
    public void logged(LogEntry entry) {
        try {
            LogLevel level = entry.getLogLevel();
            String levelName = level != null ? level.name() : "UNKNOWN";

            logEntryCounter.add(1, Attributes.of(
                AttributeKey.stringKey("log.level"), levelName
            ));

            if (level == LogLevel.ERROR) {
                String bundleName = "unknown";
                if (entry.getBundle() != null && entry.getBundle().getSymbolicName() != null) {
                    bundleName = entry.getBundle().getSymbolicName();
                }
                errorCounter.add(1, Attributes.of(
                    AttributeKey.stringKey("bundle.symbolic_name"), bundleName
                ));
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Error counting log entry", e);
        }
    }
}
