package org.eclipse.osgi.technology.opentelemetry.commons.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Hashtable;

import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.service.ServiceAware;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;

public class ProviderRegistrationIntegrationTest {

    @InjectBundleContext
    BundleContext bc;

    private OpenTelemetry createDistinctNoop() {
        return OpenTelemetrySdk.builder().build();
    }

    @Test
    void providersAppearWhenOpenTelemetryIsRegistered(
            @InjectService(cardinality = 0) ServiceAware<TracerProvider> tracerAware,
            @InjectService(cardinality = 0) ServiceAware<MeterProvider> meterAware,
            @InjectService(cardinality = 0) ServiceAware<LoggerProvider> loggerAware,
            @InjectService(cardinality = 0) ServiceAware<ContextPropagators> propagatorsAware)
            throws Exception {

        assertThat(tracerAware.size()).isZero();

        var props = new Hashtable<String, Object>();
        props.put("component.name", "test-otel");
        ServiceRegistration<OpenTelemetry> reg =
                bc.registerService(OpenTelemetry.class, createDistinctNoop(), props);

        assertThat(tracerAware.waitForService(300)).isNotNull();
        assertThat(meterAware.waitForService(300)).isNotNull();
        assertThat(loggerAware.waitForService(300)).isNotNull();
        assertThat(propagatorsAware.waitForService(300)).isNotNull();

        assertThat(tracerAware.size()).isEqualTo(1);
        assertThat(meterAware.size()).isEqualTo(1);
        assertThat(loggerAware.size()).isEqualTo(1);
        assertThat(propagatorsAware.size()).isEqualTo(1);

        assertThat(tracerAware.getServiceReference().getProperty("opentelemetry.name"))
                .isEqualTo("test-otel");

        reg.unregister();

        Thread.sleep(1000);
        assertThat(tracerAware.size()).isZero();
    }

    @Test
    void multipleOpenTelemetryInstancesCreateMultipleProviders(
            @InjectService(cardinality = 0) ServiceAware<TracerProvider> tracerAware)
            throws Exception {

        var props1 = new Hashtable<String, Object>();
        props1.put("component.name", "otel-one");
        ServiceRegistration<OpenTelemetry> reg1 =
                bc.registerService(OpenTelemetry.class, createDistinctNoop(), props1);

        assertThat(tracerAware.waitForService(300)).isNotNull();
        assertThat(tracerAware.size()).isEqualTo(1);

        var props2 = new Hashtable<String, Object>();
        props2.put("component.name", "otel-two");
        ServiceRegistration<OpenTelemetry> reg2 =
                bc.registerService(OpenTelemetry.class, createDistinctNoop(), props2);

        Thread.sleep(2000);
        assertThat(tracerAware.size()).isEqualTo(2);

        reg1.unregister();
        Thread.sleep(1000);
        assertThat(tracerAware.size()).isEqualTo(1);

        reg2.unregister();
        Thread.sleep(1000);
        assertThat(tracerAware.size()).isZero();
    }
}
