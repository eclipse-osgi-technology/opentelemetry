# OpenTelemetry for OSGi

Integrates [OpenTelemetry](https://opentelemetry.io/) with [OSGi](https://www.osgi.org/), providing traces, metrics, and logs for OSGi applications.

## Overview

This project publishes the OpenTelemetry SDK as an OSGi service and bridges various OSGi subsystems into OpenTelemetry telemetry signals.
It also provides zero-code bytecode instrumentation via the OSGi WeavingHook mechanism.

- **Maven GroupId**: `org.eclipse.osgi-technology.opentelemetry`
- **Package namespace**: `org.eclipse.osgi.technology.opentelemetry`
- **Java**: 21
- **OpenTelemetry**: 1.60.0
- **License**: EPL-2.0

## Repository Structure

```
opentelemetry/
├── pom.xml                          # Parent POM with dependency management
├── repack/                          # OpenTelemetry SDK repackaged as OSGi bundle
├── core/                            # Core runtime
│   ├── commons/                     # Shared base classes and provider registration
│   ├── sender.http/                 # OTLP/HTTP exporter
│   └── sender.logging/                  # Logging (stdout) exporter
├── integration/                     # OSGi subsystem bridges
│   ├── framework/                   # Bundle/service counts, events, inventory
│   ├── scr/                         # Declarative Services component metrics
│   ├── log/                         # Log Service → OpenTelemetry bridge
│   ├── cm/                          # Configuration Admin events and inventory
│   ├── mxbeans/                     # JVM runtime metrics (memory, CPU, threads, GC)
│   ├── felix.healthcheck/           # Felix Health Check metrics and tracing
│   ├── typedevent/                  # Typed Event bus observation
│   ├── jakarta.servlet/             # HTTP Whiteboard runtime introspection
│   └── jakarta.rest/                # JAX-RS Whiteboard runtime introspection
├── weaving/                         # OSGi WeavingHook bytecode instrumentation
│   ├── hook/                        # Host bundle: WeavingHook, ASM embedded
│   ├── jakarta.servlet/             # HttpServlet request tracing
│   ├── jakarta.rest/                # JAX-RS endpoint tracing
│   ├── jdbc/                        # JDBC operation tracing
│   └── scr/                         # DS lifecycle method tracing
├── examples/                        # Example application
│   └── app/                         # Demo servlets, JAX-RS resources, telemetry producers
└── container/                       # Observability stack (Podman pod)
```

## Modules

### Core

| Module | Description |
|---|---|
| [commons](core/commons/) | Shared base classes, provider registration for `TracerProvider`, `MeterProvider`, `LoggerProvider`, `ContextPropagators` |
| [sender.http](core/sender.http/) | OTLP/HTTP exporter with batch processing, compression, mTLS support |
| [sender.logging](core/sender.logging/) | Logging exporter (stdout via java.util.logging) for development/debugging |

The core modules publish `io.opentelemetry.api.OpenTelemetry` and individual provider interfaces (`TracerProvider`, `MeterProvider`, `LoggerProvider`, `ContextPropagators`) to the OSGi service registry.

### Integration

| Module | Description |
|---|---|
| [framework](integration/framework/) | Bundle/service counts, state distribution, event tracing, live inventory |
| [scr](integration/scr/) | DS component state metrics, inventory, and health traces |
| [log](integration/log/) | Log Service bridge forwarding `LogEntry` records to OTel |
| [cm](integration/cm/) | Configuration Admin change events, config counts, and inventory |
| [mxbeans](integration/mxbeans/) | JVM runtime metrics (memory, CPU, threads, GC, class loading, pools) |
| [felix.healthcheck](integration/felix.healthcheck/) | Felix Health Check execution metrics, tracing, and inventory |
| [typedevent](integration/typedevent/) | Typed Event bus observation with per-topic metrics and trace spans |
| [jakarta.servlet](integration/jakarta.servlet/) | HTTP Whiteboard runtime introspection (servlet contexts, servlets, filters) |
| [jakarta.rest](integration/jakarta.rest/) | JAX-RS Whiteboard runtime introspection (applications, resources, extensions) |

Each integration module consumes the `OpenTelemetry` service from the core runtime and produces domain-specific traces, metrics, and logs.

### Weaving

| Module | Description |
|---|---|
| [hook](weaving/hook/) | Host bundle: WeavingHook, WeaverRegistry, OpenTelemetryProxy, ASM embedded |
| [jakarta.servlet](weaving/jakarta.servlet/) | `HttpServlet` subclasses — HTTP request tracing and metrics |
| [jakarta.rest](weaving/jakarta.rest/) | `@Path`-annotated JAX-RS resources — REST endpoint tracing |
| [jdbc](weaving/jdbc/) | JDBC `Statement` implementations — database operation tracing |
| [scr](weaving/scr/) | DS component lifecycle methods — activate/deactivate/modified tracing |

The weaving system uses a host bundle + fragment bundle architecture.
Fragment bundles share the host's classloader, so plain Java `ServiceLoader` discovers weaver implementations without SPI Fly.

## Build

```bash
# Full build
mvn clean verify

# Build a single module
mvn clean verify -pl core/commons
```

Requires Java 21 and Maven 3.9+.

## License

[Eclipse Public License 2.0](LICENSE)
