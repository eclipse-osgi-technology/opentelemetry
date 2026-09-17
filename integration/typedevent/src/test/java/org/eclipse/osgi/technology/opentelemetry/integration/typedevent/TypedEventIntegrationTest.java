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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.typedevent.TypedEventConstants;
import org.osgi.service.typedevent.TypedEventHandler;
import org.osgi.service.typedevent.UnhandledEventHandler;
import org.osgi.service.typedevent.UntypedEventHandler;
import org.osgi.service.typedevent.monitor.MonitorEvent;
import org.osgi.service.typedevent.monitor.TypedEventMonitor;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.util.pushstream.PushStream;
import org.osgi.util.pushstream.PushStreamProvider;
import org.osgi.util.pushstream.SimplePushEventSource;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * End to end OSGi integration tests for the Typed Event OpenTelemetry
 * bridge. An {@link OpenTelemetrySdk} wired to exporters that hand each
 * export off to a {@link BlockingQueue} is registered as an
 * {@code OpenTelemetry} service, which satisfies the mandatory reference on
 * {@link TypedEventInventoryComponent}, {@link TypedEventMetricsComponent}
 * and {@link TypedEventTracingComponent} and lets Declarative Services
 * activate them. Typed Event handlers and monitors are then registered as
 * plain OSGi services to drive the components under test, and assertions
 * block on the relevant queue instead of polling.
 */
class TypedEventIntegrationTest {

    @InjectBundleContext
    BundleContext bc;

    private final BlockingQueue<SpanData> spanQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<List<MetricData>> metricQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<LogRecordData> logQueue = new LinkedBlockingQueue<>();

    private SdkTracerProvider tracerProvider;
    private SdkMeterProvider meterProvider;
    private SdkLoggerProvider loggerProvider;
    private OpenTelemetrySdk sdk;

