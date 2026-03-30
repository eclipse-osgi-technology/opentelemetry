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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;

/**
 * Emits OpenTelemetry log records inventorying all registered Typed Event
 * handler services at activation time.
 * <p>
 * Queries the OSGi service registry for:
 * <ul>
 *   <li>{@code TypedEventHandler} services — typed event consumers</li>
 *   <li>{@code UntypedEventHandler} services — untyped event consumers</li>
 *   <li>{@code UnhandledEventHandler} services — unhandled event consumers</li>
 * </ul>
 * Each handler is logged with its bundle origin, subscribed topics, and
 * event filter if configured.
 */
@Component(immediate = true)
public class TypedEventInventoryComponent {

    private static final Logger LOG = Logger.getLogger(TypedEventInventoryComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.integration.typedevent";

    @Reference(policy = ReferencePolicy.DYNAMIC)
    private volatile OpenTelemetry openTelemetry;

    @Activate
    public void activate(BundleContext ctx) {
        LOG.info("TypedEventInventoryComponent activated — logging handler inventory");

        io.opentelemetry.api.logs.Logger otelLogger =
            openTelemetry.getLogsBridge().loggerBuilder(INSTRUMENTATION_SCOPE)
                .setInstrumentationVersion("0.1.0")
                .build();

        int typedCount = emitHandlerInventory(ctx, otelLogger,
            "org.osgi.service.typedevent.TypedEventHandler", "TypedEventHandler");
        int untypedCount = emitHandlerInventory(ctx, otelLogger,
            "org.osgi.service.typedevent.UntypedEventHandler", "UntypedEventHandler");
        int unhandledCount = emitHandlerInventory(ctx, otelLogger,
            "org.osgi.service.typedevent.UnhandledEventHandler", "UnhandledEventHandler");

        otelLogger.logRecordBuilder()
            .setSeverity(Severity.INFO)
            .setBody("Typed Event handler inventory: " + typedCount + " typed, "
                + untypedCount + " untyped, " + unhandledCount + " unhandled handlers")
            .setAttribute(AttributeKey.longKey("typedevent.handlers.typed"), (long) typedCount)
            .setAttribute(AttributeKey.longKey("typedevent.handlers.untyped"), (long) untypedCount)
            .setAttribute(AttributeKey.longKey("typedevent.handlers.unhandled"), (long) unhandledCount)
            .emit();

        LOG.info("TypedEventInventoryComponent — emitted inventory for "
            + (typedCount + untypedCount + unhandledCount) + " handlers");
    }

    @Deactivate
    public void deactivate() {
        LOG.info("TypedEventInventoryComponent deactivated");
    }

    private int emitHandlerInventory(BundleContext ctx,
            io.opentelemetry.api.logs.Logger otelLogger,
            String serviceInterface, String handlerType) {
        try {
            ServiceReference<?>[] refs = ctx.getAllServiceReferences(serviceInterface, null);
            if (refs == null) {
                return 0;
            }

            for (ServiceReference<?> ref : refs) {
                emitHandlerLog(otelLogger, ref, handlerType);
            }
            return refs.length;
        } catch (InvalidSyntaxException e) {
            LOG.log(Level.WARNING, "Failed to query " + handlerType + " services", e);
            return 0;
        }
    }

    private void emitHandlerLog(io.opentelemetry.api.logs.Logger otelLogger,
            ServiceReference<?> ref, String handlerType) {

        String bundleName = ref.getBundle() != null
            ? ref.getBundle().getSymbolicName() : "unknown";

        var builder = otelLogger.logRecordBuilder()
            .setSeverity(Severity.INFO)
            .setAttribute(AttributeKey.stringKey("typedevent.handler.type"), handlerType)
            .setAttribute(AttributeKey.stringKey("typedevent.handler.bundle"), bundleName);

        // Event topics
        Object topics = ref.getProperty("event.topics");
        String topicStr = formatProperty(topics);
        if (topicStr != null) {
            builder.setAttribute(AttributeKey.stringKey("typedevent.handler.topics"), topicStr);
        }

        // Event type (TypedEventHandler only)
        Object eventType = ref.getProperty("event.type");
        if (eventType instanceof String s) {
            builder.setAttribute(AttributeKey.stringKey("typedevent.handler.event_type"), s);
        }

        // Event filter
        Object filter = ref.getProperty("event.filter");
        if (filter instanceof String s) {
            builder.setAttribute(AttributeKey.stringKey("typedevent.handler.filter"), s);
        }

        // Service ID
        Object serviceId = ref.getProperty("service.id");
        if (serviceId instanceof Long id) {
            builder.setAttribute(AttributeKey.longKey("typedevent.handler.service_id"), id);
        }

        builder.setBody(handlerType + " from " + bundleName
            + (topicStr != null ? " [topics=" + topicStr + "]" : ""));
        builder.emit();
    }

    private String formatProperty(Object value) {
        if (value instanceof String s) {
            return s;
        } else if (value instanceof String[] arr) {
            return String.join(", ", arr);
        }
        return null;
    }
}
