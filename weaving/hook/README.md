# OpenTelemetry OSGi Weaving Host

The host bundle providing the OSGi WeavingHook based bytecode instrumentation infrastructure.
It uses [ASM 9.7.1](https://asm.ow2.io/) (embedded as `Private-Package`) for bytecode manipulation and the [OSGi WeavingHook](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.weavinghook.html) mechanism for class-load-time transformation.

## Architecture

```
WeavingHookActivator (BundleActivator)
    │
    ├── OpenTelemetryWeavingHook (WeavingHook service)
    │     └── delegates to WeaverRegistry
    │
    ├── WeaverRegistry (ServiceLoader-based Weaver discovery)
    │     └── loads fragment-provided Weaver SPI implementations
    │
    └── OpenTelemetryProxy (ServiceTracker for OpenTelemetry)
          └── noop fallback when service unavailable
```

## Components

| Class | Description |
|---|---|
| `WeavingHookActivator` | `BundleActivator` registering the `WeavingHook` service |
| `OpenTelemetryWeavingHook` | OSGi `WeavingHook` delegating to discovered weavers |
| `WeaverRegistry` | `ServiceLoader.load(Weaver.class)` based discovery |
| `OpenTelemetryProxy` | `ServiceTracker` wrapping the `OpenTelemetry` service with noop fallback |
| `SafeClassWriter` | `ClassWriter` using target bundle classloader for `COMPUTE_FRAMES` |
| `Weaver` | SPI interface for weaver implementations (`name()`, `canWeave()`, `weave()`) |

## Why BundleActivator (Not Declarative Services)?

The WeavingHook must be registered **before** DS component classes are loaded.
If DS were used, the hook would miss weaving DS-managed classes because those classes load during DS component activation.
Using `BundleActivator` ensures the hook is active from the earliest possible point in the bundle lifecycle.

## SafeClassWriter

The `SafeClassWriter` overrides `getClassLoader()` to use the woven class's `BundleWiring` classloader for stack map frame computation.
In OSGi, the default `ClassWriter.getCommonSuperClass()` fails because the weaving bundle cannot load classes from the target bundle.
Falls back to `java/lang/Object` for types that cannot be resolved.

## Infrastructure Bundle Exclusion

The hook skips weaving for container infrastructure bundles to prevent instrumenting internal framework code:
Felix, Karaf, Jetty, Pax, Aries, CXF, XBean, and Eclipse Equinox.

## ASM Embedding

ASM 9.7.1 is included via `Private-Package` in the bundle manifest.
This avoids resolution ordering and classloader visibility issues that would arise from a separate ASM bundle.

## Weaver SPI

Fragment bundles attach to this host and provide `Weaver` implementations registered in `META-INF/services/org.eclipse.osgi.technology.opentelemetry.weaving.Weaver`.
Since fragments share the host's classloader, plain Java `ServiceLoader` works without SPI Fly.
