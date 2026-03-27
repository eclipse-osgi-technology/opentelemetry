package org.eclipse.osgi.technology.opentelemetry.weaving.hook;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;

/**
 * Proxy for the {@link OpenTelemetry} service that gracefully handles the
 * service not being available.
 * <p>
 * When the OpenTelemetry service is not registered (or not yet available),
 * this proxy returns noop implementations. When the service arrives, the
 * proxy transparently switches to the real implementation. When the service
 * departs, it switches back to noop.
 * <p>
 * This design ensures woven code can always call tracing/metrics methods
 * without null checks or service availability concerns.
 */
public class OpenTelemetryProxy {

    private static final Logger LOG = Logger.getLogger(OpenTelemetryProxy.class.getName());

    private final AtomicReference<OpenTelemetry> delegate =
            new AtomicReference<>(OpenTelemetry.noop());

    private final ServiceTracker<OpenTelemetry, OpenTelemetry> tracker;

    OpenTelemetryProxy(BundleContext context) {
        tracker = new ServiceTracker<>(context, OpenTelemetry.class,
                new ServiceTrackerCustomizer<>() {

            @Override
            public OpenTelemetry addingService(ServiceReference<OpenTelemetry> reference) {
                OpenTelemetry service = context.getService(reference);
                if (service != null) {
                    delegate.set(service);
                    LOG.info("OpenTelemetry service bound to weaving proxy");
                }
                return service;
            }

            @Override
            public void modifiedService(ServiceReference<OpenTelemetry> reference,
                    OpenTelemetry service) {
                delegate.set(service);
            }

            @Override
            public void removedService(ServiceReference<OpenTelemetry> reference,
                    OpenTelemetry service) {
                delegate.set(OpenTelemetry.noop());
                context.ungetService(reference);
                LOG.info("OpenTelemetry service unbound from weaving proxy, using noop");
            }
        });
    }

    void open() {
        tracker.open();
        LOG.log(Level.FINE, "OpenTelemetry service tracker opened");
    }

    void close() {
        tracker.close();
        delegate.set(OpenTelemetry.noop());
    }

    /**
     * Returns a {@link Tracer} for the given instrumentation scope.
     * Returns a noop tracer when the OpenTelemetry service is not available.
     */
    public Tracer getTracer(String instrumentationScopeName) {
        return delegate.get().getTracer(instrumentationScopeName);
    }

    /**
     * Returns a {@link Meter} for the given instrumentation scope.
     * Returns a noop meter when the OpenTelemetry service is not available.
     */
    public Meter getMeter(String instrumentationScopeName) {
        return delegate.get().getMeter(instrumentationScopeName);
    }

    /**
     * Returns the current {@link OpenTelemetry} instance (real or noop).
     */
    public OpenTelemetry get() {
        return delegate.get();
    }
}
