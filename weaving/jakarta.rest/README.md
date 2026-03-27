# OpenTelemetry JAX-RS Weaver

A fragment bundle attaching to the [weaving host](../opentelemetry-osgi-weaving/README.md) that instruments JAX-RS resource classes annotated with `@javax.ws.rs.Path`.
Only methods annotated with HTTP method annotations (`@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`, `@HEAD`, `@OPTIONS`) are instrumented.

## Detection

The weaver uses ASM `AnnotationVisitor` to detect `@Path` at the class level — **no JAX-RS API dependency is required at compile time**.
Method-level `@Path` values are combined with the class-level path to form the full route.

## Generated Telemetry

### Traces (span kind: `INTERNAL`)

| Attribute | Description |
|---|---|
| `http.method` | HTTP method from annotation (GET, POST, …) |
| `http.route` | Combined class + method `@Path` value |
| `jaxrs.resource.class` | Fully qualified resource class name |
| `jaxrs.resource.method` | Annotated method name |

Span kind is `INTERNAL` (not `SERVER`) because the outer servlet span already provides `SERVER` kind.

### Metrics

| Metric | Type | Description |
|---|---|---|
| `jaxrs.server.requests` | Counter | Total JAX-RS requests |
| `jaxrs.server.duration` | Histogram | Request duration in milliseconds |

## Components

| Class | Description |
|---|---|
| `JaxRsWeaver` | `Weaver` targeting `@Path`-annotated classes |
| `JaxRsClassVisitor` | ASM `ClassVisitor` reading class/method-level `@Path` |
| `JaxRsMethodVisitor` | ASM `AdviceAdapter` detecting HTTP method annotations |
| `JaxRsInstrumentationHelper` | Static helpers: `onMethodEnter`, `onMethodExit`, `onMethodError` |
