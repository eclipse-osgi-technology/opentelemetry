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

import java.lang.reflect.Method;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;

/**
 * Measures JAX-RS resource invocations using the standard {@link ContainerRequestFilter}
 * and {@link ContainerResponseFilter} extension points, requiring no bytecode weaving.
 * <p>
 * One instance is registered per JAX-RS application (see {@link JaxrsResourceMetricsComponent}),
 * and follows the same metric names/semantics as the bytecode-woven instrumentation:
 * <ul>
 *   <li>{@code jaxrs.server.requests} — call count by resource (route + HTTP method)</li>
 *   <li>{@code jaxrs.server.duration} — request duration in milliseconds by resource</li>
 *   <li>{@code jaxrs.server.errors} — failing request count by resource and status code</li>
 *   <li>{@code jaxrs.server.not_found} — failing request count by raw request path, for 404 responses</li>
 * </ul>
 * <p>
 * Each measurement carries {@code http.path} (the raw request path) and, when a resource
 * method actually matched, {@code http.route} (that method's URI template, e.g.
 * {@code http://host/widgets/{id}}, derived from its {@code @Path} annotations via the
 * injected {@link ResourceInfo}). {@code http.route} is omitted entirely for requests that
 * matched no resource (e.g. a routing 404), leaving {@code http.path} as the only way to
 * see which path was hit.
 */
public class JaxrsMetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final AttributeKey<String> HTTP_METHOD_KEY = AttributeKey.stringKey("http.method");
    private static final AttributeKey<String> HTTP_PATH_KEY = AttributeKey.stringKey("http.path");
    private static final AttributeKey<String> HTTP_ROUTE_KEY = AttributeKey.stringKey("http.route");
    private static final AttributeKey<Long> HTTP_STATUS_CODE_KEY = AttributeKey.longKey("http.status_code");
    private static final AttributeKey<String> APPLICATION_NAME_KEY = AttributeKey.stringKey("application.name");
    private static final AttributeKey<String> APPLICATION_BASE_KEY = AttributeKey.stringKey("application.base");

    private static final String START_TIME_PROPERTY = JaxrsMetricsFilter.class.getName() + ".startTime";

    private final String applicationName;
    private final String applicationBase;

    private final LongCounter requestCounter;
    private final LongHistogram durationHistogram;
    private final LongCounter errorCounter;
    private final LongCounter notFoundCounter;

    @Context
    ResourceInfo resourceInfo;
    
    public JaxrsMetricsFilter(Meter meter, String applicationName, String applicationBase) {
        this.applicationName = applicationName;
        this.applicationBase = applicationBase;

        this.requestCounter = meter.counterBuilder("jaxrs.server.requests")
                .setDescription("Total JAX-RS resource method invocations")
                .build();
        this.durationHistogram = meter.histogramBuilder("jaxrs.server.duration")
                .setDescription("JAX-RS resource method duration")
                .setUnit("ms")
                .ofLongs()
                .build();
        this.errorCounter = meter.counterBuilder("jaxrs.server.errors")
                .setDescription("Total failing JAX-RS requests by resource and status code")
                .build();
        this.notFoundCounter = meter.counterBuilder("jaxrs.server.not_found")
                .setDescription("Total JAX-RS 404 responses by request path")
                .build();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        requestContext.setProperty(START_TIME_PROPERTY, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        String httpMethod = requestContext.getMethod();
        String path = requestContext.getUriInfo().getPath();
        Method resourceMethod = resourceInfo.getResourceMethod();
        String route = resourceMethod != null ?
        		requestContext.getUriInfo().getBaseUriBuilder()
        			.path(resourceInfo.getResourceClass())
        			.path(resourceMethod)
        			.toTemplate()
        		: null;
        int status = responseContext.getStatus();

        Attributes attrs = Attributes.builder()
                .put(HTTP_METHOD_KEY, httpMethod)
                .put(HTTP_ROUTE_KEY, route)
                .put(HTTP_PATH_KEY, path)
                .put(APPLICATION_NAME_KEY, applicationName)
                .put(APPLICATION_BASE_KEY, applicationBase)
                .build();

        requestCounter.add(1, attrs);

        Object startTime = requestContext.getProperty(START_TIME_PROPERTY);
        if (startTime instanceof Long) {
            long durationMillis = (System.nanoTime() - (Long) startTime) / 1_000_000L;
            durationHistogram.record(durationMillis, attrs);
        }

        if (status >= 400) {
            errorCounter.add(1, attrs.toBuilder().put(HTTP_STATUS_CODE_KEY, status).build());

            if (status == 404) {
                notFoundCounter.add(1, attrs);
            }
        }
    }
}
