package org.eclipse.osgi.technology.opentelemetry.runtime;

import java.time.Duration;
import java.util.Map;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.propertytypes.ServiceRanking;
import org.osgi.service.metatype.annotations.Designate;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporterBuilder;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

/**
 * OpenTelemetry SDK service that exports telemetry via OTLP/gRPC to an
 * OpenTelemetry Collector or compatible backend.
 * <p>
 * This service has a higher service ranking than the logging exporter,
 * ensuring it is preferred by consumers when both are active.
 * <p>
 * The OTLP endpoint can be overridden via the {@code OTEL_EXPORTER_OTLP_ENDPOINT}
 * environment variable.
 * Activates when a configuration with PID {@value OtlpGrpcOpenTelemetryConfiguration#PID}
 * exists.
 */
@Component(
    name = OtlpGrpcOpenTelemetryConfiguration.COMPONENT_NAME,
    service = OpenTelemetry.class,
    configurationPid = OtlpGrpcOpenTelemetryConfiguration.PID,
    configurationPolicy = ConfigurationPolicy.REQUIRE
)
@ServiceRanking(100)
@Designate(ocd = OtlpGrpcOpenTelemetryConfiguration.class)
public class OtlpGrpcOpenTelemetryService extends AbstractOpenTelemetryService {

    private static final Logger LOG = Logger.getLogger(OtlpGrpcOpenTelemetryService.class.getName());

    @Activate
    public void activate(BundleContext context, OtlpGrpcOpenTelemetryConfiguration config) {
        LOG.info("Activating OTLP/gRPC OpenTelemetry service");
        setSdk(buildSdk(context, config));
        LOG.info("OTLP/gRPC OpenTelemetry service activated with service.name="
            + resolveServiceName(config.serviceName()) + ", endpoint=" + resolveEndpoint(config.endpoint()));
    }

    @Modified
    public void modified(BundleContext context, OtlpGrpcOpenTelemetryConfiguration config) {
        LOG.info("Reconfiguring OTLP/gRPC OpenTelemetry service");
        setSdk(buildSdk(context, config));
        LOG.info("OTLP/gRPC OpenTelemetry service reconfigured");
    }

    @Deactivate
    public void deactivate() {
        LOG.info("Deactivating OTLP/gRPC OpenTelemetry service");
        closeSdk();
    }

    private OpenTelemetrySdk buildSdk(BundleContext context, OtlpGrpcOpenTelemetryConfiguration config) {
        String endpoint = resolveEndpoint(config.endpoint());
        Duration timeout = toDuration(config.timeout());
        Duration connectTimeout = toDuration(config.connectTimeout());
        String compression = config.compression();
        Map<String, String> headers = parseKeyValuePairs(config.headers());
        byte[] trustedCerts = readPemFile(config.trustedCertificatesPath());
        byte[] clientCert = readPemFile(config.clientCertificatePath());
        byte[] clientKey = readPemFile(config.clientKeyPath());
        boolean deltaTemporality = "delta".equalsIgnoreCase(config.aggregationTemporality());

        Resource resource = buildResource(context, resolveServiceName(config.serviceName()),
            config.serviceVersion(), config.serviceNamespace(), config.additionalResourceAttributes());

        // Span exporter
        OtlpGrpcSpanExporterBuilder spanBuilder = OtlpGrpcSpanExporter.builder()
            .setEndpoint(endpoint)
            .setTimeout(timeout)
            .setConnectTimeout(connectTimeout)
            .setCompression(compression);
        headers.forEach(spanBuilder::addHeader);
        configureTls(spanBuilder, trustedCerts, clientCert, clientKey);

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(spanBuilder.build()).build())
            .build();

        // Metric exporter
        OtlpGrpcMetricExporterBuilder metricBuilder = OtlpGrpcMetricExporter.builder()
            .setEndpoint(endpoint)
            .setTimeout(timeout)
            .setConnectTimeout(connectTimeout)
            .setCompression(compression);
        if (deltaTemporality) {
            metricBuilder.setAggregationTemporalitySelector(AggregationTemporalitySelector.deltaPreferred());
        }
        headers.forEach(metricBuilder::addHeader);
        configureTls(metricBuilder, trustedCerts, clientCert, clientKey);

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(PeriodicMetricReader.create(metricBuilder.build()))
            .build();

        // Log exporter
        OtlpGrpcLogRecordExporterBuilder logBuilder = OtlpGrpcLogRecordExporter.builder()
            .setEndpoint(endpoint)
            .setTimeout(timeout)
            .setConnectTimeout(connectTimeout)
            .setCompression(compression);
        headers.forEach(logBuilder::addHeader);
        configureTls(logBuilder, trustedCerts, clientCert, clientKey);

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(BatchLogRecordProcessor.builder(logBuilder.build()).build())
            .build();

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .build();
    }

    private void configureTls(OtlpGrpcSpanExporterBuilder builder,
            byte[] trustedCerts, byte[] clientCert, byte[] clientKey) {
        if (trustedCerts != null) {
            builder.setTrustedCertificates(trustedCerts);
        }
        if (clientCert != null && clientKey != null) {
            builder.setClientTls(clientCert, clientKey);
        }
    }

    private void configureTls(OtlpGrpcMetricExporterBuilder builder,
            byte[] trustedCerts, byte[] clientCert, byte[] clientKey) {
        if (trustedCerts != null) {
            builder.setTrustedCertificates(trustedCerts);
        }
        if (clientCert != null && clientKey != null) {
            builder.setClientTls(clientCert, clientKey);
        }
    }

    private void configureTls(OtlpGrpcLogRecordExporterBuilder builder,
            byte[] trustedCerts, byte[] clientCert, byte[] clientKey) {
        if (trustedCerts != null) {
            builder.setTrustedCertificates(trustedCerts);
        }
        if (clientCert != null && clientKey != null) {
            builder.setClientTls(clientCert, clientKey);
        }
    }
}
