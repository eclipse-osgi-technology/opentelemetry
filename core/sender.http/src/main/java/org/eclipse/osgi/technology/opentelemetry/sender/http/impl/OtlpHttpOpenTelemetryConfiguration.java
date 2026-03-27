package org.eclipse.osgi.technology.opentelemetry.sender.http.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;

/**
 * Configuration for the OTLP/HTTP OpenTelemetry exporter.
 * <p>
 * Properties can be set via OSGi ConfigAdmin using the PID
 * {@value #PID}.
 * <p>
 * The OTLP/HTTP exporter sends telemetry data (traces, metrics, logs) to an
 * OpenTelemetry Collector or compatible backend using the
 * <a href="https://opentelemetry.io/docs/specs/otel/protocol/exporter/">OTLP protocol</a>
 * over HTTP/protobuf.
 * The default endpoint is {@code http://localhost:4318}.
 * Signal-specific paths ({@code /v1/traces}, {@code /v1/metrics}, {@code /v1/logs})
 * are appended automatically.
 */
@ObjectClassDefinition(
    name = "OpenTelemetry OTLP/HTTP Exporter",
    description = "Exports telemetry (traces, metrics, logs) via OTLP/HTTP to an OpenTelemetry "
        + "Collector or compatible backend. Uses HTTP/protobuf transport on port 4318 by default."
)
public @interface OtlpHttpOpenTelemetryConfiguration {

    String COMPONENT_NAME = "otlp-http-opentelemetry";

    String PID = org.eclipse.osgi.technology.opentelemetry.sender.http.api.Constants.PID;

    // --- Resource Attributes ---

    @AttributeDefinition(
        name = "Service Name",
        description = "Logical name of the service reported in all telemetry data as the "
            + "'service.name' resource attribute. Can be overridden by the OTEL_SERVICE_NAME "
            + "environment variable."
    )
    String serviceName() default "osgi-application";

    @AttributeDefinition(
        name = "Service Version",
        description = "Version of the service reported as the 'service.version' resource attribute."
    )
    String serviceVersion() default "0.1.0";

    @AttributeDefinition(
        name = "Service Namespace",
        description = "Optional logical grouping for the service reported as the "
            + "'service.namespace' resource attribute. Leave empty if not needed."
    )
    String serviceNamespace() default "";

    @AttributeDefinition(
        name = "Additional Resource Attributes",
        description = "Extra resource attributes added to all telemetry as 'key=value' pairs. "
            + "These become part of the OpenTelemetry Resource and appear on every trace, metric, "
            + "and log record. Example: deployment.environment=production"
    )
    String[] additionalResourceAttributes() default {};

    // --- Transport ---

    @AttributeDefinition(
        name = "OTLP Endpoint",
        description = "The OTLP/HTTP base endpoint URL. Signal-specific paths "
            + "(/v1/traces, /v1/metrics, /v1/logs) are appended automatically. "
            + "Can be overridden by the OTEL_EXPORTER_OTLP_ENDPOINT environment variable. "
            + "Default: http://localhost:4318"
    )
    String endpoint() default "http://localhost:4318";

    @AttributeDefinition(
        name = "Export Timeout (ms)",
        description = "Maximum time in milliseconds to wait for each export request to complete. "
            + "If exceeded, the export is cancelled and data may be lost. Default: 10000"
    )
    long timeout() default 10000;

    @AttributeDefinition(
        name = "Connection Timeout (ms)",
        description = "Maximum time in milliseconds to wait when establishing a connection to the "
            + "OTLP endpoint. Default: 10000"
    )
    long connectTimeout() default 10000;

    @AttributeDefinition(
        name = "Compression",
        description = "Compression algorithm applied to the export payload before sending. "
            + "'gzip' reduces bandwidth usage at the cost of additional CPU. "
            + "'none' sends payloads uncompressed.",
        options = {
            @Option(label = "None", value = "none"),
            @Option(label = "Gzip", value = "gzip")
        }
    )
    String compression() default "none";

    @AttributeDefinition(
        name = "Headers",
        description = "Additional HTTP headers sent with every export request, specified as "
            + "'key=value' pairs. Useful for authentication tokens or routing metadata. "
            + "Example: Authorization=Bearer my-token"
    )
    String[] headers() default {};

    // --- Batch Processing ---

    @AttributeDefinition(
        name = "Span Schedule Delay (ms)",
        description = "Delay between consecutive span exports in milliseconds. "
            + "Lower values send data faster but increase network traffic. Default: 5000"
    )
    long spanScheduleDelay() default 5000;

    @AttributeDefinition(
        name = "Span Max Export Batch Size",
        description = "Maximum number of spans exported per batch. Default: 512"
    )
    int spanMaxExportBatchSize() default 512;

    @AttributeDefinition(
        name = "Span Max Queue Size",
        description = "Maximum number of spans queued before dropping. Default: 2048"
    )
    int spanMaxQueueSize() default 2048;

    @AttributeDefinition(
        name = "Metric Export Interval (ms)",
        description = "Interval between consecutive metric exports in milliseconds. "
            + "Lower values send metrics faster. Default: 60000"
    )
    long metricExportInterval() default 60000;

    @AttributeDefinition(
        name = "Log Schedule Delay (ms)",
        description = "Delay between consecutive log record exports in milliseconds. Default: 5000"
    )
    long logScheduleDelay() default 5000;

    @AttributeDefinition(
        name = "Log Max Export Batch Size",
        description = "Maximum number of log records exported per batch. Default: 512"
    )
    int logMaxExportBatchSize() default 512;

    // --- Metrics ---

    @AttributeDefinition(
        name = "Aggregation Temporality",
        description = "Controls how metric data points are aggregated over time. "
            + "'cumulative' (default) reports the running total since process start — "
            + "compatible with most backends including Prometheus. "
            + "'delta' reports only the change since the last export — preferred by some "
            + "commercial backends (e.g. Datadog, Dynatrace).",
        options = {
            @Option(label = "Cumulative", value = "cumulative"),
            @Option(label = "Delta", value = "delta")
        }
    )
    String aggregationTemporality() default "cumulative";

    // --- TLS ---

    @AttributeDefinition(
        name = "Trusted Certificates Path",
        description = "File path to a PEM-encoded certificate bundle containing trusted CA "
            + "certificates for TLS server verification. When set, these certificates are used "
            + "instead of the system default trust store. Required when the OTLP endpoint uses "
            + "a self-signed or private CA certificate. Leave empty to use system defaults."
    )
    String trustedCertificatesPath() default "";

    @AttributeDefinition(
        name = "Client Certificate Path",
        description = "File path to a PEM-encoded client certificate for mutual TLS (mTLS) "
            + "authentication. Must be used together with 'clientKeyPath'. Both are required "
            + "for mTLS; leave both empty to disable client certificate authentication."
    )
    String clientCertificatePath() default "";

    @AttributeDefinition(
        name = "Client Key Path",
        description = "File path to a PEM-encoded client private key in PKCS#8 format for "
            + "mutual TLS (mTLS) authentication. Must be used together with "
            + "'clientCertificatePath'."
    )
    String clientKeyPath() default "";
}
