# OpenTelemetry OSGi SCR Bridge

Exposes OSGi Declarative Services (SCR) component state as OpenTelemetry telemetry using the [SCR Introspection API](https://docs.osgi.org/specification/osgi.cmpn/8.0.0/service.component.html#service.component-introspection) (`ServiceComponentRuntime`).

## Features

- **SCR Metrics** — Async gauges for component states (active, satisfied, unsatisfied configuration, unsatisfied reference, failed activation)
- **SCR Inventory** — Structured log records enumerating all DS component descriptions and their current configurations
- **SCR Health Check** — Periodic health trace that flags non-active components with their unsatisfied references

## Generated Telemetry

### Traces

| Span Name | Kind | Description |
|---|---|---|
| `osgi.scr.healthcheck` | `INTERNAL` | Periodic check of all DS component states |

### Metrics

| Metric | Type | Description |
|---|---|---|
| `osgi.scr.component.count` | Gauge | Total number of DS component descriptions |
| `osgi.scr.component.states` | Gauge | Component configuration count per state |

State values: `ACTIVE` (8), `SATISFIED` (4), `UNSATISFIED_REFERENCE` (2), `UNSATISFIED_CONFIGURATION` (1), `FAILED_ACTIVATION` (16).

### Logs

- **SCR Inventory** — Snapshot of all component descriptions with name, state, implementation class, and service interfaces

## Components

| Class | Description |
|---|---|
| `ScrMetricsComponent` | Async gauges querying `ServiceComponentRuntime` on each collection cycle |
| `ScrInventoryComponent` | Emits structured log records for all DS components at activation |
| `ScrHealthCheckComponent` | Periodic trace flagging non-active components |
