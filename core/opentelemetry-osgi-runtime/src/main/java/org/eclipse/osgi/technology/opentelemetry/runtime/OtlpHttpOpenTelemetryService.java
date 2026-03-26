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
import org.osgi.service.metatype.annotations.Designate;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporterBuilder;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
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
 * OpenTelemetry SDK service that exports telemetry via OTLP/HTTP to an
 * OpenTelemetry Collector or compatible backend.
 * <p>
 * This service has a higher service ranking than the logging exporter,
 * ensuring it is preferred by consumers when both are active.
 * <p>
 * The OTLP endpoint can be overridden via the {@code OTEL_EXPORTER_OTLP_ENDPOINT}
 * environment variable.
 * Activates when a configuration with PID {@value OtlpHttpOpenTelemetryConfiguration#PID}
 * exists.
 */
@Component(
    name = OtlpHttpOpenTelemetryConfiguration.COMPONENT_NAME,
    service = OpenTelemetry.class,
    configurationPid = OtlpHttpOpenTelemetryConfiguration.PID,
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    property = "service.ranking:Integer=100"
)
@Designate(ocd = OtlpHttpOpenTelemetryConfiguration.class)
public class OtlpHttpOpenTelemetryService extends AbstractOpenTelemetryService {

    private static final Logger LOG = Logger.getLogger(OtlpHttpOpenTelemetryService.class.getName());

    @Activate
    public void activate(BundleContext context, OtlpHttpOpenTelemetryConfiguration config) {
        LOG.info("Activating OTLP/HTTP OpenTelemetry service");
        setSdk(buildSdk(context, config));
        LOG.info("OTLP/HTTP OpenTelemetry service activated with service.name="
            + resolveServiceName(config.serviceName()) + ", endpoint=" + resolveEndpoint(config.endpoint()));
    }

    @Modified
    public void modified(BundleContext context, OtlpHttpOpenTelemetryConfiguration config) {
        LOG.info("Reconfiguring OTLP/HTTP OpenTelemetry service");
        setSdk(buildSdk(context, config));
        LOG.info("OTLP/HTTP OpenTelemetry service reconfigured");
    }

    @Deactivate
    public void deactivate() {
        LOG.info("Deactivating OTLP/HTTP OpenTelemetry service");
        closeSdk();
    }

    private OpenTelemetrySdk buildSdk(BundleContext context, OtlpHttpOpenTelemetryConfiguration config) {
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
        OtlpHttpSpanExporterBuilder spanBuilder = OtlpHttpSpanExporter.builder()
            .setEndpoint(endpoint + "/v1/traces")
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
        OtlpHttpMetricExporterBuilder metricBuilder = OtlpHttpMetricExporter.builder()
            .setEndpoint(endpoint + "/v1/metrics")
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
        OtlpHttpLogRecordExporterBuilder logBuilder = OtlpHttpLogRecordExporter.builder()
            .setEndpoint(endpoint + "/v1/logs")
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

    private void configureTls(OtlpHttpSpanExporterBuilder builder,
            byte[] trustedCerts, byte[] clientCert, byte[] clientKey) {
        if (trustedCerts != null) {
            builder.setTrustedCertificates(trustedCerts);
        }
        if (clientCert != null && clientKey != null) {
            builder.setClientTls(clientCert, clientKey);
        }
    }

    private void configureTls(OtlpHttpMetricExporterBuilder builder,
            byte[] trustedCerts, byte[] clientCert, byte[] clientKey) {
        if (trustedCerts != null) {
            builder.setTrustedCertificates(trustedCerts);
        }
        if (clientCert != null && clientKey != null) {
            builder.setClientTls(clientCert, clientKey);
        }
    }

    private void configureTls(OtlpHttpLogRecordExporterBuilder builder,
            byte[] trustedCerts, byte[] clientCert, byte[] clientKey) {
        if (trustedCerts != null) {
            builder.setTrustedCertificates(trustedCerts);
        }
        if (clientCert != null && clientKey != null) {
            builder.setClientTls(clientCert, clientKey);
        }
    }
}
