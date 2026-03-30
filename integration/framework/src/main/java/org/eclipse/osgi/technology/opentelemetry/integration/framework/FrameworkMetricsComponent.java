package org.eclipse.osgi.technology.opentelemetry.integration.framework;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Registers OSGi framework metrics as OpenTelemetry async gauges.
 * <p>
 * Metrics include:
 * <ul>
 *   <li>{@code osgi.bundle.count} — Total number of installed bundles</li>
 *   <li>{@code osgi.bundle.active} — Number of active bundles</li>
 *   <li>{@code osgi.service.count} — Number of registered services</li>
 *   <li>{@code osgi.bundle.states} — Bundle count per state (ACTIVE, RESOLVED, INSTALLED, etc.)</li>
 * </ul>
 * <p>
 * All gauges query the framework on every metric collection cycle, reflecting
 * the live state of the OSGi runtime.
 */
@Component(immediate = true)
public class FrameworkMetricsComponent {

    private static final Logger LOG = Logger.getLogger(FrameworkMetricsComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.integration.framework";

    @Reference(policy = ReferencePolicy.DYNAMIC)
    private volatile OpenTelemetry openTelemetry;

    private BundleContext bundleContext;
    private ObservableLongGauge bundleCountGauge;
    private ObservableLongGauge activeBundleGauge;
    private ObservableLongGauge serviceCountGauge;
    private ObservableLongGauge bundleStatesGauge;

    @Activate
    public void activate(BundleContext context) {
        this.bundleContext = context;

        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);

        bundleCountGauge = meter.gaugeBuilder("osgi.bundle.count")
            .setDescription("Total number of installed OSGi bundles")
            .setUnit("{bundles}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    measurement.record(bundleContext.getBundles().length);
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read bundle count", e);
                }
            });

        activeBundleGauge = meter.gaugeBuilder("osgi.bundle.active")
            .setDescription("Number of active OSGi bundles")
            .setUnit("{bundles}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    long count = 0;
                    for (Bundle b : bundleContext.getBundles()) {
                        if (b.getState() == Bundle.ACTIVE) {
                            count++;
                        }
                    }
                    measurement.record(count);
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read active bundle count", e);
                }
            });

        serviceCountGauge = meter.gaugeBuilder("osgi.service.count")
            .setDescription("Number of registered OSGi services")
            .setUnit("{services}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    ServiceReference<?>[] refs = bundleContext.getAllServiceReferences(null, null);
                    measurement.record(refs != null ? refs.length : 0);
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read service count", e);
                }
            });

        bundleStatesGauge = meter.gaugeBuilder("osgi.bundle.states")
            .setDescription("Number of OSGi bundles per state")
            .setUnit("{bundles}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    var stateCounts = new HashMap<String, Long>();
                    for (Bundle b : bundleContext.getBundles()) {
                        String stateName = BundleStateUtil.bundleStateToString(b.getState());
                        stateCounts.merge(stateName, 1L, Long::sum);
                    }
                    stateCounts.forEach((stateName, count) ->
                        measurement.record(count, Attributes.of(
                            AttributeKey.stringKey("osgi.bundle.state"), stateName
                        ))
                    );
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read bundle states", e);
                }
            });

        LOG.info("FrameworkMetricsComponent activated — OSGi framework metrics registered");
    }

    @Deactivate
    public void deactivate() {
        if (bundleCountGauge != null) bundleCountGauge.close();
        if (activeBundleGauge != null) activeBundleGauge.close();
        if (serviceCountGauge != null) serviceCountGauge.close();
        if (bundleStatesGauge != null) bundleStatesGauge.close();
        LOG.info("FrameworkMetricsComponent deactivated");
    }
}
