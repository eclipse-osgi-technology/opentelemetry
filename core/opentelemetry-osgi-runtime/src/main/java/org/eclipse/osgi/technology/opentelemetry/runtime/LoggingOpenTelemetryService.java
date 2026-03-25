package org.eclipse.osgi.technology.opentelemetry.runtime;

import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.logging.SystemOutLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/**
 * OpenTelemetry SDK service that exports telemetry to stdout via
 * {@code java.util.logging}.
 * <p>
 * Useful for development and debugging without requiring an external collector.
 * Activates when a configuration with PID {@value LoggingOpenTelemetryConfiguration#PID}
 * exists.
 */
@Component(
    name = LoggingOpenTelemetryConfiguration.COMPONENT_NAME,
    service = OpenTelemetry.class,
    configurationPid = LoggingOpenTelemetryConfiguration.PID,
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    immediate = true
)
@Designate(ocd = LoggingOpenTelemetryConfiguration.class)
public class LoggingOpenTelemetryService extends AbstractOpenTelemetryService {

    private static final Logger LOG = Logger.getLogger(LoggingOpenTelemetryService.class.getName());

    @Activate
    public void activate(BundleContext context, LoggingOpenTelemetryConfiguration config) {
        LOG.info("Activating logging OpenTelemetry service");
        setSdk(buildSdk(context, config));
        LOG.info("Logging OpenTelemetry service activated with service.name="
            + resolveServiceName(config.serviceName()));
    }

    @Modified
    public void modified(BundleContext context, LoggingOpenTelemetryConfiguration config) {
        LOG.info("Reconfiguring logging OpenTelemetry service");
        setSdk(buildSdk(context, config));
        LOG.info("Logging OpenTelemetry service reconfigured");
    }

    @Deactivate
    public void deactivate() {
        LOG.info("Deactivating logging OpenTelemetry service");
        closeSdk();
    }

    private OpenTelemetrySdk buildSdk(BundleContext context, LoggingOpenTelemetryConfiguration config) {
        Resource resource = buildResource(context, resolveServiceName(config.serviceName()),
            config.serviceVersion(), config.serviceNamespace(), config.additionalResourceAttributes());

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
            .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(PeriodicMetricReader.create(LoggingMetricExporter.create()))
            .build();

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(SystemOutLogRecordExporter.create()))
            .build();

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .build();
    }
}
