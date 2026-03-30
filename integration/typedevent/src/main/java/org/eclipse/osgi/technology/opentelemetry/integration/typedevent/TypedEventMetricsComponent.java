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

package org.eclipse.osgi.technology.opentelemetry.integration.typedevent;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.typedevent.UntypedEventHandler;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Observes all events flowing through the OSGi Typed Event Bus and exposes
 * OpenTelemetry metrics.
 * <p>
 * Registers as an {@link UntypedEventHandler} with {@code event.topics=*} to
 * receive every event regardless of topic. Publishes:
 * <ul>
 *   <li>{@code osgi.typedevent.events.total} — counter of events by topic</li>
 *   <li>{@code osgi.typedevent.handlers.count} — gauge of registered event handler
 *       services by type (typed, untyped)</li>
 * </ul>
 */
@Component(immediate = true, service = UntypedEventHandler.class,
    property = "event.topics=*")
public class TypedEventMetricsComponent implements UntypedEventHandler {

    private static final Logger LOG = Logger.getLogger(TypedEventMetricsComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.integration.typedevent";

    @Reference(policy = ReferencePolicy.DYNAMIC)
    private volatile OpenTelemetry openTelemetry;

    private BundleContext bundleContext;
    private LongCounter eventCounter;
    private ObservableLongGauge typedHandlerGauge;
    private ObservableLongGauge untypedHandlerGauge;

    @Activate
    public void activate(BundleContext ctx) {
        this.bundleContext = ctx;
        LOG.info("TypedEventMetricsComponent activated — registering Typed Event metrics");
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);

        eventCounter = meter.counterBuilder("osgi.typedevent.events.total")
            .setDescription("Total number of typed events delivered")
            .setUnit("{events}")
            .build();

        typedHandlerGauge = meter.gaugeBuilder("osgi.typedevent.handlers.typed")
            .setDescription("Number of registered TypedEventHandler services")
            .setUnit("{handlers}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                measurement.record(countServices("org.osgi.service.typedevent.TypedEventHandler"));
            });

        untypedHandlerGauge = meter.gaugeBuilder("osgi.typedevent.handlers.untyped")
            .setDescription("Number of registered UntypedEventHandler services")
            .setUnit("{handlers}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                measurement.record(countServices("org.osgi.service.typedevent.UntypedEventHandler"));
            });

        LOG.info("TypedEventMetricsComponent — Typed Event metrics registered");
    }

    @Deactivate
    public void deactivate() {
        closeQuietly(typedHandlerGauge);
        closeQuietly(untypedHandlerGauge);
        LOG.info("TypedEventMetricsComponent deactivated");
    }

    @Override
    public void notifyUntyped(String topic, Map<String, Object> event) {
        try {
            String topicPrefix = extractTopicPrefix(topic);
            eventCounter.add(1, Attributes.of(
                AttributeKey.stringKey("typedevent.topic"), topic,
                AttributeKey.stringKey("typedevent.topic.prefix"), topicPrefix
            ));
        } catch (Exception e) {
            LOG.log(Level.FINE, "Failed to record typed event metric", e);
        }
    }

    private long countServices(String className) {
        try {
            ServiceReference<?>[] refs = bundleContext.getAllServiceReferences(className, null);
            return refs != null ? refs.length : 0;
        } catch (InvalidSyntaxException e) {
            LOG.log(Level.FINE, "Failed to count services: " + className, e);
            return 0;
        }
    }

    /**
     * Extracts the top-level prefix from a topic (first two segments).
     * For example, {@code "org/osgi/example/ExampleEvent"} returns {@code "org/osgi"}.
     */
    static String extractTopicPrefix(String topic) {
        if (topic == null) {
            return "unknown";
        }
        int first = topic.indexOf('/');
        if (first < 0) {
            return topic;
        }
        int second = topic.indexOf('/', first + 1);
        if (second < 0) {
            return topic;
        }
        return topic.substring(0, second);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
