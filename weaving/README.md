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
| [hook](hook/README.md) | Host bundle: WeavingHook, WeaverRegistry, OpenTelemetryProxy, ASM embedded | [Read more](hook/README.md) |
| [jakarta.servlet](jakarta.servlet/README.md) | `HttpServlet` subclasses — HTTP request tracing and metrics | [Read more](jakarta.servlet/README.md) |
| [jakarta.rest](jakarta.rest/README.md) | `@Path`-annotated JAX-RS resources — REST endpoint tracing | [Read more](jakarta.rest/README.md) |
| [jdbc](jdbc/README.md) | JDBC `Statement` implementations — database operation tracing | [Read more](jdbc/README.md) |
| [scr](scr/README.md) | DS component lifecycle methods — activate/deactivate/modified tracing | [Read more](scr/README.md) |

Each module README contains instrumented methods, generated telemetry tables, and component descriptions.

## Adding New Weavers

1. Create a new module as a fragment bundle (`Fragment-Host: org.eclipse.osgi-technology.opentelemetry.weaving.hook`)
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
