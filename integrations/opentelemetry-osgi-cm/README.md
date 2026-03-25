# OpenTelemetry Config Admin Bridge

Bridges the [OSGi Configuration Admin](https://docs.osgi.org/specification/osgi.cmpn/8.0.0/service.cm.html) service into OpenTelemetry.
Listens for configuration change events and provides inventory snapshots.

## Features

- **Config Admin Metrics** — Gauges for total configuration and factory configuration counts, plus a counter for configuration events by type
- **Config Admin Events** — Trace spans for each configuration change event with PID, factory PID, and event type
- **Config Admin Inventory** — Structured log records with a snapshot of all configurations at activation time

## Generated Telemetry

### Traces

| Span Name Pattern | Kind | Description |
|---|---|---|
| `osgi.cm.updated` | `INTERNAL` | Configuration created or updated |
| `osgi.cm.deleted` | `INTERNAL` | Configuration deleted |
| `osgi.cm.location_changed` | `INTERNAL` | Configuration location changed |

### Metrics

| Metric | Type | Description |
|---|---|---|
| `osgi.cm.configuration.count` | Gauge | Total number of configurations |
| `osgi.cm.factory.count` | Gauge | Number of factory configurations |
| `osgi.cm.events` | Counter | Configuration events by type (CM_UPDATED, CM_DELETED, CM_LOCATION_CHANGED) |

### Logs

- **Config Admin Inventory** — Snapshot of all configurations with PID, factory PID, bundle location, property count, and property keys (values are excluded for security)

## Components

| Class | Description |
|---|---|
| `ConfigAdminMetricsComponent` | `ConfigurationListener` with async gauges and event counter |
| `ConfigAdminEventComponent` | `ConfigurationListener` creating trace spans for changes |
| `ConfigAdminInventoryComponent` | Queries `configAdmin.listConfigurations(null)` at activation |
