/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 * 
 */

package org.eclipse.osgi.technology.opentelemetry.weaving.hook;

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
        var iterator = loader.iterator();
        while (iterator.hasNext()) {
            try {
                Weaver weaver = iterator.next();
                weavers.add(weaver);
                LOG.info("Discovered weaver: " + weaver.name());
            } catch (java.util.ServiceConfigurationError e) {
                LOG.log(Level.WARNING, "Failed to load weaver: " + e.getMessage(), e);
            }
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
