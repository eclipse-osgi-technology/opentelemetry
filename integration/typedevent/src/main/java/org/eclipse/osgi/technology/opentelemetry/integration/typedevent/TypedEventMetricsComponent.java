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

import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.typedevent.TypedEventHandler;
import org.osgi.service.typedevent.UntypedEventHandler;
import org.osgi.service.typedevent.monitor.MonitorEvent;
import org.osgi.service.typedevent.monitor.TypedEventMonitor;
import org.osgi.util.pushstream.PushStream;

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
@Component()
public class TypedEventMetricsComponent {

    private static final Logger LOG = Logger.getLogger(TypedEventMetricsComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.integration.typedevent";

    private final OpenTelemetry openTelemetry;

    private final Meter meter;
    private final ObservableLongGauge typedHandlerGauge;
    private final ObservableLongGauge untypedHandlerGauge;
    
    
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Map<TypedEventMonitor, AutoCloseable> eventCounter = new IdentityHashMap<>();
    private final Set<ServiceReference<?>> typedHandlers = new HashSet<>();
    private final Set<ServiceReference<?>> untypedHandlers = new HashSet<>();

    @Reference(service = TypedEventMonitor.class,
    		cardinality = MULTIPLE, policy = DYNAMIC)
    void addMonitor(TypedEventMonitor monitor) {
    	LongCounter lc = meter.counterBuilder("osgi.typedevent.events.total")
                .setDescription("Total number of typed events delivered")
                .setUnit("{events}")
                .build();
    	PushStream<MonitorEvent> stream = monitor.monitorEvents();
		stream.forEach(e -> processEvent(lc, e));
		try {
			doWithWriteLock(stream, s -> eventCounter.put(monitor, s));
		} catch (Exception e) {
			doWithWriteLock(stream, s -> eventCounter.remove(monitor, s));
			closeQuietly(stream);
		}
    }
    
    void removeMonitor(TypedEventMonitor monitor) {
    	closeQuietly(doWithWriteLock(monitor, eventCounter::remove));
    }

    @Reference(service = TypedEventHandler.class,
    		cardinality = MULTIPLE, policy = DYNAMIC)
    void addTypedHandler(ServiceReference<TypedEventHandler<?>> ref) {
    	doWithWriteLock(ref, typedHandlers::add);
    }

    void removeTypedHandler(ServiceReference<TypedEventHandler<?>> ref) {
    	doWithWriteLock(ref, typedHandlers::remove);
    }

    @Reference(service = UntypedEventHandler.class,
    		cardinality = MULTIPLE, policy = DYNAMIC)
    void addUntypedHandler(ServiceReference<UntypedEventHandler> ref) {
    	doWithWriteLock(ref, untypedHandlers::add);
    }
    
    void removeUntypedHandler(ServiceReference<UntypedEventHandler> ref) {
    	doWithWriteLock(ref, untypedHandlers::remove);
    }

	private <T, R> R doWithWriteLock(T argument, Function<T, R> action) {
    	Lock writeLock = rwLock.writeLock();
    	writeLock.lock();
    	try {
			return action.apply(argument);
    	} finally {
    		writeLock.unlock();
    	}
    }

    private long getWithReadLock(LongSupplier supplier) {
    	Lock readLock = rwLock.readLock();
    	readLock.lock();
    	try {
    		return supplier.getAsLong();
    	} finally {
    		readLock.unlock();
    	}
    }

    @Activate
    public TypedEventMetricsComponent(@Reference OpenTelemetry ot) {
    	this.openTelemetry = ot;
        LOG.info("TypedEventMetricsComponent activated — registering Typed Event metrics");
        meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);

        typedHandlerGauge = meter.gaugeBuilder("osgi.typedevent.handlers.typed")
            .setDescription("Number of registered TypedEventHandler services")
            .setUnit("{handlers}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                measurement.record(getWithReadLock(typedHandlers::size));
            });

        untypedHandlerGauge = meter.gaugeBuilder("osgi.typedevent.handlers.untyped")
            .setDescription("Number of registered UntypedEventHandler services")
            .setUnit("{handlers}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                measurement.record(getWithReadLock(untypedHandlers::size));
            });

        LOG.info("TypedEventMetricsComponent — Typed Event metrics registered");
    }

    @Deactivate
    public void deactivate() {
        closeQuietly(typedHandlerGauge);
        closeQuietly(untypedHandlerGauge);
        LOG.info("TypedEventMetricsComponent deactivated");
    }

    private void processEvent(LongCounter eventCounter, MonitorEvent event) {
    	String topic = event.topic;
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
