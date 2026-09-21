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

package org.eclipse.osgi.technology.opentelemetry.jakarta.servlet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.eclipse.osgi.technology.opentelemetry.integration.jakarta.servlet.HttpMetricsFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.MappingMatch;

/**
 * Verifies that {@link HttpMetricsFilter} records call counts, durations and failure
 * metrics using only the {@code HttpServletRequest}/{@code HttpServletResponse}/
 * {@code HttpServletMapping} data available to a standard {@code jakarta.servlet.Filter}
 * — no OpenTelemetry SDK or OSGi runtime is required. Mockito stands in for the
 * Servlet/OTel API types.
 * <p>
 * This test lives outside the {@code .integration.} package tree (unlike
 * {@code HttpWhiteboardIntegrationTest}) so that it runs as a plain unit test under
 * Maven Surefire rather than the OSGi/bnd test harness.
 */
@ExtendWith(MockitoExtension.class)
class HttpMetricsFilterTest {

    private static final String WIDGET_ROUTE = "/widgets/*";

    @Mock
    private OpenTelemetry openTelemetry;
    @Mock
    private Meter meter;

    @Mock(answer = Answers.RETURNS_SELF)
    private LongCounterBuilder requestCounterBuilder;
    @Mock
    private LongCounter requestCounter;

    @Mock(answer = Answers.RETURNS_SELF)
    private LongCounterBuilder errorCounterBuilder;
    @Mock
    private LongCounter errorCounter;

    @Mock(answer = Answers.RETURNS_SELF)
    private LongCounterBuilder notFoundCounterBuilder;
    @Mock
    private LongCounter notFoundCounter;

    @Mock(answer = Answers.RETURNS_SELF)
    private DoubleHistogramBuilder doubleHistogramBuilder;
    @Mock(answer = Answers.RETURNS_SELF)
    private LongHistogramBuilder longHistogramBuilder;
    @Mock
    private LongHistogram durationHistogram;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;
    @Mock
    private HttpServletMapping mapping;
    @Mock
    private FilterConfig filterConfig;
    @Mock
    private ServletContext servletContext;

    private HttpMetricsFilter filter;

    @BeforeEach
    void setUp() {
        when(openTelemetry.getMeter(anyString())).thenReturn(meter);

        when(requestCounterBuilder.build()).thenReturn(requestCounter);
        when(meter.counterBuilder("http.server.requests")).thenReturn(requestCounterBuilder);

        when(errorCounterBuilder.build()).thenReturn(errorCounter);
        when(meter.counterBuilder("http.server.errors")).thenReturn(errorCounterBuilder);

        when(notFoundCounterBuilder.build()).thenReturn(notFoundCounter);
        when(meter.counterBuilder("http.server.not_found")).thenReturn(notFoundCounterBuilder);

        when(doubleHistogramBuilder.ofLongs()).thenReturn(longHistogramBuilder);
        when(longHistogramBuilder.build()).thenReturn(durationHistogram);
        when(meter.histogramBuilder("http.server.duration")).thenReturn(doubleHistogramBuilder);

        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getServletContextName()).thenReturn("widgets-app");
        when(servletContext.getContextPath()).thenReturn("/api");

        filter = new HttpMetricsFilter(openTelemetry);
        filter.init(filterConfig);
    }

    /** Stubs {@link #mapping} to report a matched {@code /widgets/*} servlet mapping. */
    private void matchRoute() {
        when(request.getHttpServletMapping()).thenReturn(mapping);
        when(mapping.getMappingMatch()).thenReturn(MappingMatch.PATH);
        when(mapping.getPattern()).thenReturn(WIDGET_ROUTE);
    }

    @Test
    void recordsCallCountAndDurationForRequest() throws Exception {
        matchRoute();
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/widgets/42");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, chain);

        Attributes expectedAttrs = Attributes.builder()
                .put("http.method", "GET")
                .put("http.route", WIDGET_ROUTE)
                .put("http.path", "/api/widgets/42")
                .put("context.name", "widgets-app")
                .put("context.path", "/api")
                .build();

        verify(chain).doFilter(request, response);
        verify(requestCounter).add(1, expectedAttrs);
        verify(durationHistogram).record(anyLong(), eq(expectedAttrs));
        verifyNoInteractions(errorCounter, notFoundCounter);
    }

    @Test
    void accumulatesCallCountAcrossRequestsToSameRoute() throws Exception {
        matchRoute();
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/widgets/42");
        when(response.getStatus()).thenReturn(200);

        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
        }

        verify(requestCounter, times(3)).add(eq(1L), any(Attributes.class));
    }

    @Test
    void recordsErrorsByRouteAndStatusCode() throws Exception {
        matchRoute();
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/widgets/42");
        when(response.getStatus()).thenReturn(500);

        filter.doFilter(request, response, chain);

        Attributes expectedErrorAttrs = Attributes.builder()
                .put("http.method", "GET")
                .put("http.route", WIDGET_ROUTE)
                .put("http.path", "/api/widgets/42")
                .put("context.name", "widgets-app")
                .put("context.path", "/api")
                .put("http.status_code", 500)
                .build();

        verify(errorCounter).add(1, expectedErrorAttrs);
        verifyNoInteractions(notFoundCounter);
    }

    @Test
    void recordsNotFoundWithRawPathAndNoRouteForUnmatchedRequest() throws Exception {
        // No servlet mapping matched: getHttpServletMapping() stays null (default mock answer).
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/does/not/exist");
        when(response.getStatus()).thenReturn(404);

        filter.doFilter(request, response, chain);

        // http.route is omitted entirely (Attributes silently drops null-valued put()s) since
        // there is no matched servlet mapping to derive a route template from.
        Attributes expectedAttrs = Attributes.builder()
                .put("http.method", "GET")
                .put("http.path", "/api/does/not/exist")
                .put("context.name", "widgets-app")
                .put("context.path", "/api")
                .build();

        verify(notFoundCounter).add(1, expectedAttrs);
        verify(errorCounter).add(1, expectedAttrs.toBuilder().put("http.status_code", 404).build());
    }
}
