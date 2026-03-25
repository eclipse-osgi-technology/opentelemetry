package org.eclipse.osgi.technology.opentelemetry.cm;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Listens for OSGi Configuration Admin events and creates OpenTelemetry trace
 * spans for each configuration change.
 * <p>
 * Creates spans named {@code osgi.cm.updated}, {@code osgi.cm.deleted}, or
 * {@code osgi.cm.location_changed} with attributes for the PID, factory PID,
 * and event type.
 */
@Component(immediate = true, service = ConfigurationListener.class)
public class ConfigAdminEventComponent implements ConfigurationListener {

    private static final Logger LOG = Logger.getLogger(ConfigAdminEventComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.cm.events";

    @Reference
    private OpenTelemetry openTelemetry;

    @Activate
    public void activate() {
        LOG.info("ConfigAdminEventComponent activated — tracing configuration events");
    }

    @Deactivate
    public void deactivate() {
        LOG.info("ConfigAdminEventComponent deactivated");
    }

    @Override
    public void configurationEvent(ConfigurationEvent event) {
        try {
            Tracer tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE, "0.1.0");
            String eventType = ConfigAdminMetricsComponent.eventTypeToString(event.getType());
            String spanName = switch (event.getType()) {
                case ConfigurationEvent.CM_UPDATED -> "osgi.cm.updated";
                case ConfigurationEvent.CM_DELETED -> "osgi.cm.deleted";
                case ConfigurationEvent.CM_LOCATION_CHANGED -> "osgi.cm.location_changed";
                default -> "osgi.cm.event";
            };

            Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AttributeKey.stringKey("cm.pid"), event.getPid())
                .setAttribute(AttributeKey.stringKey("cm.event.type"), eventType)
                .startSpan();

            try (Scope ignored = span.makeCurrent()) {
                if (event.getFactoryPid() != null) {
                    span.setAttribute("cm.factory.pid", event.getFactoryPid());
                }

                if (event.getReference() != null) {
                    span.setAttribute("cm.service.bundle",
                        event.getReference().getBundle().getSymbolicName());
                }

                if (event.getType() == ConfigurationEvent.CM_DELETED) {
                    span.addEvent("Configuration deleted: " + event.getPid());
                } else if (event.getType() == ConfigurationEvent.CM_UPDATED) {
                    span.addEvent("Configuration updated: " + event.getPid());
                }
            } finally {
                span.end();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to trace configuration event", e);
        }
    }
}
