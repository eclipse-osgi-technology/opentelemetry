# OpenTelemetry OSGi HTTP Whiteboard Bridge

Bridges the [OSGi HTTP Whiteboard](https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.servlet.html) runtime state into OpenTelemetry.
Introspects servlet contexts, servlets, filters, listeners, resources, and error pages via the `HttpServiceRuntime` DTO hierarchy.

## How It Works

The integration references the `HttpServiceRuntime` service and queries its `RuntimeDTO` on every metric collection cycle.
Components only activate when an `HttpServiceRuntime` service is available — no configuration needed.

### DTO Hierarchy

```
HttpServiceRuntime.getRuntimeDTO() → RuntimeDTO
  ├── servletContextDTOs[] → ServletContextDTO
  │     ├── name, contextPath, serviceId
  │     ├── servletDTOs[] → ServletDTO (name, patterns, asyncSupported)
  │     ├── filterDTOs[] → FilterDTO (name, patterns, dispatcher)
  │     ├── listenerDTOs[] → ListenerDTO (types)
  │     ├── resourceDTOs[] → ResourceDTO (patterns, prefix)
  │     └── errorPageDTOs[] → ErrorPageDTO (exceptions, errorCodes)
  ├── failedServletDTOs[], failedFilterDTOs[], ...
  └── preprocessorDTOs[]
```

## Components

| Component | Signal | Description |
|---|---|---|
| `HttpWhiteboardMetricsComponent` | Metrics | Async gauges for servlet/filter/listener/resource/error page counts per context |
| `HttpWhiteboardInventoryComponent` | Logs | Structured log records showing the full DTO hierarchy at activation |

## Metrics

| Metric | Type | Labels | Description |
|---|---|---|---|
| `osgi.http.whiteboard.contexts` | Gauge | — | Number of active servlet contexts |
| `osgi.http.whiteboard.servlets` | Gauge | `context.name` | Servlets per context |
| `osgi.http.whiteboard.filters` | Gauge | `context.name` | Filters per context |
| `osgi.http.whiteboard.listeners` | Gauge | `context.name` | Listeners per context |
| `osgi.http.whiteboard.resources` | Gauge | `context.name` | Resources per context |
| `osgi.http.whiteboard.error.pages` | Gauge | `context.name` | Error pages per context |
| `osgi.http.whiteboard.failed` | Gauge | — | Total failed registrations |

## Compatibility

This integration uses the pre-Jakarta HTTP Whiteboard API (`org.osgi.service.http.runtime`) for compatibility with:
- Pax Web
- Apache Felix HTTP Whiteboard
- Any OSGi R7+ HTTP Whiteboard implementation

## Maven

```xml
<dependency>
    <groupId>org.eclipse.osgi-technology.opentelemetry</groupId>
    <artifactId>org.eclipse.osgi-technology.opentelemetry.integration.jakarta.servlet</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```
