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

import static org.assertj.core.api.Assertions.assertThat;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.eclipse.osgi.technology.opentelemetry.core.sender.logging.api.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.jakartars.runtime.dto.BaseApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.RuntimeDTO;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.annotation.Property;
import org.osgi.test.common.annotation.config.WithConfiguration;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.MetricData;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;

/**
 * Full-stack integration test: boots a real JAX-RS Whiteboard implementation
 * ({@code org.eclipse.osgitech.rest}, Jersey-based, served over Felix's Jetty
 * Servlet Whiteboard), registers a plain JAX-RS resource, and drives it with
 * real HTTP requests to verify that {@link JaxrsResourceMetricsComponent}
 * (via {@link JaxrsMetricsFilter}) records the expected OpenTelemetry metrics —
 * using only {@code ContainerRequestFilter}/{@code ContainerResponseFilter}, no
 * bytecode weaving. {@code http.route} is the matched resource's URI template
 * (e.g. {@code http://localhost:8080/widgets/{id}}); {@code http.path} is the raw
 * request path. {@code http.route} is omitted entirely for unmatched requests.
 */
@ExtendWith({ BundleContextExtension.class, ServiceExtension.class })
@WithConfiguration(pid = "JakartarsServletWhiteboardRuntimeComponent")
@WithConfiguration(pid = Constants.PID, properties = { @Property(key = "serviceName", value = "jaxrs-metrics-it") })
class JaxrsWhiteboardIntegrationTest {

    @Path("widgets")
    public static class WidgetResource {

        @GET
        @Path("{id}")
        public Response getWidget(@PathParam("id") String id) {
            return Response.ok("widget-" + id).build();
        }

        @GET
        @Path("boom")
        public Response boom() {
            return Response.status(500).build();
        }
    }

    private static final int EXPECTED_METRIC_COUNT = 4; // requests, duration, errors, not_found

    private final List<LogRecord> metricLogRecords = new CopyOnWriteArrayList<>();
    private Logger metricExporterLogger;
    private Handler metricExporterHandler;
    private ServiceRegistration<?> resourceRegistration;

    @BeforeEach
    void registerResource(@InjectBundleContext BundleContext ctx,
            @InjectService(timeout = 10000) JakartarsServiceRuntime runtime) throws InterruptedException {
        metricExporterLogger = Logger.getLogger("io.opentelemetry.exporter.logging.LoggingMetricExporter");
        metricExporterHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                metricLogRecords.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        metricExporterLogger.addHandler(metricExporterHandler);

        Hashtable<String, Object> props = new Hashtable<>();
        props.put(JAKARTA_RS_RESOURCE, Boolean.TRUE);
        resourceRegistration = ctx.registerService(Object.class, new WidgetResource(), props);

        long serviceId = (Long) resourceRegistration.getReference().getProperty("service.id");
        awaitResourceRegistered(runtime, serviceId);
    }

    @AfterEach
    void tearDown() {
        if (resourceRegistration != null) {
            resourceRegistration.unregister();
        }
        if (metricExporterLogger != null && metricExporterHandler != null) {
            metricExporterLogger.removeHandler(metricExporterHandler);
        }
    }

    @Test
    void recordsMetricsForRealHttpRequests(@InjectService(timeout = 10000) ClientBuilder clientBuilder,
            @InjectService(timeout = 10000) ConfigurationAdmin configurationAdmin) throws Exception {
        Client client = clientBuilder.build();
        try {
            Response ok = client.target("http://localhost:8080/widgets/42").request().get();
            assertThat(ok.getStatus()).isEqualTo(200);
            ok.close();

            Response error = client.target("http://localhost:8080/widgets/boom").request().get();
            assertThat(error.getStatus()).isEqualTo(500);
            error.close();

            Response notFound = client.target("http://localhost:8080/does/not/exist").request().get();
            assertThat(notFound.getStatus()).isEqualTo(404);
            notFound.close();
        } finally {
            client.close();
        }

        List<MetricData> metrics = flushMetrics(configurationAdmin);

        assertThat(hasSumPoint(metrics, "jaxrs.server.requests", "http.route", "http://localhost:8080/widgets/{id}"))
                .isTrue();
        assertThat(hasSumPoint(metrics, "jaxrs.server.requests", "http.path", "widgets/42")).isTrue();
        assertThat(hasHistogramPoint(metrics, "jaxrs.server.duration", "http.route", "http://localhost:8080/widgets/{id}"))
                .isTrue();
        assertThat(hasSumPoint(metrics, "jaxrs.server.errors", "http.route", "http://localhost:8080/widgets/boom"))
                .isTrue();
        assertThat(hasSumPoint(metrics, "jaxrs.server.not_found", "http.path", "does/not/exist")).isTrue();
        assertThat(hasAttribute(metrics, "jaxrs.server.not_found", "http.route")).isFalse();
    }

