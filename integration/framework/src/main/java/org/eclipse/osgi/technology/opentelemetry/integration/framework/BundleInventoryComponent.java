package org.eclipse.osgi.technology.opentelemetry.integration.framework;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;

/**
 * Maintains a live view of the OSGi bundle inventory as OpenTelemetry log records.
 * <p>
 * On activation, emits a full snapshot of all installed bundles.
 * Thereafter, listens for {@link BundleEvent}s and emits a log record for every
 * state change — installs, starts, stops, updates, uninstalls, and resolution changes.
 * <p>
 * Uses {@link SynchronousBundleListener} to capture events before the framework
 * proceeds, ensuring the logged state is accurate at the time of the event.
 * <p>
 * Emitted log records include:
 * <ul>
 *   <li>{@code osgi.bundle.id}, {@code osgi.bundle.symbolic_name}, {@code osgi.bundle.version}</li>
 *   <li>{@code osgi.bundle.state} — the bundle state after the event</li>
 *   <li>{@code osgi.bundle.event} — the event type (INSTALLED, STARTED, etc.)</li>
 *   <li>{@code osgi.bundle.location} — the install location</li>
 *   <li>{@code osgi.inventory.type} — {@code snapshot} for the initial dump, {@code change} for live events</li>
 * </ul>
 */
@Component(immediate = true)
public class BundleInventoryComponent implements SynchronousBundleListener {

    private static final Logger LOG = Logger.getLogger(BundleInventoryComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.framework.inventory.bundle";

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

        context.addBundleListener(this);

        emitSnapshot(context);
        LOG.info("BundleInventoryComponent activated — tracking bundle lifecycle");
    }

    @Deactivate
    public void deactivate() {
        bundleContext.removeBundleListener(this);
        LOG.info("BundleInventoryComponent deactivated");
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        try {
            Bundle bundle = event.getBundle();
            String eventType = FrameworkEventComponent.bundleEventTypeToString(event.getType());

            Severity severity = switch (event.getType()) {
                case BundleEvent.UNINSTALLED -> Severity.WARN;
                case BundleEvent.INSTALLED, BundleEvent.STARTED, BundleEvent.STOPPED -> Severity.INFO;
                default -> Severity.DEBUG;
            };

            otelLogger.logRecordBuilder()
                .setSeverity(severity)
                .setBody("Bundle " + eventType.toLowerCase() + ": "
                    + bundle.getSymbolicName() + " " + bundle.getVersion())
                .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "change")
                .setAttribute(AttributeKey.stringKey("osgi.bundle.event"), eventType)
                .setAttribute(AttributeKey.longKey("osgi.bundle.id"), bundle.getBundleId())
                .setAttribute(AttributeKey.stringKey("osgi.bundle.symbolic_name"),
                    bundle.getSymbolicName() != null ? bundle.getSymbolicName() : "null")
                .setAttribute(AttributeKey.stringKey("osgi.bundle.version"),
                    bundle.getVersion().toString())
                .setAttribute(AttributeKey.stringKey("osgi.bundle.state"),
                    BundleStateUtil.bundleStateToString(bundle.getState()))
                .setAttribute(AttributeKey.stringKey("osgi.bundle.location"), bundle.getLocation())
                .emit();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Error logging bundle change", e);
        }
    }

    private void emitSnapshot(BundleContext context) {
        try {
            Bundle[] bundles = context.getBundles();
            long activeCount = 0;
            List<BundleInfo> bundleInfos = new ArrayList<>(bundles.length);

            for (Bundle bundle : bundles) {
                int state = bundle.getState();
                if (state == Bundle.ACTIVE) {
                    activeCount++;
                }
                bundleInfos.add(new BundleInfo(
                    bundle.getBundleId(),
                    bundle.getSymbolicName(),
                    bundle.getVersion().toString(),
                    state,
                    BundleStateUtil.bundleStateToString(state),
                    bundle.getLocation()
                ));
            }

            String vendor = context.getProperty("org.osgi.framework.vendor");
            String version = context.getProperty("org.osgi.framework.version");

            otelLogger.logRecordBuilder()
                .setSeverity(Severity.INFO)
                .setBody("OSGi bundle inventory snapshot: " + bundles.length
                    + " bundles, " + activeCount + " active")
                .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                .setAttribute(AttributeKey.stringKey("osgi.framework.vendor"),
                    vendor != null ? vendor : "unknown")
                .setAttribute(AttributeKey.stringKey("osgi.framework.version"),
                    version != null ? version : "unknown")
                .setAttribute(AttributeKey.longKey("osgi.bundle.total"), (long) bundles.length)
                .setAttribute(AttributeKey.longKey("osgi.bundle.active"), activeCount)
                .emit();

            for (BundleInfo info : bundleInfos) {
                otelLogger.logRecordBuilder()
                    .setSeverity(Severity.DEBUG)
                    .setBody("Bundle: " + info.symbolicName() + " [" + info.stateName() + "]")
                    .setAttribute(AttributeKey.stringKey("osgi.inventory.type"), "snapshot")
                    .setAttribute(AttributeKey.longKey("osgi.bundle.id"), info.bundleId())
                    .setAttribute(AttributeKey.stringKey("osgi.bundle.symbolic_name"),
                        info.symbolicName())
                    .setAttribute(AttributeKey.stringKey("osgi.bundle.version"), info.version())
                    .setAttribute(AttributeKey.stringKey("osgi.bundle.state"), info.stateName())
                    .setAttribute(AttributeKey.stringKey("osgi.bundle.location"), info.location())
                    .emit();
            }

            LOG.info("Emitted bundle inventory snapshot: " + bundles.length + " bundles");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to emit bundle inventory snapshot", e);
        }
    }
}
