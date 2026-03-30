# OpenTelemetry OSGi Integrations

This folder contains integration bundles that bridge various OSGi subsystems into OpenTelemetry telemetry signals.
Each module consumes the `OpenTelemetry` service published by the [core runtime](../core/README.md) and produces domain-specific traces, metrics, and logs.

## Modules

| Module | Description | Details |
|---|---|---|
| [framework](framework/README.md) | Bundle/service counts, state distribution, event tracing, live inventory | [Read more](framework/README.md) |
| [scr](scr/README.md) | DS component state metrics, inventory, and health traces via SCR Introspection API | [Read more](scr/README.md) |
| [log](log/README.md) | Log Service bridge forwarding `LogEntry` records to OTel with severity mapping | [Read more](log/README.md) |
| [cm](cm/README.md) | Configuration Admin change events, config counts, and inventory | [Read more](cm/README.md) |
| [mxbeans](mxbeans/README.md) | JVM runtime metrics (memory, CPU, threads, GC, class loading, pools) | [Read more](mxbeans/README.md) |
| [felix.healthcheck](felix.healthcheck/README.md) | Felix Health Check execution metrics, tracing, and inventory | [Read more](felix.healthcheck/README.md) |
| [typedevent](typedevent/README.md) | Typed Event bus observation with per-topic metrics and trace spans | [Read more](typedevent/README.md) |
| [jakarta.servlet](jakarta.servlet/README.md) | HTTP Whiteboard runtime introspection (servlet contexts, servlets, filters, listeners) | [Read more](jakarta.servlet/README.md) |
| [jakarta.rest](jakarta.rest/README.md) | JAX-RS Whiteboard runtime introspection (applications, resources, extensions) | [Read more](jakarta.rest/README.md) |

Each module README contains detailed telemetry tables, component descriptions, and configuration options.
