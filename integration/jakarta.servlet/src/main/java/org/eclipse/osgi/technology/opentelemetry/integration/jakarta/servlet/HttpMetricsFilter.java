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

package org.eclipse.osgi.technology.opentelemetry.integration.jakarta.servlet;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.servlet.whiteboard.propertytypes.HttpWhiteboardContextSelect;
import org.osgi.service.servlet.whiteboard.propertytypes.HttpWhiteboardFilterPattern;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.MappingMatch;

/**
 * Registers against every HTTP Whiteboard servlet context and measures servlet
 * requests using the standard {@link Filter} extension point, requiring no bytecode
 * weaving.
 * <p>
 * A new instance is created per matched servlet context (see
 * {@code scope = ServiceScope.PROTOTYPE}), and {@link #init(FilterConfig)} builds
 * this instance's counters/histogram scoped to that context's name and path. Follows
 * the same metric names/semantics as the bytecode-woven instrumentation:
 * <ul>
 *   <li>{@code http.server.requests} — call count by route and HTTP method</li>
 *   <li>{@code http.server.duration} — request duration in milliseconds by route</li>
 *   <li>{@code http.server.errors} — failing request count by route and status code</li>
 *   <li>{@code http.server.not_found} — failing request count by raw request path, for 404 responses</li>
 * </ul>
 * <p>
 * Each measurement carries {@code http.path} (the raw request path) and, when a
 * servlet actually matched, {@code http.route} (that servlet's mapped URL pattern,
 * derived from {@link HttpServletRequest#getHttpServletMapping()}). {@code http.route}
 * is omitted entirely for requests that matched no explicit servlet mapping (e.g. a
 * routing 404 falling through to the default servlet), leaving {@code http.path} as
 * the only way to see which path was hit.
 */
@Component(scope = ServiceScope.PROTOTYPE)
@HttpWhiteboardFilterPattern("/*")
@HttpWhiteboardContextSelect("(osgi.http.whiteboard.context.name=*)")
public class HttpMetricsFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(HttpMetricsFilter.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.integration.jakarta.servlet";

    private static final AttributeKey<String> HTTP_METHOD_KEY = AttributeKey.stringKey("http.method");
    private static final AttributeKey<String> HTTP_PATH_KEY = AttributeKey.stringKey("http.path");
    private static final AttributeKey<String> HTTP_ROUTE_KEY = AttributeKey.stringKey("http.route");
    private static final AttributeKey<Long> HTTP_STATUS_CODE_KEY = AttributeKey.longKey("http.status_code");
    private static final AttributeKey<String> CONTEXT_NAME_KEY = AttributeKey.stringKey("context.name");
    private static final AttributeKey<String> CONTEXT_PATH_KEY = AttributeKey.stringKey("context.path");

    private final OpenTelemetry openTelemetry;

    private String contextName;
    private String contextPath;

    private LongCounter requestCounter;
    private LongHistogram durationHistogram;
    private LongCounter errorCounter;
    private LongCounter notFoundCounter;

    @Activate
    public HttpMetricsFilter(@Reference OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        ServletContext servletContext = filterConfig.getServletContext();
        contextName = servletContext.getServletContextName() != null ? servletContext.getServletContextName() : "default";
        contextPath = servletContext.getContextPath() != null ? servletContext.getContextPath() : "/";

        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);
        requestCounter = meter.counterBuilder("http.server.requests")
                .setDescription("Total HTTP Whiteboard servlet requests")
                .build();
        durationHistogram = meter.histogramBuilder("http.server.duration")
                .setDescription("HTTP Whiteboard servlet request duration")
                .setUnit("ms")
                .ofLongs()
                .build();
        errorCounter = meter.counterBuilder("http.server.errors")
                .setDescription("Total failing HTTP Whiteboard requests by route and status code")
                .build();
        notFoundCounter = meter.counterBuilder("http.server.not_found")
                .setDescription("Total HTTP Whiteboard 404 responses by request path")
                .build();

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Registered HttpMetricsFilter for context '" + contextName + "' at path '" + contextPath + "'");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        long startTime = System.nanoTime();
        RuntimeException failure = null;
        try {
            chain.doFilter(request, response);
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            recordMetrics(httpRequest, httpResponse, startTime, failure);
        }
    }

    private void recordMetrics(HttpServletRequest request, HttpServletResponse response, long startTime,
            RuntimeException failure) {
        String httpMethod = request.getMethod();
        String path = request.getRequestURI();
        String route = resolveRoute(request);
        int status = failure != null ? 500 : response.getStatus();

        Attributes attrs = Attributes.builder()
                .put(HTTP_METHOD_KEY, httpMethod)
                .put(HTTP_ROUTE_KEY, route)
                .put(HTTP_PATH_KEY, path)
                .put(CONTEXT_NAME_KEY, contextName)
                .put(CONTEXT_PATH_KEY, contextPath)
                .build();

        requestCounter.add(1, attrs);

        long durationMillis = (System.nanoTime() - startTime) / 1_000_000L;
        durationHistogram.record(durationMillis, attrs);

        if (status >= 400) {
            errorCounter.add(1, attrs.toBuilder().put(HTTP_STATUS_CODE_KEY, status).build());

            if (status == 404) {
                notFoundCounter.add(1, attrs);
            }
        }
    }

    /**
     * Derives the route template from the request's servlet mapping, or {@code null}
     * when no explicit servlet mapping matched (e.g. the request fell through to the
     * default/unmapped servlet).
     */
    private static String resolveRoute(HttpServletRequest request) {
        HttpServletMapping mapping = request.getHttpServletMapping();
        if (mapping == null || mapping.getMappingMatch() == null
                || mapping.getMappingMatch() == MappingMatch.DEFAULT) {
            return null;
        }
        return mapping.getPattern();
    }

    @Override
    public void destroy() {
        // No resources to release: the counters/histogram are owned by the Meter.
    }
}
