# OpenTelemetry OSGi Integrations

This folder contains integration bundles that bridge various OSGi subsystems into OpenTelemetry telemetry signals.
Each module consumes the `OpenTelemetry` service published by the [core runtime](../core/README.md) and produces domain-specific traces, metrics, and logs.

## Modules

| Module | Description | Details |
|---|---|---|
| [opentelemetry-osgi-framework](opentelemetry-osgi-framework/README.md) | Bundle/service counts, state distribution, event tracing, live inventory | [Read more →](opentelemetry-osgi-framework/README.md) |
| [opentelemetry-osgi-scr](opentelemetry-osgi-scr/README.md) | DS component state metrics, inventory, and health traces via SCR Introspection API | [Read more →](opentelemetry-osgi-scr/README.md) |
| [opentelemetry-osgi-log](opentelemetry-osgi-log/README.md) | Log Service bridge forwarding `LogEntry` records to OTel with severity mapping | [Read more →](opentelemetry-osgi-log/README.md) |
| [opentelemetry-osgi-felix-healthcheck](opentelemetry-osgi-felix-healthcheck/README.md) | Felix Health Check execution metrics, tracing, and inventory | [Read more →](opentelemetry-osgi-felix-healthcheck/README.md) |
| [opentelemetry-osgi-cm](opentelemetry-osgi-cm/README.md) | Configuration Admin change events, config counts, and inventory | [Read more →](opentelemetry-osgi-cm/README.md) |
| [opentelemetry-osgi-typedevent](opentelemetry-osgi-typedevent/README.md) | Typed Event bus observation with per-topic metrics and trace spans | [Read more →](opentelemetry-osgi-typedevent/README.md) |
| [opentelemetry-osgi-mxbeans](opentelemetry-osgi-mxbeans/README.md) | JVM runtime metrics (memory, CPU, threads, GC, class loading, pools) | [Read more →](opentelemetry-osgi-mxbeans/README.md) |
| [opentelemetry-osgi-http-whiteboard](opentelemetry-osgi-http-whiteboard/README.md) | HTTP Whiteboard runtime introspection (servlet contexts, servlets, filters, listeners) | [Read more →](opentelemetry-osgi-http-whiteboard/README.md) |
| [opentelemetry-osgi-jaxrs-whiteboard](opentelemetry-osgi-jaxrs-whiteboard/README.md) | JAX-RS Whiteboard runtime introspection (applications, resources, extensions) | [Read more →](opentelemetry-osgi-jaxrs-whiteboard/README.md) |

Each module README contains detailed telemetry tables, component descriptions, and configuration options.