    private void awaitResourceRegistered(JakartarsServiceRuntime runtime, long serviceId) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            RuntimeDTO dto = runtime.getRuntimeDTO();
            if (hasResource(dto.defaultApplication, serviceId) || (dto.applicationDTOs != null
                    && Arrays.stream(dto.applicationDTOs).anyMatch(app -> hasResource(app, serviceId)))) {
                return;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Widget resource (service.id=" + serviceId + ") was not registered in time");
    }

    private boolean hasResource(BaseApplicationDTO app, long serviceId) {
        return app != null && app.resourceDTOs != null
                && Arrays.stream(app.resourceDTOs).anyMatch(r -> r.serviceId == serviceId);
    }

    /**
     * The logging {@code OpenTelemetry} service only exports metrics via its
     * {@code PeriodicMetricReader} (60s interval) or when its SDK is closed. Since the
     * {@code OpenTelemetry} service is only exposed through the plain API (no SDK
     * downcast available), reconfiguring its Config Admin PID forces
     * {@code AbstractOpenTelemetryService} to close (and flush) the old SDK before
     * building a replacement — the same mechanism that already fires on test teardown.
     * A property actually has to change for Config Admin to redeliver the
     * configuration, so a fresh marker value is stamped in on every call.
     */
    private List<MetricData> flushMetrics(ConfigurationAdmin configurationAdmin) throws Exception {
        metricLogRecords.clear();

        Configuration configuration = configurationAdmin.getConfiguration(Constants.PID, null);
        Dictionary<String, Object> props = configuration.getProperties();
        if (props == null) {
            props = new Hashtable<>();
        }
        props.put("flushMarker", System.nanoTime());
        configuration.update(props);

        // The reconfiguration is dispatched asynchronously by Config Admin, and the
        // exporter logs one record per metric on that thread — poll rather than
        // stopping at the first (possibly unrelated) log record.
        List<MetricData> metrics = extractMetrics();
        for (int i = 0; i < 100 && metrics.size() < EXPECTED_METRIC_COUNT; i++) {
            Thread.sleep(100);
            metrics = extractMetrics();
        }
        return metrics;
    }

    private List<MetricData> extractMetrics() {
        List<MetricData> metrics = new ArrayList<>();
        for (LogRecord record : metricLogRecords) {
            Object[] params = record.getParameters();
            if (params != null && params.length > 0 && params[0] instanceof MetricData) {
                metrics.add((MetricData) params[0]);
            }
        }
        return metrics;
    }

    private boolean hasSumPoint(List<MetricData> metrics, String metricName, String attributeName,
            String attributeValue) {
        return metrics.stream()
                .filter(m -> m.getName().equals(metricName))
                .flatMap(m -> m.getLongSumData().getPoints().stream())
                .anyMatch(p -> attributeValue.equals(p.getAttributes().get(AttributeKey.stringKey(attributeName))));
    }

    private boolean hasHistogramPoint(List<MetricData> metrics, String metricName, String attributeName,
            String attributeValue) {
        return metrics.stream()
                .filter(m -> m.getName().equals(metricName))
                .flatMap(m -> m.getHistogramData().getPoints().stream())
                .anyMatch(p -> attributeValue.equals(p.getAttributes().get(AttributeKey.stringKey(attributeName))));
    }

    private boolean hasAttribute(List<MetricData> metrics, String metricName, String attributeName) {
        return metrics.stream()
                .filter(m -> m.getName().equals(metricName))
                .flatMap(m -> m.getLongSumData().getPoints().stream())
                .anyMatch(p -> p.getAttributes().get(AttributeKey.stringKey(attributeName)) != null);
    }
}
