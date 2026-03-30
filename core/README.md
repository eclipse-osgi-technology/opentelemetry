# OpenTelemetry OSGi Core

This folder contains the core runtime modules that provide the OpenTelemetry SDK as an OSGi service.

## Modules

| Module | Description | Details |
|---|---|---|
| [commons](commons/) | Shared base classes and provider registration for `TracerProvider`, `MeterProvider`, `LoggerProvider`, `ContextPropagators` | [Read more](commons/) |
| [sender.http](sender.http/) | OTLP/HTTP exporter with batch processing, compression, and mTLS support | [Read more](sender.http/) |
| [sender.logging](sender.logging/) | Logging exporter (stdout via java.util.logging) for development/debugging | [Read more](sender.logging/) |

The sender modules publish `io.opentelemetry.api.OpenTelemetry` and individual provider interfaces (`TracerProvider`, `MeterProvider`, `LoggerProvider`, `ContextPropagators`) to the OSGi service registry.
The `sender.http` exporter has a higher service ranking (100) than `sender.logging` (0), so it is preferred when both are configured.
