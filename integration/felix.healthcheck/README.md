# OpenTelemetry Felix Health Check Bridge

Bridges [Apache Felix Health Checks](https://felix.apache.org/documentation/subprojects/apache-felix-healthcheck.html) into OpenTelemetry.
Periodically executes all registered health checks and publishes results as traces, metrics, and inventory logs.

## Features

- **Health Check Metrics** — Gauges for total health check count, status distribution (OK, WARN, CRITICAL, TEMPORARILY_UNAVAILABLE, HEALTH_CHECK_ERROR), and execution duration
- **Health Check Tracing** — Periodic execution of all registered health checks, producing a parent span `osgi.hc.execution` with child spans per individual check result
- **Health Check Inventory** — Structured log records listing all registered health checks with their names, tags, and bundle info at activation time

## Generated Telemetry

### Traces

| Span Name | Kind | Description |
|---|---|---|
| `osgi.hc.execution` | `INTERNAL` | Parent span for periodic health check execution |
| `osgi.hc.<checkName>` | `INTERNAL` | Child span per individual health check result |

### Metrics

| Metric | Type | Description |
|---|---|---|
| `osgi.hc.count` | Gauge | Total number of registered health checks |
| `osgi.hc.status` | Gauge | Health check count by status (OK, WARN, CRITICAL, etc.) |
| `osgi.hc.duration` | Gauge | Total health check execution duration in milliseconds |

### Logs

- **Health Check Inventory** — Snapshot of all `HealthCheck` service references with name, tags, and registering bundle

## Components

| Class | Description |
|---|---|
| `HealthCheckMetricsComponent` | Periodic execution via `ScheduledExecutorService`, caches results for async gauges |
| `HealthCheckTracingComponent` | Creates trace spans for each execution cycle |
| `HealthCheckInventoryComponent` | Emits inventory log records at activation |

## General Checks

Apache Felix provides general checks (CPU, memory, thread usage, disk space, bundles started, framework start).
Most general checks use `configurationPolicy=REQUIRE` — they only activate when a `.cfg` file is deployed.
