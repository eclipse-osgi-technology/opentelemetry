# OpenTelemetry SCR Lifecycle Weaver

A fragment bundle attaching to the [weaving host](../opentelemetry-osgi-weaving/README.md) that instruments Declarative Services component lifecycle methods at class-load time.
Inspired by [biz.aQute.trace](https://github.com/aQute-os/biz.aQute.osgi.util).

## Detection

The weaver reads the `Service-Component` manifest header of each bundle, parses the referenced DS XML files, and extracts:
- The `<implementation class="...">` attribute to identify component classes
- The `<component>` element attributes for lifecycle method names

Lifecycle method names follow the DS specification defaults:
- `activate` — default `"activate"`
- `deactivate` — default `"deactivate"`
- `modified` — no default (only instrumented when explicitly declared in XML)
- `init` — default `0` (constructor injection when > 0)

No annotation scanning is performed — the XML descriptor is the single source of truth, as annotations are not mandatory for DS components.
Parsed `ComponentDescriptor` records are cached per bundle ID for efficient `canWeave()` checks.

## Generated Telemetry

### Traces (span kind: `INTERNAL`)

| Attribute | Description |
|---|---|
| `scr.component.name` | DS component name from the XML descriptor |
| `scr.component.class` | Fully qualified component implementation class |
| `scr.lifecycle.action` | Lifecycle action: activate, deactivate, modified, constructor |
| `scr.method.name` | Method name (or `<init>` for constructors) |

Span names follow the pattern `scr.<action> <SimpleClassName>` (e.g., `scr.activate HealthCheckInventoryComponent`).

### Metrics

| Metric | Type | Description |
|---|---|---|
| `scr.lifecycle.operations` | Counter | Total lifecycle operations by action and component |
| `scr.lifecycle.duration` | Histogram | Lifecycle method duration in milliseconds |

## Components

| Class | Description |
|---|---|
| `ScrWeaver` | `Weaver` with DS XML parsing and `ComponentDescriptor` caching |
| `ComponentDescriptor` | Record: className, componentName, activateMethod, deactivateMethod, modifiedMethod, initParameterCount |
| `ScrClassVisitor` | ASM `ClassVisitor` matching methods by name from XML descriptor |
| `ScrMethodVisitor` | ASM `AdviceAdapter` injecting instrumentation |
| `ScrInstrumentationHelper` | Static helpers: `onLifecycleEnter`, `onLifecycleExit`, `onLifecycleError` |

## Note on Early Activations

Components that activate before the OpenTelemetry service is registered will have their lifecycle methods instrumented but produce noop spans.
The `OpenTelemetryProxy` returns noop tracers/meters until the real service arrives.
This is expected — the OpenTelemetry runtime is itself a DS component.
