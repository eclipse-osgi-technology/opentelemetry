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

import java.util.logging.Logger;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.weaving.WeavingHook;

/**
 * Bundle activator that registers the {@link WeavingHook} service and starts
 * the OpenTelemetry service tracker.
 * <p>
 * This bundle uses a {@link BundleActivator} instead of Declarative Services
 * because it must be active before DS components are loaded — otherwise it
 * cannot weave DS component classes.
 */
@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public class WeavingHookActivator implements BundleActivator {

    private static final Logger LOG = Logger.getLogger(WeavingHookActivator.class.getName());

    private OpenTelemetryProxy telemetryProxy;
    private ServiceRegistration<WeavingHook> hookRegistration;

    @Override
    public void start(BundleContext context) {
        telemetryProxy = new OpenTelemetryProxy(context);
        telemetryProxy.open();

        WeaverRegistry registry = new WeaverRegistry();
        String ownBsn = context.getBundle().getSymbolicName();
        OpenTelemetryWeavingHook hook = new OpenTelemetryWeavingHook(
                registry, telemetryProxy, ownBsn);

        hookRegistration = context.registerService(WeavingHook.class, hook, null);

        LOG.info("OpenTelemetry Weaving Hook activated with "
                + registry.getWeavers().size() + " weaver(s)");
    }

    @Override
    public void stop(BundleContext context) {
        if (hookRegistration != null) {
            try {
                hookRegistration.unregister();
            } catch (IllegalStateException e) {
                // Already unregistered
            }
            hookRegistration = null;
        }
        if (telemetryProxy != null) {
            telemetryProxy.close();
            telemetryProxy = null;
        }
        LOG.info("OpenTelemetry Weaving Hook deactivated");
    }
}
