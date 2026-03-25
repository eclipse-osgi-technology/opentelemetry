package org.eclipse.osgi.technology.opentelemetry.weaving;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discovers {@link Weaver} implementations via Java {@link ServiceLoader}.
 * <p>
 * Because weaver implementations are packaged as fragment bundles attached to
 * this host bundle, they share the same classloader. This means standard Java
 * SPI works without any OSGi-specific SPI Fly considerations.
 */
class WeaverRegistry {

    private static final Logger LOG = Logger.getLogger(WeaverRegistry.class.getName());

    private final List<Weaver> weavers;

    WeaverRegistry() {
        weavers = new ArrayList<>();
        ServiceLoader<Weaver> loader = ServiceLoader.load(Weaver.class, getClass().getClassLoader());
        for (Weaver weaver : loader) {
            weavers.add(weaver);
            LOG.info("Discovered weaver: " + weaver.name());
        }
        if (weavers.isEmpty()) {
            LOG.log(Level.FINE, "No weaver fragments discovered");
        } else {
            LOG.info("Total weavers discovered: " + weavers.size());
        }
    }

    List<Weaver> getWeavers() {
        return weavers;
    }
}
