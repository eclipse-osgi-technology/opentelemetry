# OpenTelemetry for OSGi

Integrates [OpenTelemetry](https://opentelemetry.io/) with [OSGi](https://www.osgi.org/), providing traces, metrics, and logs for OSGi applications.

## Overview

This project publishes the OpenTelemetry SDK as an OSGi service and bridges various OSGi subsystems into OpenTelemetry telemetry signals.
It also provides zero-code bytecode instrumentation via the OSGi WeavingHook mechanism.

- **Maven GroupId**: `org.eclipse.osgi-technology`
- **Package namespace**: `org.eclipse.osgi.technology.opentelemetry`
- **Java**: 21
- **OpenTelemetry**: 1.49.0
- **License**: EPL-2.0

## Repository Structure

```
opentelemetry-osgi/
├── pom.xml                          # Parent POM with dependency management
├── core/                            # Core runtime
│   └── opentelemetry-osgi-runtime/  # OpenTelemetry SDK lifecycle and service publishing
├── integrations/                    # OSGi subsystem bridges
│   ├── opentelemetry-osgi-framework/       # Bundle/service counts, events, inventory
│   ├── opentelemetry-osgi-scr/             # Declarative Services component metrics
│   ├── opentelemetry-osgi-log/             # Log Service → OpenTelemetry bridge
│   ├── opentelemetry-osgi-felix-healthcheck/ # Felix Health Check metrics and tracing
│   ├── opentelemetry-osgi-cm/              # Configuration Admin events and inventory
│   ├── opentelemetry-osgi-typedevent/      # Typed Event bus observation
│   ├── opentelemetry-osgi-mxbeans/         # JVM runtime metrics (memory, CPU, threads, GC)
│   ├── opentelemetry-osgi-http-whiteboard/ # HTTP Whiteboard runtime introspection
│   └── opentelemetry-osgi-jaxrs-whiteboard/ # JAX-RS Whiteboard runtime introspection
├── weaving/                         # OSGi WeavingHook bytecode instrumentation
│   ├── opentelemetry-osgi-weaving/         # Host bundle: WeavingHook, ASM embedded
│   ├── opentelemetry-osgi-weaver-servlet/  # HttpServlet request tracing
│   ├── opentelemetry-osgi-weaver-jdbc/     # JDBC operation tracing
│   ├── opentelemetry-osgi-weaver-jaxrs/    # JAX-RS endpoint tracing
│   └── opentelemetry-osgi-weaver-scr/      # DS lifecycle method tracing
└── doc/                             # Documentation and images
```

## Modules

### Core

| Module | Description |
|---|---|
| [opentelemetry-osgi-runtime](core/opentelemetry-osgi-runtime/) | OpenTelemetry SDK lifecycle, service publishing, OTLP/HTTP and OTLP/gRPC export |

The runtime publishes `io.opentelemetry.api.OpenTelemetry` and individual provider interfaces (`TracerProvider`, `MeterProvider`, `LoggerProvider`, `ContextPropagators`) to the OSGi service registry.

### Integrations

| Module | Description |
|---|---|
| [opentelemetry-osgi-framework](integrations/opentelemetry-osgi-framework/) | Bundle/service counts, state distribution, event tracing, live inventory |
| [opentelemetry-osgi-scr](integrations/opentelemetry-osgi-scr/) | DS component state metrics, inventory, and health traces |
| [opentelemetry-osgi-log](integrations/opentelemetry-osgi-log/) | Log Service bridge forwarding `LogEntry` records to OTel |
| [opentelemetry-osgi-felix-healthcheck](integrations/opentelemetry-osgi-felix-healthcheck/) | Felix Health Check execution metrics, tracing, and inventory |
| [opentelemetry-osgi-cm](integrations/opentelemetry-osgi-cm/) | Configuration Admin change events, config counts, and inventory |
| [opentelemetry-osgi-typedevent](integrations/opentelemetry-osgi-typedevent/) | Typed Event bus observation with per-topic metrics and trace spans |
| [opentelemetry-osgi-mxbeans](integrations/opentelemetry-osgi-mxbeans/) | JVM runtime metrics (memory, CPU, threads, GC, class loading, pools) |
| [opentelemetry-osgi-http-whiteboard](integrations/opentelemetry-osgi-http-whiteboard/) | HTTP Whiteboard runtime introspection (servlet contexts, servlets, filters) |
| [opentelemetry-osgi-jaxrs-whiteboard](integrations/opentelemetry-osgi-jaxrs-whiteboard/) | JAX-RS Whiteboard runtime introspection (applications, resources, extensions) |

Each integration module consumes the `OpenTelemetry` service from the core runtime and produces domain-specific traces, metrics, and logs.

### Weaving

| Module | Description |
|---|---|
| [opentelemetry-osgi-weaving](weaving/opentelemetry-osgi-weaving/) | Host bundle: WeavingHook, WeaverRegistry, OpenTelemetryProxy, ASM embedded |
| [opentelemetry-osgi-weaver-servlet](weaving/opentelemetry-osgi-weaver-servlet/) | `HttpServlet` subclasses — HTTP request tracing and metrics |
| [opentelemetry-osgi-weaver-jdbc](weaving/opentelemetry-osgi-weaver-jdbc/) | JDBC `Statement` implementations — database operation tracing |
| [opentelemetry-osgi-weaver-jaxrs](weaving/opentelemetry-osgi-weaver-jaxrs/) | `@Path`-annotated JAX-RS resources — REST endpoint tracing |
| [opentelemetry-osgi-weaver-scr](weaving/opentelemetry-osgi-weaver-scr/) | DS component lifecycle methods — activate/deactivate/modified tracing |

The weaving system uses a host bundle + fragment bundle architecture.
Fragment bundles share the host's classloader, so plain Java `ServiceLoader` discovers weaver implementations without SPI Fly.

## Build

```bash
# Full build
mvn clean verify

# Build a single module
mvn clean verify -pl core/opentelemetry-osgi-runtime
```

Requires Java 21 and Maven 3.9+.

## License

[Eclipse Public License 2.0](LICENSE)
