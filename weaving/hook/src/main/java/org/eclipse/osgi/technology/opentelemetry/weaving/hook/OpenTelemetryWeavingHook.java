package org.eclipse.osgi.technology.opentelemetry.weaving.hook;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;

/**
 * OSGi {@link WeavingHook} implementation that delegates class transformation
 * to discovered {@link Weaver} implementations.
 * <p>
 * For each class being loaded, the hook iterates over all registered weavers
 * and lets matching ones transform the bytecode. The weaving hook skips
 * classes from the weaving bundle itself and from the OSGi framework to avoid
 * recursive instrumentation.
 */
class OpenTelemetryWeavingHook implements WeavingHook {

    private static final Logger LOG = Logger.getLogger(OpenTelemetryWeavingHook.class.getName());

    private final WeaverRegistry registry;
    private final OpenTelemetryProxy telemetry;
    private final String ownBundleSymbolicName;

    OpenTelemetryWeavingHook(WeaverRegistry registry, OpenTelemetryProxy telemetry,
            String ownBundleSymbolicName) {
        this.registry = registry;
        this.telemetry = telemetry;
        this.ownBundleSymbolicName = ownBundleSymbolicName;
    }

    @Override
    public void weave(WovenClass wovenClass) {
        String bundleSymbolicName = wovenClass.getBundleWiring().getBundle().getSymbolicName();
        if (shouldSkipBundle(bundleSymbolicName)) {
            return;
        }

        String className = wovenClass.getClassName();
        List<Weaver> weavers = registry.getWeavers();

        for (Weaver weaver : weavers) {
            try {
                if (weaver.canWeave(className, wovenClass)) {
                    LOG.log(Level.FINE, () -> "Weaving " + className + " with " + weaver.name()
                            + " (bundle=" + bundleSymbolicName + ")");
                    weaver.weave(wovenClass, telemetry);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Weaver " + weaver.name()
                        + " failed on " + className + ": " + e.getMessage(), e);
            }
        }
    }

    private boolean shouldSkipBundle(String bsn) {
        if (bsn == null) {
            return true;
        }
        if (ownBundleSymbolicName.equals(bsn)) {
            return true;
        }
        // Skip OSGi framework implementations
        if ("org.apache.felix.framework".equals(bsn) || "org.eclipse.osgi".equals(bsn)) {
            return true;
        }
        // Skip infrastructure bundles that should never be instrumented
        return bsn.startsWith("org.apache.karaf.") || bsn.startsWith("org.ops4j.pax.")
                || bsn.startsWith("org.eclipse.jetty.") || bsn.startsWith("org.apache.felix.")
                || bsn.startsWith("org.apache.aries.") || bsn.startsWith("org.apache.cxf.")
                || bsn.startsWith("org.apache.xbean.");
    }
}
