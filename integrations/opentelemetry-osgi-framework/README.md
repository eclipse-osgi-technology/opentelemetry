# OpenTelemetry OSGi Framework Bridge

Bridges the OSGi core framework state into OpenTelemetry telemetry signals.
This module registers as a `BundleListener` and `ServiceListener` to capture framework events in real time and provides live inventory snapshots.

## Features

- **Framework Metrics** — Async gauges for total bundle and service counts
- **Framework Events** — Trace spans for bundle install/start/stop/uninstall and service register/unregister/modify events
- **Bundle Inventory** — Structured log records with a snapshot of all installed bundles plus change tracking via `SynchronousBundleListener`
- **Service Inventory** — Structured log records with a snapshot of all registered services plus change tracking via `ServiceListener`

## Generated Telemetry

### Traces

| Span Name Pattern | Kind | Description |
|---|---|---|
| `osgi.bundle.<event>` | `INTERNAL` | Bundle lifecycle events (installed, started, stopped, etc.) |
| `osgi.service.<event>` | `INTERNAL` | Service registry events (registered, unregistering, modified) |

### Metrics

| Metric | Type | Description |
|---|---|---|
| `osgi.bundle.count` | Gauge | Total number of installed bundles |
| `osgi.bundle.states` | Gauge | Bundle count per state (ACTIVE, RESOLVED, INSTALLED, etc.) |
| `osgi.service.count` | Gauge | Total number of registered services |

### Logs

- **Bundle Inventory** — Full bundle snapshot at activation + incremental change records
- **Service Inventory** — Full service snapshot at activation + incremental change records with using-bundles info

## Components

| Class | Description |
|---|---|
| `FrameworkMetricsComponent` | Async gauges for bundle and service counts |
| `FrameworkEventComponent` | Trace spans for bundle and service events |
| `BundleInventoryComponent` | `SynchronousBundleListener` capturing bundle state snapshots |
| `ServiceInventoryComponent` | `ServiceListener` capturing service registry changes |
| `BundleStateUtil` | Utility mapping bundle state integers to human-readable names |
| `BundleInfo` | Immutable record capturing bundle state snapshots |
