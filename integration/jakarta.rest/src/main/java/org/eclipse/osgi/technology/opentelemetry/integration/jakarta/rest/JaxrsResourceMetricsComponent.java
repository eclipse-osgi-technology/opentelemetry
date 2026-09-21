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

package org.eclipse.osgi.technology.opentelemetry.integration.jakarta.rest;

import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SERVICE_PROPERTIES;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_NAME;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.jakartars.whiteboard.propertytypes.JakartarsExtension;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;

/**
 * Registers a {@link JaxrsMetricsFilter} against every JAX-RS Whiteboard application,
 * measuring resource method invocations purely through the standard
 * {@code ContainerRequestFilter}/{@code ContainerResponseFilter} extension points —
 * no bytecode weaving is involved.
 * <p>
 * A new instance of this {@link Feature} is created per application (see
 * {@code scope = ServiceScope.PROTOTYPE}), and {@link #configure(FeatureContext)}
 * registers a {@link JaxrsMetricsFilter} scoped to that application's name and base path.
 * See {@link JaxrsMetricsFilter} for the metrics produced.
 */
@Component(scope = ServiceScope.PROTOTYPE)
@JakartarsExtension
public class JaxrsResourceMetricsComponent implements Feature {

    private static final Logger LOG = Logger.getLogger(JaxrsResourceMetricsComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.integration.jakarta.rest";

    private final OpenTelemetry openTelemetry;

    @Activate
    public JaxrsResourceMetricsComponent(@Reference OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        LOG.info("JaxrsResourceMetricsComponent activated");
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean configure(FeatureContext context) {
        Map<String, Object> serviceProps = (Map<String, Object>) context.getConfiguration()
                .getProperty(JAKARTA_RS_APPLICATION_SERVICE_PROPERTIES);

        String name = "default";
        String base = "/";
        if (serviceProps != null) {
            Object nameProp = serviceProps.get(JAKARTA_RS_NAME);
            if (nameProp != null) {
                name = String.valueOf(nameProp);
            }
            Object baseProp = serviceProps.get(JAKARTA_RS_APPLICATION_BASE);
            if (baseProp != null) {
                base = String.valueOf(baseProp);
            }
        }

        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);
        context.register(new JaxrsMetricsFilter(meter, name, base));

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Registered JaxrsMetricsFilter for application '" + name + "' at base '" + base + "'");
        }
        return true;
    }
}
