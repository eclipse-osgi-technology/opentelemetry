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

package org.eclipse.osgi.technology.opentelemetry.weaving.jakarta.rest;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.osgi.technology.opentelemetry.weaving.hook.OpenTelemetryProxy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Static helper methods called from woven JAX-RS bytecode.
 * Holds a reference to the {@link OpenTelemetryProxy} which provides
 * noop implementations when the OpenTelemetry service is not available.
 */
public final class JaxRsInstrumentationHelper {

    static final String SCOPE =
            "org.eclipse.osgi.technology.opentelemetry.weaving.jakarta.rest";

    private static final AtomicReference<OpenTelemetryProxy> PROXY = new AtomicReference<>();

    private JaxRsInstrumentationHelper() {}

    static void setProxy(OpenTelemetryProxy proxy) {
        PROXY.set(proxy);
    }

    /**
     * Called at the beginning of a JAX-RS resource method.
     *
     * @param httpMethod the HTTP method (GET, POST, etc.) from the annotation
     * @param route the path template from {@code @Path} annotations
     * @param resourceClass the resource class name
     * @param methodName the Java method name
     * @return context array {@code [Span, Scope, startTimeMillis]} or null
     */
    public static Object[] onMethodEnter(String httpMethod, String route,
            String resourceClass, String methodName) {
        OpenTelemetryProxy proxy = PROXY.get();
        if (proxy == null) {
            return null;
        }

        Tracer tracer = proxy.getTracer(SCOPE);
        String spanName = httpMethod + " " + route;

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AttributeKey.stringKey("http.method"), httpMethod)
                .setAttribute(AttributeKey.stringKey("http.route"), route)
                .setAttribute(AttributeKey.stringKey("jaxrs.resource.class"), resourceClass)
                .setAttribute(AttributeKey.stringKey("jaxrs.resource.method"), methodName)
                .startSpan();

        Scope scope = span.makeCurrent();
        return new Object[] { span, scope, System.currentTimeMillis() };
    }

    /**
     * Called when a JAX-RS resource method throws an exception.
     */
    public static void onMethodError(Object[] context, Throwable error) {
        if (context == null) {
            return;
        }
        Span span = (Span) context[0];
        span.setStatus(StatusCode.ERROR, error.getMessage());
        span.recordException(error);
    }

    /**
     * Called at the end of a JAX-RS resource method (normal or exceptional).
     */
    public static void onMethodExit(Object[] context) {
        if (context == null) {
            return;
        }
        Span span = (Span) context[0];
        Scope scope = (Scope) context[1];
        long startTime = (long) context[2];

        try {
            long duration = System.currentTimeMillis() - startTime;

            OpenTelemetryProxy proxy = PROXY.get();
            if (proxy != null) {
                Meter meter = proxy.getMeter(SCOPE);
                Attributes attrs = Attributes.empty();

                LongCounter counter = meter.counterBuilder("jaxrs.server.requests")
                        .setDescription("Total JAX-RS resource method invocations")
                        .build();
                counter.add(1, attrs);

                LongHistogram histogram = meter.histogramBuilder("jaxrs.server.duration")
                        .setDescription("JAX-RS resource method duration")
                        .setUnit("ms")
                        .ofLongs()
                        .build();
                histogram.record(duration, attrs);
            }
        } finally {
            span.end();
            scope.close();
        }
    }
}
