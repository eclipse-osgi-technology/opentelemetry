package org.eclipse.osgi.technology.opentelemetry.runtime;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;

/**
 * Configuration for the OTLP/gRPC OpenTelemetry exporter.
 * <p>
 * Properties can be set via OSGi ConfigAdmin using the PID
 * {@value #PID}.
 * <p>
 * The OTLP/gRPC exporter sends telemetry data (traces, metrics, logs) to an
 * OpenTelemetry Collector or compatible backend using the
 * <a href="https://opentelemetry.io/docs/specs/otel/protocol/exporter/">OTLP protocol</a>
 * over gRPC (HTTP/2).
 * The default endpoint is {@code http://localhost:4317}.
 * Unlike the HTTP exporter, gRPC uses a single endpoint for all signal types
 * (the service path is determined by the gRPC service definition).
 */
@ObjectClassDefinition(
    name = "OpenTelemetry OTLP/gRPC Exporter",
    description = "Exports telemetry (traces, metrics, logs) via OTLP/gRPC to an OpenTelemetry "
        + "Collector or compatible backend. Uses gRPC (HTTP/2) transport on port 4317 by default."
)
public @interface OtlpGrpcOpenTelemetryConfiguration {

    String COMPONENT_NAME = "otlp-grpc-opentelemetry";

    String PID = "org.eclipse.osgi.technology.opentelemetry.runtime.otlp.grpc";

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
        description = "The OTLP/gRPC endpoint URL. Unlike HTTP, gRPC uses a single endpoint "
            + "for all signal types (traces, metrics, logs). The gRPC service path is determined "
            + "by the protobuf service definition. "
            + "Can be overridden by the OTEL_EXPORTER_OTLP_ENDPOINT environment variable. "
            + "Default: http://localhost:4317"
    )
    String endpoint() default "http://localhost:4317";

    @AttributeDefinition(
        name = "Export Timeout (ms)",
        description = "Maximum time in milliseconds to wait for each export RPC to complete. "
            + "If exceeded, the export is cancelled and data may be lost. Default: 10000"
    )
    long timeout() default 10000;

    @AttributeDefinition(
        name = "Connection Timeout (ms)",
        description = "Maximum time in milliseconds to wait when establishing a gRPC connection "
            + "to the OTLP endpoint. Default: 10000"
    )
    long connectTimeout() default 10000;

    @AttributeDefinition(
        name = "Compression",
        description = "Compression algorithm applied to the gRPC message payload before sending. "
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
        description = "Additional gRPC metadata headers sent with every export RPC, specified as "
            + "'key=value' pairs. Useful for authentication tokens or routing metadata. "
            + "Example: Authorization=Bearer my-token"
    )
    String[] headers() default {};

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
