# OpenTelemetry OSGi Examples

This folder contains example applications demonstrating OpenTelemetry OSGi integration.

## Modules

| Module | Description |
|---|---|
| [app](app/) | Complete example application with servlets, JAX-RS resources, and telemetry producers |

The example application demonstrates:
- Programmatic tracing with custom spans and nested operations
- Business metrics (counters, histograms, gauges)
- Log record production
- Context propagation between operations
- JDBC telemetry
- Servlet and JAX-RS endpoint instrumentation via weaving

### Running the Example

```bash
cd examples/app
mvn bnd-resolver:resolve bnd-run:run -pl examples/app
```

The application is configured via `example.bndrun` and connects to an OTLP endpoint at `http://localhost:4318` by default.
Use the [observability stack](../container/README.md) to visualize traces, metrics, and logs in Grafana.
