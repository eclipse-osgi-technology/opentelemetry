# OpenTelemetry OSGi JAX-RS Whiteboard Bridge

Bridges the [OSGi JAX-RS Whiteboard](https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.jakartars.html) runtime state into OpenTelemetry.
Introspects applications, resources, extensions, and resource methods via the `JaxrsServiceRuntime` DTO hierarchy.

## How It Works

The integration references the `JaxrsServiceRuntime` service and queries its `RuntimeDTO` on every metric collection cycle.
Components only activate when a `JaxrsServiceRuntime` service is available — requires a JAX-RS Whiteboard implementation such as [Apache Aries JAX-RS Whiteboard](https://github.com/apache/aries-jax-rs-whiteboard).

### DTO Hierarchy

```
JaxrsServiceRuntime.getRuntimeDTO() → RuntimeDTO
  ├── defaultApplication → ApplicationDTO
  │     ├── name, base (path)
  │     ├── resourceDTOs[] → ResourceDTO
  │     │     └── resourceMethods[] → ResourceMethodInfoDTO (method, path)
  │     └── extensionDTOs[] → ExtensionDTO (extensionTypes[])
  ├── applicationDTOs[] → ApplicationDTO (same structure)
  ├── failedApplicationDTOs[]
  ├── failedResourceDTOs[]
  └── failedExtensionDTOs[]
```

## Components

| Component | Signal | Description |
|---|---|---|
| `JaxrsWhiteboardMetricsComponent` | Metrics | Async gauges for per-application counts of resources, extensions, and methods |
| `JaxrsWhiteboardInventoryComponent` | Logs | Structured log records showing the full DTO hierarchy at activation |

## Metrics

| Metric | Type | Labels | Description |
|---|---|---|---|
| `osgi.jaxrs.applications` | Gauge | — | Number of active JAX-RS applications |
| `osgi.jaxrs.resources` | Gauge | `application.name`, `application.base` | Resources per application |
| `osgi.jaxrs.extensions` | Gauge | `application.name`, `application.base` | Extensions per application |
| `osgi.jaxrs.resource.methods` | Gauge | `application.name`, `application.base` | Resource methods per application |
| `osgi.jaxrs.failed` | Gauge | — | Total failed registrations |

## Activation Requirements

The JAX-RS Whiteboard integration uses **optional imports** for the `org.osgi.service.jaxrs.runtime` package.
The bundle resolves in any OSGi runtime, but components only activate when:

1. A bundle exports the `org.osgi.service.jaxrs.runtime` package (e.g., the JAX-RS Whiteboard API bundle)
2. A `JaxrsServiceRuntime` service is registered (e.g., by Aries JAX-RS Whiteboard)

Without a JAX-RS Whiteboard implementation, the bundle sits idle with no resource overhead.

## Compatibility

This integration uses the pre-Jakarta JAX-RS Whiteboard API (`org.osgi.service.jaxrs.runtime`) for compatibility with:
- Apache Aries JAX-RS Whiteboard
- Any OSGi R7+ JAX-RS Whiteboard implementation

## Maven

```xml
<dependency>
    <groupId>org.eclipse.osgi-technology</groupId>
    <artifactId>opentelemetry-osgi-jaxrs-whiteboard</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```
