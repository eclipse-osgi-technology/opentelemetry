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
| `JaxrsResourceMetricsComponent` | Metrics | Registers a `JaxrsMetricsFilter` against every JAX-RS application via `ContainerRequestFilter`/`ContainerResponseFilter` — call counts, durations, and failures per resource, with no bytecode weaving |

## Metrics

| Metric | Type | Labels | Description |
|---|---|---|---|
| `osgi.jaxrs.applications` | Gauge | — | Number of active JAX-RS applications |
| `osgi.jaxrs.resources` | Gauge | `application.name`, `application.base` | Resources per application |
| `osgi.jaxrs.extensions` | Gauge | `application.name`, `application.base` | Extensions per application |
| `osgi.jaxrs.resource.methods` | Gauge | `application.name`, `application.base` | Resource methods per application |
| `osgi.jaxrs.failed` | Gauge | — | Total failed registrations |
| `jaxrs.server.requests` | Counter | `http.method`, `http.route`\*, `http.path`, `application.name`, `application.base` | Call count per resource (route + HTTP method) |
| `jaxrs.server.duration` | Histogram (ms) | `http.method`, `http.route`\*, `http.path`, `application.name`, `application.base` | Request duration per resource |
| `jaxrs.server.errors` | Counter | `http.method`, `http.route`\*, `http.path`, `http.status_code`, `application.name`, `application.base` | Failing requests per resource and status code (status &ge; 400) |
| `jaxrs.server.not_found` | Counter | `http.method`, `http.route`\*, `http.path`, `application.name`, `application.base` | 404 responses by raw request path, including unmatched routes |

\* `http.route` is only present when the request was routed to a resource method — see below.

`JaxrsResourceMetricsComponent`/`JaxrsMetricsFilter` measure the same signals as the [JAX-RS bytecode weaver](../../weaving/jakarta.rest/README.md) (same `jaxrs.server.requests`/`jaxrs.server.duration` metric names), but purely through the standard JAX-RS `ContainerRequestFilter`/`ContainerResponseFilter` extension points — no ASM weaving is involved.

- `http.path` is always present: the raw request path (`UriInfo#getPath()`), e.g. `widgets/42`.
- `http.route` is the matched resource method's URI *template*, built from its `@Path` annotations via `UriInfo#getBaseUriBuilder()` (injected `ResourceInfo`), e.g. `http://localhost:8080/widgets/{id}`. It is only added when a resource method actually matched the request — for a routing 404 (no resource matches at all), the attribute is omitted entirely rather than falling back to the raw path, so `http.path` is what you use to see which unmatched paths are being hit.

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

## Testing

This module has two independent, deliberately separate test suites:

- **`JaxrsMetricsFilterTest`** (package `org.eclipse.osgi.technology.opentelemetry.jakarta.rest`, *not* `...integration...`) — a plain Mockito-based unit test of `JaxrsMetricsFilter`, run by Maven Surefire (`mvn test`) on the ordinary JVM classpath. It lives outside the `.integration.` package tree on purpose: this module's parent POM excludes `**/integration/**` from Surefire (those packages are reserved for the OSGi/bnd test below), and Mockito's inline mock maker doesn't work inside a Felix bundle classloader anyway, so this test must never run there.
- **`JaxrsWhiteboardIntegrationTest`** (package `...integration.jakarta.rest`) boots a full JAX-RS Whiteboard stack in a real Felix framework — no mocks — to exercise `JaxrsResourceMetricsComponent`/`JaxrsMetricsFilter` end to end:
  - [`org.eclipse.osgitech.rest.servlet.whiteboard`](https://github.com/osgi/jakartarest-osgi) (Jersey-based JAX-RS Whiteboard implementation) served over
  - `org.apache.felix.http.jetty` (the Servlet Whiteboard, i.e. the actual embedded HTTP server)

  It registers a plain JAX-RS resource as an OSGi service, drives it with real HTTP requests (`jakarta.ws.rs.client.Client`, itself an OSGi service provided by the whiteboard), and asserts on the OpenTelemetry metrics captured via the logging exporter. This test — and only this test — is picked up by the `bnd-testing-maven-plugin` run (its `Test-Cases` header is restricted to the `*.integration.*` package by the reactor root `pom.xml`'s `bnd-maven-plugin` configuration), and its test-scope dependencies (Jetty, the whiteboard, Mockito, etc.) are pulled in only for `test.bndrun` resolution — they are not runtime dependencies of the bundle itself.

Since `JaxrsMetricsFilterTest` calls `new JaxrsMetricsFilter(...)` directly, `JaxrsMetricsFilter`'s class and constructor are `public` even though the package stays `Private-Package` (not exported) for OSGi consumers.

## Maven

```xml
<dependency>
    <groupId>org.eclipse.osgi-technology.opentelemetry</groupId>
    <artifactId>org.eclipse.osgi-technology.opentelemetry.integration.jakarta.rest</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```
