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

package org.eclipse.osgi.technology.opentelemetry.integration.scr;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.component.runtime.dto.UnsatisfiedReferenceDTO;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Periodically inspects the DS component landscape and creates OpenTelemetry
 * traces for component health checks.
 * <p>
 * Every 30 seconds, this component creates a parent span {@code osgi.scr.healthcheck}
 * with a child span per component that is not in the ACTIVE state.
 * This makes component resolution problems visible in distributed trace backends.
 * <p>
 * Components in FAILED_ACTIVATION state produce error spans with the failure message.
 * Components with unsatisfied references produce warning spans listing the missing dependencies.
 * <p>
 * Supports dynamic 1..n bindings of {@link ServiceComponentRuntime} services,
 * running a separate health-check scheduler per service instance.
 */
@Component(immediate = true)
public class ScrHealthCheckComponent {

    private static final Logger LOG = Logger.getLogger(ScrHealthCheckComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.integration.scr";
    private static final AttributeKey<Long> SERVICE_ID_KEY = AttributeKey.longKey("osgi.service.id");

    @Reference(policy = ReferencePolicy.DYNAMIC)
    private volatile OpenTelemetry openTelemetry;

    private final ConcurrentHashMap<ServiceComponentRuntime, ScheduledExecutorService> services = new ConcurrentHashMap<>();

    @Activate
    public void activate() {
        LOG.info("ScrHealthCheckComponent activated");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindServiceComponentRuntime(ServiceComponentRuntime scr, Map<String, Object> properties) {
        long serviceId = (Long) properties.get(Constants.SERVICE_ID);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "otel-scr-healthcheck-" + serviceId);
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> runHealthCheck(scr, serviceId), 5, 30, TimeUnit.SECONDS);
        services.put(scr, scheduler);
        LOG.info("ScrHealthCheckComponent — bound ServiceComponentRuntime (service.id=" + serviceId + ")");
    }

    void unbindServiceComponentRuntime(ServiceComponentRuntime scr) {
        ScheduledExecutorService scheduler = services.remove(scr);
        if (scheduler != null) {
            scheduler.shutdown();
            LOG.info("ScrHealthCheckComponent — unbound ServiceComponentRuntime");
        }
    }

    @Deactivate
    public void deactivate() {
        services.values().forEach(ScheduledExecutorService::shutdown);
        services.clear();
        LOG.info("ScrHealthCheckComponent deactivated");
    }

    private void runHealthCheck(ServiceComponentRuntime scr, long serviceId) {
        try {
            Tracer tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE, "0.1.0");
            Collection<ComponentDescriptionDTO> descriptions = scr.getComponentDescriptionDTOs();

            int totalComponents = 0;
            int problemComponents = 0;

            Span parentSpan = tracer.spanBuilder("osgi.scr.healthcheck")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("osgi.scr.description.count", (long) descriptions.size())
                .setAttribute(SERVICE_ID_KEY, serviceId)
                .startSpan();

            try (Scope parentScope = parentSpan.makeCurrent()) {
                for (ComponentDescriptionDTO desc : descriptions) {
                    for (ComponentConfigurationDTO config : scr.getComponentConfigurationDTOs(desc)) {
                        totalComponents++;

                        if (config.state != ComponentConfigurationDTO.ACTIVE
                                && config.state != ComponentConfigurationDTO.SATISFIED) {
                            problemComponents++;
                            traceComponentProblem(tracer, desc, config);
                        }
                    }
                }

                parentSpan.setAttribute("osgi.scr.configuration.total", totalComponents);
                parentSpan.setAttribute("osgi.scr.configuration.problems", problemComponents);

                if (problemComponents > 0) {
                    parentSpan.setStatus(StatusCode.ERROR,
                        problemComponents + " component(s) with problems");
                    parentSpan.addEvent("Health check found " + problemComponents + " problem(s)");
                } else {
                    parentSpan.addEvent("All " + totalComponents + " components healthy");
                }
            } finally {
                parentSpan.end();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "SCR health check failed", e);
        }
    }

    private void traceComponentProblem(Tracer tracer, ComponentDescriptionDTO desc,
            ComponentConfigurationDTO config) {
        String stateName = ScrMetricsComponent.configStateToString(config.state);

        Span span = tracer.spanBuilder("osgi.scr.problem." + stateName.toLowerCase())
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(AttributeKey.stringKey("osgi.scr.component.name"), desc.name)
            .setAttribute(AttributeKey.stringKey("osgi.scr.component.class"), desc.implementationClass)
            .setAttribute(AttributeKey.stringKey("osgi.scr.component.state"), stateName)
            .setAttribute(AttributeKey.longKey("osgi.scr.component.config_id"), config.id)
            .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            if (desc.bundle != null) {
                span.setAttribute("osgi.scr.bundle.name", desc.bundle.symbolicName);
            }

            if (config.unsatisfiedReferences != null) {
                for (UnsatisfiedReferenceDTO ref : config.unsatisfiedReferences) {
                    span.addEvent("Unsatisfied reference: " + ref.name
                        + (ref.target != null ? " (target=" + ref.target + ")" : ""));
                }
            }

            if (config.failure != null && !config.failure.isEmpty()) {
                span.setStatus(StatusCode.ERROR, "Activation failed");
                span.addEvent("Failure: " + config.failure);
            } else {
                span.setStatus(StatusCode.ERROR, "Component not active: " + stateName);
            }
        } finally {
            span.end();
        }
    }
}
