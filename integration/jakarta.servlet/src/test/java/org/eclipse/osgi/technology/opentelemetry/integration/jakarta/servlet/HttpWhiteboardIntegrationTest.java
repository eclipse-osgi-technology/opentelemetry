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

import static org.assertj.core.api.Assertions.assertThat;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.osgi.service.servlet.runtime.HttpServiceRuntime;
import org.osgi.service.servlet.runtime.dto.RuntimeDTO;
import org.osgi.service.servlet.runtime.dto.ServletContextDTO;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.annotation.Property;
import org.osgi.test.common.annotation.config.WithConfiguration;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.MetricData;

import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Full-stack integration test: boots a real HTTP Whiteboard implementation
 * ({@code org.apache.felix.http.jetty12}), registers a plain servlet, and drives
 * it with real HTTP requests to verify that {@link HttpMetricsFilter} records the
 * expected OpenTelemetry metrics — using only {@code jakarta.servlet.Filter}, no
 * bytecode weaving. {@code http.route} is the matched servlet's registered URL
 * pattern (e.g. {@code /widgets/*}); {@code http.path} is the raw request path.
 * <p>
 * The "unmatched request, {@code http.route} omitted" case is exercised only by
 * {@code HttpMetricsFilterTest} (a mocked {@code HttpServletMapping} with no match):
 * a request under a path no whiteboard servlet claims at all never reaches any
 * registered filter under Felix's HTTP Whiteboard implementation, so it can't be
 * observed here — the {@code /widgets/missing} case below still matches the
 * registered {@code /widgets/*} pattern and reaches {@link HttpMetricsFilter}, it
 * just 404s from within the servlet.
 */
@ExtendWith({ BundleContextExtension.class, ServiceExtension.class })
@WithConfiguration(pid = Constants.PID, properties = { @Property(key = "serviceName", value = "http-metrics-it") })
class HttpWhiteboardIntegrationTest {

    public static class WidgetServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String pathInfo = req.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/missing")) {
                resp.sendError(404);
                return;
            }
            if (pathInfo.equals("/boom")) {
                resp.setStatus(500);
                return;
            }
            String id = pathInfo.substring(1);
            resp.setStatus(200);
            resp.getWriter().write("widget-" + id);
        }
    }

    private static final int EXPECTED_METRIC_COUNT = 4; // requests, duration, errors, not_found

    private final List<LogRecord> metricLogRecords = new CopyOnWriteArrayList<>();
    private Logger metricExporterLogger;
    private Handler metricExporterHandler;
    private ServiceRegistration<Servlet> servletRegistration;

    @BeforeEach
    void registerServlet(@InjectBundleContext BundleContext ctx,
            @InjectService(timeout = 10000) HttpServiceRuntime runtime) throws InterruptedException {
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
        props.put(HTTP_WHITEBOARD_SERVLET_PATTERN, "/widgets/*");
        servletRegistration = ctx.registerService(Servlet.class, new WidgetServlet(), props);

        long serviceId = (Long) servletRegistration.getReference().getProperty("service.id");
        awaitServletRegistered(runtime, serviceId);
    }

    @AfterEach
    void tearDown() {
        if (servletRegistration != null) {
            servletRegistration.unregister();
        }
        if (metricExporterLogger != null && metricExporterHandler != null) {
            metricExporterLogger.removeHandler(metricExporterHandler);
        }
    }

    @Test
    void recordsMetricsForRealHttpRequests(@InjectService(timeout = 10000) ConfigurationAdmin configurationAdmin)
            throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<Void> ok = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:8080/widgets/42")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(ok.statusCode()).isEqualTo(200);

        HttpResponse<Void> error = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:8080/widgets/boom")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(error.statusCode()).isEqualTo(500);

        HttpResponse<Void> notFound = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:8080/widgets/missing")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(notFound.statusCode()).isEqualTo(404);

        List<MetricData> metrics = flushMetrics(configurationAdmin);

        assertThat(hasSumPoint(metrics, "http.server.requests", "http.route", "/widgets/*")).isTrue();
        assertThat(hasSumPoint(metrics, "http.server.requests", "http.path", "/widgets/42")).isTrue();
        assertThat(hasHistogramPoint(metrics, "http.server.duration", "http.route", "/widgets/*")).isTrue();
        assertThat(hasSumPoint(metrics, "http.server.errors", "http.path", "/widgets/boom")).isTrue();
        assertThat(hasSumPoint(metrics, "http.server.not_found", "http.path", "/widgets/missing")).isTrue();
        assertThat(hasSumPoint(metrics, "http.server.not_found", "http.route", "/widgets/*")).isTrue();
    }

    private void awaitServletRegistered(HttpServiceRuntime runtime, long serviceId) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            RuntimeDTO dto = runtime.getRuntimeDTO();
            if (dto.servletContextDTOs != null
                    && Arrays.stream(dto.servletContextDTOs).anyMatch(ctx -> hasServlet(ctx, serviceId))) {
                return;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Widget servlet (service.id=" + serviceId + ") was not registered in time");
    }

    private boolean hasServlet(ServletContextDTO ctx, long serviceId) {
        return ctx.servletDTOs != null && Arrays.stream(ctx.servletDTOs).anyMatch(s -> s.serviceId == serviceId);
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

}
