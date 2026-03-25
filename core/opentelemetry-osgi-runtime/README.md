# OpenTelemetry OSGi Runtime

Provides configured [OpenTelemetry](https://opentelemetry.io/) SDK instances as OSGi services using Declarative Services.
This is the core module that all other integration modules depend on.

## Features

- Supports multiple exporter types, each as a separate service with its own configuration PID
- Publishes `io.opentelemetry.api.OpenTelemetry` to the OSGi service registry
- Registers individual provider interfaces as separate OSGi services for direct consumption:
  - `io.opentelemetry.api.trace.TracerProvider`
  - `io.opentelemetry.api.metrics.MeterProvider`
  - `io.opentelemetry.api.logs.LoggerProvider`
  - `io.opentelemetry.context.propagation.ContextPropagators`
- Provider sub-services include an `opentelemetry.name` property for targeting a specific exporter
- Multiple exporters can be active simultaneously (OTLP variants have higher service ranking)
- All configuration annotations use OSGi Metatype annotations for runtime discoverability

## Exporter Types

| Exporter | Component Name | Configuration PID | Default Port | Description |
|---|---|---|---|---|
| Logging | `logging-opentelemetry` | `...runtime.logging` | — | Exports to stdout via `java.util.logging` |
| OTLP/HTTP | `otlp-http-opentelemetry` | `...runtime.otlp.http` | 4318 | Exports via OTLP/HTTP to a collector |
| OTLP/gRPC | `otlp-grpc-opentelemetry` | `...runtime.otlp.grpc` | 4317 | Exports via OTLP/gRPC to a collector |

Each exporter activates only when its configuration PID exists in ConfigAdmin.

## Configuration

All configuration interfaces use OSGi Metatype annotations (`@ObjectClassDefinition`, `@AttributeDefinition`) and are discoverable via management tools.

### Logging Exporter

PID: `org.eclipse.osgi.technology.opentelemetry.runtime.logging`

| Property | Default | Description |
|---|---|---|
| `serviceName` | `osgi-application` | Service name in telemetry data |
| `serviceVersion` | `0.1.0` | Service version resource attribute |
| `serviceNamespace` | (empty) | Logical grouping (optional) |
| `additionalResourceAttributes` | (empty) | Extra key=value resource attributes |

### OTLP/HTTP Exporter

PID: `org.eclipse.osgi.technology.opentelemetry.runtime.otlp.http`

| Property | Type | Default | Description |
|---|---|---|---|
| `serviceName` | String | `osgi-application` | Service name in telemetry data |
| `serviceVersion` | String | `0.1.0` | Service version resource attribute |
| `serviceNamespace` | String | (empty) | Logical grouping (optional) |
| `endpoint` | String | `http://localhost:4318` | OTLP/HTTP collector endpoint (signal paths appended automatically) |
| `timeout` | long | `10000` | Export timeout in milliseconds |
| `connectTimeout` | long | `10000` | Connection timeout in milliseconds |
| `compression` | String | `none` | Compression: `none` or `gzip` |
| `headers` | String[] | (empty) | Additional HTTP headers as `key=value` pairs |
| `aggregationTemporality` | String | `cumulative` | Metric aggregation: `cumulative` or `delta` |
| `trustedCertificatesPath` | String | (empty) | PEM file with trusted CA certificates for TLS |
| `clientCertificatePath` | String | (empty) | PEM file with client certificate for mTLS |
| `clientKeyPath` | String | (empty) | PEM file with client private key (PKCS#8) for mTLS |
| `additionalResourceAttributes` | String[] | (empty) | Extra key=value resource attributes |

### OTLP/gRPC Exporter

PID: `org.eclipse.osgi.technology.opentelemetry.runtime.otlp.grpc`

Same configuration options as OTLP/HTTP, with these differences:
- Default `endpoint`: `http://localhost:4317` (gRPC port)
- The endpoint is used as-is (gRPC multiplexes all signals on one connection)

### Environment Variable Overrides

| Variable | Overrides | Scope |
|---|---|---|
| `OTEL_SERVICE_NAME` | `serviceName` | All exporters |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `endpoint` | OTLP exporters only |

## Resource Attributes

The runtime automatically enriches all telemetry with OSGi framework information as [OpenTelemetry Resource](https://opentelemetry.io/docs/concepts/resources/) attributes.
Resource attributes are attached to **every** trace, metric, and log record — they are the primary mechanism for identifying and correlating telemetry from different service instances.

| Attribute | Source | Description |
|---|---|---|
| `service.name` | `OTEL_SERVICE_NAME` env or `serviceName` config | Identifies the logical service |
| `service.version` | `serviceVersion` config | Service version |
| `service.namespace` | `serviceNamespace` config | Logical grouping (optional) |
| `service.instance.id` | `org.osgi.framework.uuid` | Unique instance identifier (framework UUID) |
| `osgi.framework.uuid` | `org.osgi.framework.uuid` | OSGi framework UUID |
| `osgi.framework.vendor` | `org.osgi.framework.vendor` | Framework implementation vendor |
| `osgi.framework.version` | `org.osgi.framework.version` | Framework specification version |

The `service.instance.id` is the standard OpenTelemetry semantic convention for distinguishing multiple instances of the same service.
It is automatically set to the OSGi framework UUID, which is unique per framework launch.
In Prometheus, `service.name` maps to the `job` label and `service.instance.id` maps to the `instance` label.

Additional resource attributes can be configured via `additionalResourceAttributes` (format: `key=value`).

## Components

| Class | Description |
|---|---|
| `AbstractOpenTelemetryService` | Base class with resource building, SDK lifecycle, and delegation |
| `LoggingOpenTelemetryService` | DS component publishing `OpenTelemetry` with logging exporters |
| `OtlpHttpOpenTelemetryService` | DS component publishing `OpenTelemetry` with OTLP/HTTP exporters |
| `OtlpGrpcOpenTelemetryService` | DS component publishing `OpenTelemetry` with OTLP/gRPC exporters |
| `OpenTelemetryProviderRegistration` | DS component registering individual provider services per exporter |
| `LoggingOpenTelemetryConfiguration` | Metatype-annotated config for the logging exporter |
| `OtlpHttpOpenTelemetryConfiguration` | Metatype-annotated config for the OTLP/HTTP exporter |
| `OtlpGrpcOpenTelemetryConfiguration` | Metatype-annotated config for the OTLP/gRPC exporter |

## Usage

Other bundles consume the service via Declarative Services:

```java
@Reference
private OpenTelemetry openTelemetry;

// Or consume individual providers directly:
@Reference
private TracerProvider tracerProvider;
```

### Targeting a Specific Exporter

When multiple exporters are active, consumers can target a specific one using the
`opentelemetry.name` service property:

```java
@Reference(target = "(opentelemetry.name=otlp-http-opentelemetry)")
private TracerProvider tracerProvider;
```

Without a target filter, consumers get the highest-ranked exporter (OTLP variants by default, ranking=100).

## Adding New Exporters

To add a new exporter type:

1. Create a configuration annotation with `COMPONENT_NAME` and `PID` constants, annotated with `@ObjectClassDefinition`
2. Create a service class extending `AbstractOpenTelemetryService`, annotated with `@Designate(ocd = YourConfig.class)`
3. Use `@Component(name = YourConfig.COMPONENT_NAME, configurationPid = YourConfig.PID, ...)`
4. Add any required exporter dependencies to `pom.xml`