    @BeforeEach
    void setUp() {
        SpanExporter spanExporter = new SpanExporter() {
            @Override
            public CompletableResultCode export(Collection<SpanData> spans) {
                spanQueue.addAll(spans);
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };

        MetricExporter metricExporter = new MetricExporter() {
            @Override
            public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
                return AggregationTemporality.CUMULATIVE;
            }

            @Override
            public CompletableResultCode export(Collection<MetricData> metrics) {
                metricQueue.add(List.copyOf(metrics));
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };

        LogRecordExporter logExporter = new LogRecordExporter() {
            @Override
            public CompletableResultCode export(Collection<LogRecordData> logs) {
                logQueue.addAll(logs);
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };

        tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
        meterProvider = SdkMeterProvider.builder()
            .registerMetricReader(PeriodicMetricReader.builder(metricExporter)
                .setInterval(Duration.ofMillis(50))
                .build())
            .build();
        loggerProvider = SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
            .build();

        sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .build();
    }

    @AfterEach
    void tearDown() {
        if (tracerProvider != null) {
            tracerProvider.close();
        }
        if (meterProvider != null) {
            meterProvider.close();
        }
        if (loggerProvider != null) {
            loggerProvider.close();
        }
    }

    // --- TypedEventInventoryComponent ---

    @Test
    void inventoryEmitsSummaryWithNoHandlersRegistered() throws Exception {
        bc.registerService(OpenTelemetry.class, sdk, new Hashtable<>());

        LogRecordData summary = takeLog();
        assertThat(summary.getBodyValue().asString())
            .isEqualTo("Typed Event handler inventory: 0 typed, 0 untyped, 0 unhandled handlers");
        assertThat(summary.getAttributes().get(AttributeKey.longKey("typedevent.handlers.typed"))).isZero();
        assertThat(summary.getAttributes().get(AttributeKey.longKey("typedevent.handlers.untyped"))).isZero();
        assertThat(summary.getAttributes().get(AttributeKey.longKey("typedevent.handlers.unhandled"))).isZero();
        assertThat(logQueue.poll(200, TimeUnit.MILLISECONDS)).as("no further log records expected").isNull();
    }

    @Test
    void inventoryEmitsOneRecordPerRegisteredHandlerPlusSummary() throws Exception {
        Dictionary<String, Object> typedProps = new Hashtable<>();
        typedProps.put(TypedEventConstants.TYPED_EVENT_TOPICS, "com/example/Foo");
        typedProps.put(TypedEventConstants.TYPED_EVENT_TYPE, "com.example.Foo");
        TypedEventHandler<Object> typedHandler = (topic, event) -> { /* no-op */ };
        bc.registerService(TypedEventHandler.class, typedHandler, typedProps);

        Dictionary<String, Object> untypedProps = new Hashtable<>();
        untypedProps.put(TypedEventConstants.TYPED_EVENT_TOPICS, new String[] { "com/example/*" });
        UntypedEventHandler untypedHandler = (topic, event) -> { /* no-op */ };
        bc.registerService(UntypedEventHandler.class, untypedHandler, untypedProps);

        UnhandledEventHandler unhandledHandler = (topic, event) -> { /* no-op */ };
        bc.registerService(UnhandledEventHandler.class, unhandledHandler, new Hashtable<>());

        bc.registerService(OpenTelemetry.class, sdk, new Hashtable<>());

        List<LogRecordData> logs = takeLogs(4);
        assertThat(logQueue.poll(200, TimeUnit.MILLISECONDS)).as("no further log records expected").isNull();

        LogRecordData summary = logs.stream()
            .filter(r -> r.getBodyValue().asString().startsWith("Typed Event handler inventory"))
            .findFirst()
            .orElseThrow();
        assertThat(summary.getAttributes().get(AttributeKey.longKey("typedevent.handlers.typed"))).isEqualTo(1);
        assertThat(summary.getAttributes().get(AttributeKey.longKey("typedevent.handlers.untyped"))).isEqualTo(1);
        assertThat(summary.getAttributes().get(AttributeKey.longKey("typedevent.handlers.unhandled"))).isEqualTo(1);

        LogRecordData typedRecord = logs.stream()
            .filter(r -> "TypedEventHandler"
                .equals(r.getAttributes().get(AttributeKey.stringKey("typedevent.handler.type"))))
            .findFirst()
            .orElseThrow();
        assertThat(typedRecord.getAttributes().get(AttributeKey.stringKey("typedevent.handler.topics")))
            .isEqualTo("com/example/Foo");
        assertThat(typedRecord.getAttributes().get(AttributeKey.stringKey("typedevent.handler.event_type")))
            .isEqualTo("com.example.Foo");
        assertThat(typedRecord.getAttributes().get(AttributeKey.stringKey("typedevent.handler.bundle"))).isNotNull();

        LogRecordData untypedRecord = logs.stream()
            .filter(r -> "UntypedEventHandler"
                .equals(r.getAttributes().get(AttributeKey.stringKey("typedevent.handler.type"))))
            .findFirst()
            .orElseThrow();
        assertThat(untypedRecord.getAttributes().get(AttributeKey.stringKey("typedevent.handler.topics")))
            .isEqualTo("com/example/*");
    }

    // --- TypedEventMetricsComponent ---

    @Test
    void metricsGaugesTrackRegisteredHandlerCounts() throws Exception {
        bc.registerService(OpenTelemetry.class, sdk, new Hashtable<>());

        TypedEventHandler<Object> handlerOne = (topic, event) -> { /* no-op */ };
        ServiceRegistration<?> handlerOneReg = bc.registerService(TypedEventHandler.class, handlerOne, new Hashtable<>());
        TypedEventHandler<Object> handlerTwo = (topic, event) -> { /* no-op */ };
        bc.registerService(TypedEventHandler.class, handlerTwo, new Hashtable<>());
        UntypedEventHandler untypedHandler = (topic, event) -> { /* no-op */ };
        bc.registerService(UntypedEventHandler.class, untypedHandler, new Hashtable<>());

        takeMetrics(metrics -> gaugeValue(metrics, "osgi.typedevent.handlers.typed").orElse(-1L) == 2
            && gaugeValue(metrics, "osgi.typedevent.handlers.untyped").orElse(-1L) == 1);

        handlerOneReg.unregister();

        takeMetrics(metrics -> gaugeValue(metrics, "osgi.typedevent.handlers.typed").orElse(-1L) == 1);
    }

    @Test
    void metricsCounterAggregatesEventsByTopicPrefix() throws Exception {
        bc.registerService(OpenTelemetry.class, sdk, new Hashtable<>());

        PushStreamProvider psp = new PushStreamProvider();
        SimplePushEventSource<MonitorEvent> source = psp.createSimpleEventSource(MonitorEvent.class);
        registerFakeMonitor(psp, source);
        Thread.sleep(200); // allow SCR to bind the monitor

        publishWithData(source, "org/osgi/example/FooEvent", Map.of());
        publishWithData(source, "org/osgi/example/BarEvent", Map.of());
        publishWithData(source, "com/acme/BazEvent", Map.of());

        takeMetrics(metrics -> counterValue(metrics, "org/osgi") == 2 && counterValue(metrics, "com/acme") == 1);
    }

    @Test
    void metricsCounterUsesFullTopicAsPrefixWhenTopicHasNoSecondSegment() throws Exception {
        bc.registerService(OpenTelemetry.class, sdk, new Hashtable<>());

        PushStreamProvider psp = new PushStreamProvider();
        SimplePushEventSource<MonitorEvent> source = psp.createSimpleEventSource(MonitorEvent.class);
        registerFakeMonitor(psp, source);
        Thread.sleep(200);

        publishWithData(source, "standalone", Map.of());
        publishWithData(source, "one/segment", Map.of());

        takeMetrics(metrics -> counterValue(metrics, "standalone") == 1 && counterValue(metrics, "one/segment") == 1);
    }

    // --- TypedEventTracingComponent ---

    @Test
    void tracingCreatesOneSpanPerEventWithDataAttributes() throws Exception {
        bc.registerService(OpenTelemetry.class, sdk, new Hashtable<>());

        PushStreamProvider psp = new PushStreamProvider();
        SimplePushEventSource<MonitorEvent> source = psp.createSimpleEventSource(MonitorEvent.class);
        registerFakeMonitor(psp, source);
        Thread.sleep(200);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "Alice");
        data.put("count", 3);
        data.put("active", true);
        publishWithData(source, "com/example/Widget", data);

        SpanData span = takeSpan();
        assertThat(span.getName()).isEqualTo("osgi.typedevent.deliver");
        assertThat(span.getKind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("typedevent.topic"))).isEqualTo("com/example/Widget");
        assertThat(span.getAttributes().get(AttributeKey.longKey("typedevent.data.field_count"))).isEqualTo(3);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("typedevent.data.name"))).isEqualTo("Alice");
        assertThat(span.getAttributes().get(AttributeKey.longKey("typedevent.data.count"))).isEqualTo(3);
        assertThat(span.getAttributes().get(AttributeKey.booleanKey("typedevent.data.active"))).isTrue();
        assertThat(spanQueue.poll(200, TimeUnit.MILLISECONDS)).as("no further spans expected").isNull();
    }

    @Test
    void tracingTruncatesEventDataAfterTwentyAttributes() throws Exception {
        bc.registerService(OpenTelemetry.class, sdk, new Hashtable<>());

        PushStreamProvider psp = new PushStreamProvider();
        SimplePushEventSource<MonitorEvent> source = psp.createSimpleEventSource(MonitorEvent.class);
        registerFakeMonitor(psp, source);
        Thread.sleep(200);

        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < 25; i++) {
            data.put("attr" + i, i);
        }
        publishWithData(source, "com/example/Wide", data);

        SpanData span = takeSpan();
        assertThat(span.getAttributes().get(AttributeKey.booleanKey("typedevent.data.truncated"))).isTrue();

        long dataFieldAttributeCount = span.getAttributes().asMap().keySet().stream()
            .filter(k -> k.getKey().startsWith("typedevent.data.attr"))
            .count();
        assertThat(dataFieldAttributeCount).isEqualTo(20);
    }

    // --- helpers ---

    private void registerFakeMonitor(PushStreamProvider psp, SimplePushEventSource<MonitorEvent> source) {
        TypedEventMonitor monitor = new TypedEventMonitor() {
            @Override
            public PushStream<MonitorEvent> monitorEvents() {
                return psp.createStream(source);
            }

            @Override
            public PushStream<MonitorEvent> monitorEvents(int history) {
                return monitorEvents();
            }

            @Override
            public PushStream<MonitorEvent> monitorEvents(Instant history) {
                return monitorEvents();
            }
        };
        bc.registerService(TypedEventMonitor.class, monitor, new Hashtable<>());
    }

    private void publishWithData(SimplePushEventSource<MonitorEvent> source, String topic, Map<String, Object> data) {
        MonitorEvent event = new MonitorEvent();
        event.topic = topic;
        event.eventData = data;
        event.publicationTime = Instant.now();
        source.publish(event);
    }

    private SpanData takeSpan() throws InterruptedException {
        SpanData span = spanQueue.poll(3, TimeUnit.SECONDS);
        assertThat(span).as("expected a span within the timeout").isNotNull();
        return span;
    }

    private LogRecordData takeLog() throws InterruptedException {
        LogRecordData log = logQueue.poll(3, TimeUnit.SECONDS);
        assertThat(log).as("expected a log record within the timeout").isNotNull();
        return log;
    }

    private List<LogRecordData> takeLogs(int count) throws InterruptedException {
        List<LogRecordData> logs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            logs.add(takeLog());
        }
        return logs;
    }

    /**
     * Drains metric snapshots from the queue - one per collection cycle of the
     * periodic reader - until one satisfies {@code condition}, and returns it.
     * Since the reader exports on a fixed interval regardless of whether
     * anything changed, non-matching snapshots are simply discarded.
     */
    private List<MetricData> takeMetrics(Predicate<List<MetricData>> condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        List<MetricData> metrics;
        do {
            long remaining = deadline - System.currentTimeMillis();
            metrics = metricQueue.poll(Math.max(remaining, 0), TimeUnit.MILLISECONDS);
            assertThat(metrics).as("expected a matching metrics snapshot within the timeout").isNotNull();
        } while (!condition.test(metrics));
        return metrics;
    }

    private static Optional<Long> gaugeValue(List<MetricData> metrics, String metricName) {
        return metrics.stream()
            .filter(m -> m.getName().equals(metricName))
            .flatMap(m -> m.getLongGaugeData().getPoints().stream())
            .map(LongPointData::getValue)
            .findFirst();
    }

    private static long counterValue(List<MetricData> metrics, String topicPrefix) {
        return metrics.stream()
            .filter(m -> m.getName().equals("osgi.typedevent.events.total"))
            .flatMap(m -> m.getLongSumData().getPoints().stream())
            .filter(p -> topicPrefix.equals(p.getAttributes().get(AttributeKey.stringKey("typedevent.topic.prefix"))))
            .mapToLong(LongPointData::getValue)
            .sum();
    }
}
