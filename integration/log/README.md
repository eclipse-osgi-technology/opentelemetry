# OpenTelemetry OSGi Log Bridge

Forwards OSGi Log Service entries to OpenTelemetry using the [OSGi Log Service](https://docs.osgi.org/specification/osgi.core/8.0.0/service.log.html) (`LogReaderService`).
Each `LogEntry` is converted to an OpenTelemetry log record with full context enrichment.

## Features

- **Log Bridge** — Registers as a `LogListener` on `LogReaderService` and forwards each `LogEntry` as an OpenTelemetry log record
- **Log Metrics** — Counters for log entries by level and error counters by bundle name

## Generated Telemetry

### Metrics

| Metric | Type | Description |
|---|---|---|
| `osgi.log.entries` | Counter | Log entries by level (AUDIT, ERROR, WARN, INFO, DEBUG, TRACE) |
| `osgi.log.errors` | Counter | Error log entries by bundle name |

### Logs

Each forwarded log record includes:
- Bundle symbolic name, ID, and version
- Logger name and sequence number
- Thread info and source code location
- Exception details (if present)
- Severity mapped from OSGi `LogLevel` to OpenTelemetry `Severity`

## Components

| Class | Description |
|---|---|
| `LogBridgeComponent` | `LogListener` forwarding entries to OTel log bridge |
| `LogMetricsComponent` | Counters for log entries by level and errors by bundle |

## Dependencies

Requires OSGi Log Service in the container (e.g., Pax Logging or `org.apache.felix:org.apache.felix.log`).
