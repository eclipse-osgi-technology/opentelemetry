# OpenTelemetry OSGi Weaving

This folder contains the OSGi WeavingHook based bytecode instrumentation infrastructure for OpenTelemetry.
It provides zero-code instrumentation of application bundles at class-load time using the [OSGi WeavingHook](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.weavinghook.html) mechanism and [ASM](https://asm.ow2.io/) bytecode manipulation.

## Architecture

The weaving system uses a **host bundle + fragment bundle** architecture.
Fragment bundles share the host's classloader, so plain Java `ServiceLoader` discovers weaver implementations without SPI Fly.

```
OSGi framework loads a class
    │
    ▼
OpenTelemetryWeavingHook.weave(WovenClass)
    │
    ├── Skip infrastructure bundles (Felix, Karaf, Jetty, Pax, Aries, CXF, XBean)
    │
    ├── For each registered Weaver:
    │     ├── weaver.canWeave(className, wovenClass) → yes/no
    │     └── weaver.weave(wovenClass, telemetryProxy) → transform bytecode
    │
    └── Transformed class continues loading
```

## Modules

| Module | Target | Details |
|---|---|---|
| [opentelemetry-osgi-weaving](opentelemetry-osgi-weaving/README.md) | Host bundle: WeavingHook, WeaverRegistry, OpenTelemetryProxy, ASM embedded | [Read more →](opentelemetry-osgi-weaving/README.md) |
| [opentelemetry-osgi-weaver-servlet](opentelemetry-osgi-weaver-servlet/README.md) | `HttpServlet` subclasses — HTTP request tracing and metrics | [Read more →](opentelemetry-osgi-weaver-servlet/README.md) |
| [opentelemetry-osgi-weaver-jdbc](opentelemetry-osgi-weaver-jdbc/README.md) | JDBC `Statement` implementations — database operation tracing | [Read more →](opentelemetry-osgi-weaver-jdbc/README.md) |
| [opentelemetry-osgi-weaver-jaxrs](opentelemetry-osgi-weaver-jaxrs/README.md) | `@Path`-annotated JAX-RS resources — REST endpoint tracing | [Read more →](opentelemetry-osgi-weaver-jaxrs/README.md) |
| [opentelemetry-osgi-weaver-scr](opentelemetry-osgi-weaver-scr/README.md) | DS component lifecycle methods — activate/deactivate/modified tracing | [Read more →](opentelemetry-osgi-weaver-scr/README.md) |

Each module README contains instrumented methods, generated telemetry tables, and component descriptions.

## Adding New Weavers

1. Create a new module as a fragment bundle (`Fragment-Host: opentelemetry-osgi-weaving`)
2. Implement the `Weaver` interface: `name()`, `canWeave()`, `weave()`
3. Use `SafeClassWriter` (from the host) instead of plain `ClassWriter`
4. Register via `META-INF/services/org.eclipse.osgi.technology.opentelemetry.weaving.Weaver`
5. Deploy the bundle in the OSGi runtime

### Bytecode Transformation Pattern

All weavers follow the same instrumentation pattern using ASM's `AdviceAdapter`:

```java
Object[] ctx = InstrumentationHelper.onEnter(args...);
try {
    // original method body
} catch (Throwable t) {
    InstrumentationHelper.onError(ctx, t);
    throw t;
} finally {
    InstrumentationHelper.onExit(ctx);
}
```
