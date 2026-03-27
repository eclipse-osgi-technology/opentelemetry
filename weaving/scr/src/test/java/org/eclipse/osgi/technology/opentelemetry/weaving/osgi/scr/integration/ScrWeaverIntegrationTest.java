package org.eclipse.osgi.technology.opentelemetry.weaving.osgi.scr.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.test.common.annotation.InjectBundleContext;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class ScrWeaverIntegrationTest {

    @InjectBundleContext
    BundleContext context;

    private final List<SpanData> exportedSpans = new CopyOnWriteArrayList<>();
    private SdkTracerProvider tracerProvider;
    private ServiceRegistration<OpenTelemetry> otelRegistration;

    @BeforeEach
    void setUp() {
        SpanExporter testExporter = new SpanExporter() {
            @Override
            public CompletableResultCode export(Collection<SpanData> spans) {
                exportedSpans.addAll(spans);
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
                .addSpanProcessor(SimpleSpanProcessor.create(testExporter))
                .build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        Dictionary<String, Object> otelProps = new Hashtable<>();
        otelProps.put(org.osgi.framework.Constants.SERVICE_RANKING, Integer.MAX_VALUE);
        otelRegistration = context.registerService(OpenTelemetry.class, sdk, otelProps);
    }

    @AfterEach
    void tearDown() {
        if (otelRegistration != null) {
            otelRegistration.unregister();
        }
        if (tracerProvider != null) {
            tracerProvider.close();
        }
        exportedSpans.clear();
    }

    @Test
    void componentActivationCreatesSpan() throws Exception {
        // Find the test app bundle and restart it so the component
        // activates AFTER our OTel SDK is registered
        Bundle testAppBundle = findBundle(
                "org.eclipse.osgi-technology.opentelemetry.weaving.osgi.scr.test.app");
        assertThat(testAppBundle).isNotNull();

        testAppBundle.stop();
        exportedSpans.clear();
        testAppBundle.start();

        Thread.sleep(1000);
        tracerProvider.forceFlush().join(5000, TimeUnit.MILLISECONDS);

        assertThat(exportedSpans).isNotEmpty();

        SpanData span = exportedSpans.stream()
                .filter(s -> s.getName().contains("scr.activate"))
                .filter(s -> s.getName().contains("TestComponent"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No scr.activate span for TestComponent found. Spans: " + exportedSpans));

        assertThat(span.getKind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(span.getAttributes().get(
                AttributeKey.stringKey("scr.lifecycle.action"))).isEqualTo("activate");
        assertThat(span.getAttributes().get(
                AttributeKey.stringKey("scr.component.class")))
                .contains("TestComponent");
    }

    private Bundle findBundle(String bsn) {
        for (Bundle b : context.getBundles()) {
            if (bsn.equals(b.getSymbolicName())) {
                return b;
            }
        }
        return null;
    }
}
