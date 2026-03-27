# OpenTelemetry Typed Event Bridge

Bridges the [OSGi Typed Event Service](https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.typedevent.html) into OpenTelemetry.
Observes all events flowing through the event bus and produces traces, metrics, and inventory logs.

## Features

- **Typed Event Metrics** — Counter for all events by topic and topic prefix, gauges for registered handler counts (typed, untyped, unhandled)
- **Typed Event Tracing** — Trace spans for each event delivered through the bus with topic and event data attributes
- **Typed Event Inventory** — Structured log records enumerating all registered event handlers at activation time

## Generated Telemetry

### Traces

| Span Name | Kind | Attributes |
|---|---|---|
| `osgi.typedevent.deliver` | `INTERNAL` | `typedevent.topic`, event data (up to 20 attributes) |

### Metrics

| Metric | Type | Description |
|---|---|---|
| `osgi.typedevent.events.total` | Counter | Events by topic and topic prefix |
| `osgi.typedevent.handlers.typed` | Gauge | Registered `TypedEventHandler` count |
| `osgi.typedevent.handlers.untyped` | Gauge | Registered `UntypedEventHandler` count |
| `osgi.typedevent.handlers.unhandled` | Gauge | Registered `UnhandledEventHandler` count |

### Logs

- **Handler Inventory** — Snapshot of all `TypedEventHandler`, `UntypedEventHandler`, and `UnhandledEventHandler` services with their event topics and registering bundles

## Components

| Class | Description |
|---|---|
| `TypedEventMetricsComponent` | `UntypedEventHandler` with `event.topics=*` counting events and tracking handlers |
| `TypedEventTracingComponent` | `UntypedEventHandler` with `event.topics=*` creating trace spans per event |
| `TypedEventInventoryComponent` | Queries all event handler service references at activation |

## Design Note

The integration registers as an `UntypedEventHandler` with `event.topics=*` to observe all events.
Per the Typed Event specification, this makes all events "handled", so `UnhandledEventHandler` services registered by other bundles will never fire.
This is a conscious trade-off for simpler implementation over using the `TypedEventMonitor` PushStream API.

## Dependencies

Requires the [Apache Aries TypedEvent Bus](https://github.com/apache/aries-typedevent) implementation, which depends on:
- Aries Component DSL
- OSGi Converter, PushStream, Promise, Function

All dependencies have proper `Bundle-SymbolicName` headers — no `wrap:` protocol needed.
