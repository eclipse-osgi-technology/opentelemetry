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

package org.eclipse.osgi.technology.opentelemetry.jakarta.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.osgi.technology.opentelemetry.integration.jakarta.rest.JaxrsMetricsFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

/**
 * Verifies that {@link JaxrsMetricsFilter} records call counts, durations and failure
 * metrics using only the {@code ContainerRequestContext}/{@code ContainerResponseContext}/
 * {@code ResourceInfo} data available to a standard JAX-RS filter — no OpenTelemetry SDK or
 * OSGi runtime is required. Mockito stands in for the JAX-RS/OTel API types, except for
 * {@code UriBuilder}: {@link #matchResource} pairs a real, annotated {@link WidgetResource}
 * with a real {@code UriBuilder} (from {@link UriBuilder#fromUri}) so the route-template
 * computation (reading {@code @Path} off a real class/method) is exercised for real rather
 * than stubbed to a fixed string.
 * <p>
 * This test lives outside the {@code .integration.} package tree (unlike
 * {@code JaxrsWhiteboardIntegrationTest}) so that it runs as a plain unit test under
 * Maven Surefire rather than the OSGi/bnd test harness.
 */
@ExtendWith(MockitoExtension.class)
class JaxrsMetricsFilterTest {

    private static final String BASE_URI = "http://localhost:8080/";
    /** The route template a matched {@link WidgetResource#getWidget()} resolves to. */
    private static final String WIDGET_ROUTE = "http://localhost:8080/widgets/{id}";

    @Path("widgets")
    static class WidgetResource {
        @Path("{id}")
        public void getWidget() {
        }
    }

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
    private ContainerRequestContext requestContext;
    @Mock
    private ContainerResponseContext responseContext;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private ResourceInfo resourceInfo;

    private JaxrsMetricsFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        when(requestCounterBuilder.build()).thenReturn(requestCounter);
        when(meter.counterBuilder("jaxrs.server.requests")).thenReturn(requestCounterBuilder);

        when(errorCounterBuilder.build()).thenReturn(errorCounter);
        when(meter.counterBuilder("jaxrs.server.errors")).thenReturn(errorCounterBuilder);

        when(notFoundCounterBuilder.build()).thenReturn(notFoundCounter);
        when(meter.counterBuilder("jaxrs.server.not_found")).thenReturn(notFoundCounterBuilder);

        when(doubleHistogramBuilder.ofLongs()).thenReturn(longHistogramBuilder);
        when(longHistogramBuilder.build()).thenReturn(durationHistogram);
        when(meter.histogramBuilder("jaxrs.server.duration")).thenReturn(doubleHistogramBuilder);

        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        // A real JAX-RS runtime hands out a fresh builder per call; this exercises the real
        // @Path-reading path(Class)/path(Method) logic instead of stubbing a fixed template string.
        // Unused (hence lenient) for the unmatched-request test, which never reaches this call.
        lenient().when(uriInfo.getBaseUriBuilder()).thenAnswer(invocation -> UriBuilder.fromUri(BASE_URI));

        // A real ContainerRequestContext round-trips setProperty()/getProperty(); simulate
        // that instead of asserting on the raw start-time value the filter stashes there.
        Map<String, Object> properties = new HashMap<>();
        doAnswer(invocation -> properties.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(requestContext).setProperty(anyString(), any());
        when(requestContext.getProperty(anyString())).thenAnswer(invocation -> properties.get(invocation.getArgument(0)));

        filter = new JaxrsMetricsFilter(meter, "widgets-app", "/api");

        // resourceInfo is a package-private @Context field, injected by the JAX-RS runtime;
        // this test lives in a different package (see class javadoc), so it has to reach in.
        Field field = JaxrsMetricsFilter.class.getDeclaredField("resourceInfo");
        field.setAccessible(true);
        field.set(filter, resourceInfo);
    }

    /** Stubs {@link #resourceInfo} to report a matched {@code WidgetResource#getWidget()} call. */
    private void matchResource() throws NoSuchMethodException {
        Method method = WidgetResource.class.getMethod("getWidget");
        when(resourceInfo.getResourceMethod()).thenReturn(method);
        doReturn(WidgetResource.class).when(resourceInfo).getResourceClass();
    }

    @Test
    void recordsCallCountAndDurationForRequest() throws Exception {
        matchResource();
        when(requestContext.getMethod()).thenReturn("GET");
        when(uriInfo.getPath()).thenReturn("widgets/42");
        when(responseContext.getStatus()).thenReturn(200);

        filter.filter(requestContext);
        filter.filter(requestContext, responseContext);

        Attributes expectedAttrs = Attributes.builder()
                .put("http.method", "GET")
                .put("http.route", WIDGET_ROUTE)
                .put("http.path", "widgets/42")
                .put("application.name", "widgets-app")
                .put("application.base", "/api")
                .build();

        verify(requestCounter).add(1, expectedAttrs);
        verify(durationHistogram).record(anyLong(), eq(expectedAttrs));
        verifyNoInteractions(errorCounter, notFoundCounter);
    }

    @Test
    void accumulatesCallCountAcrossRequestsToSameRoute() throws Exception {
        matchResource();
        when(requestContext.getMethod()).thenReturn("GET");
        when(uriInfo.getPath()).thenReturn("widgets/42");
        when(responseContext.getStatus()).thenReturn(200);

        for (int i = 0; i < 3; i++) {
            filter.filter(requestContext);
            filter.filter(requestContext, responseContext);
        }

        verify(requestCounter, times(3)).add(eq(1L), any(Attributes.class));
    }

    @Test
    void recordsErrorsByRouteAndStatusCode() throws Exception {
        matchResource();
        when(requestContext.getMethod()).thenReturn("GET");
        when(uriInfo.getPath()).thenReturn("widgets/42");
        when(responseContext.getStatus()).thenReturn(500);

        filter.filter(requestContext);
        filter.filter(requestContext, responseContext);

        Attributes expectedErrorAttrs = Attributes.builder()
                .put("http.method", "GET")
                .put("http.route", WIDGET_ROUTE)
                .put("http.path", "widgets/42")
                .put("application.name", "widgets-app")
                .put("application.base", "/api")
                .put("http.status_code", 500)
                .build();

        verify(errorCounter).add(1, expectedErrorAttrs);
        verifyNoInteractions(notFoundCounter);
    }

    @Test
    void recordsNotFoundWithRawPathAndNoRouteForUnmatchedRequest() {
        // No resource matched: resourceInfo.getResourceMethod() stays null (default mock answer).
        when(requestContext.getMethod()).thenReturn("GET");
        when(uriInfo.getPath()).thenReturn("does/not/exist");
        when(responseContext.getStatus()).thenReturn(404);

        filter.filter(requestContext);
        filter.filter(requestContext, responseContext);

        // http.route is omitted entirely (Attributes silently drops null-valued put()s) since
        // there is no matched resource method to derive a route template from.
        Attributes expectedAttrs = Attributes.builder()
                .put("http.method", "GET")
                .put("http.path", "does/not/exist")
                .put("application.name", "widgets-app")
                .put("application.base", "/api")
                .build();

        verify(notFoundCounter).add(1, expectedAttrs);
        verify(errorCounter).add(1, expectedAttrs.toBuilder().put("http.status_code", 404).build());
    }
}
