# OpenTelemetry OSGi Core

This folder contains the core runtime module that provides the OpenTelemetry SDK as an OSGi service.

## Modules

| Module | Description | Details |
|---|---|---|
| [opentelemetry-osgi-runtime](opentelemetry-osgi-runtime/README.md) | OpenTelemetry SDK lifecycle, service publishing, OTLP/HTTP export | [Read more →](opentelemetry-osgi-runtime/README.md) |

The runtime publishes `io.opentelemetry.api.OpenTelemetry` and individual provider interfaces (`TracerProvider`, `MeterProvider`, `LoggerProvider`, `ContextPropagators`) to the OSGi service registry.
See the [module README](opentelemetry-osgi-runtime/README.md) for configuration details.
