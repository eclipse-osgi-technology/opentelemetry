# AGENTS.md - Instructions for AI Agents

This file contains instructions and context for AI agents working on this codebase.

## Project Overview

This is a Maven multi-module project integrating OpenTelemetry with OSGi.
The project is organized into three top-level folders, each containing related modules.

## Repository Structure

```
opentelemetry-osgi/
├── pom.xml                          # Parent POM with dependency management
├── core/                            # Core runtime
│   ├── pom.xml                      # Aggregator POM
│   └── opentelemetry-osgi-runtime/  # OSGi service providing OpenTelemetry SDK
│       └── src/main/java/org/eclipse/osgi/technology/opentelemetry/runtime/
│           ├── AbstractOpenTelemetryService.java        # Base class with resource building, SDK lifecycle
│           ├── LoggingOpenTelemetryService.java         # Logging exporter service
│           ├── OtlpHttpOpenTelemetryService.java        # OTLP/HTTP exporter service
│           ├── OtlpGrpcOpenTelemetryService.java        # OTLP/gRPC exporter service
│           ├── OpenTelemetryProviderRegistration.java   # Dynamic 1..n provider registration
│           ├── LoggingOpenTelemetryConfiguration.java   # Logging exporter config (with Metatype)
│           ├── OtlpHttpOpenTelemetryConfiguration.java  # OTLP/HTTP config (with Metatype)
│           └── OtlpGrpcOpenTelemetryConfiguration.java  # OTLP/gRPC config (with Metatype)
├── integrations/                    # OSGi subsystem bridges
│   ├── pom.xml                      # Aggregator POM
│   ├── opentelemetry-osgi-framework/  # Framework bridge (bundles, services, events)
│   │   └── src/main/java/org/eclipse/osgi/technology/opentelemetry/framework/
│   │       ├── FrameworkMetricsComponent.java    # Bundle/service count gauges
│   │       ├── FrameworkEventComponent.java      # Bundle/service event tracing
│   │       ├── BundleInventoryComponent.java     # Live bundle inventory
│   │       ├── ServiceInventoryComponent.java    # Live service inventory
│   │       ├── BundleStateUtil.java              # Bundle state name utility
│   │       └── BundleInfo.java                   # Bundle state record
│   ├── opentelemetry-osgi-scr/      # SCR introspection → OpenTelemetry bridge
│   │   └── src/main/java/org/eclipse/osgi/technology/opentelemetry/scr/
│   │       ├── ScrMetricsComponent.java         # DS component state gauges
│   │       ├── ScrInventoryComponent.java       # DS inventory as structured logs
│   │       └── ScrHealthCheckComponent.java     # Periodic health trace
│   ├── opentelemetry-osgi-felix-healthcheck/  # Felix Health Check → OpenTelemetry bridge
│   │   └── src/main/java/org/eclipse/osgi/technology/opentelemetry/healthcheck/
│   │       ├── HealthCheckMetricsComponent.java  # HC count/status/duration gauges
│   │       ├── HealthCheckTracingComponent.java  # Periodic HC execution traces
│   │       └── HealthCheckInventoryComponent.java # HC inventory as structured logs
│   ├── opentelemetry-osgi-cm/          # OSGi Config Admin → OpenTelemetry bridge
│   │   └── src/main/java/org/eclipse/osgi/technology/opentelemetry/cm/
│   │       ├── ConfigAdminMetricsComponent.java  # Configuration count gauges + event counter
│   │       ├── ConfigAdminEventComponent.java    # Configuration change traces
│   │       └── ConfigAdminInventoryComponent.java # Configuration inventory as structured logs
│   ├── opentelemetry-osgi-mxbeans/     # Java MXBeans → OpenTelemetry bridge
│   │   └── src/main/java/org/eclipse/osgi/technology/opentelemetry/mxbeans/
│   │       ├── MxBeansMetricsComponent.java      # JVM metrics (memory, CPU, threads, GC, etc.)
│   │       └── MxBeansConfiguration.java         # DS config annotation for metric groups
│   ├── opentelemetry-osgi-typedevent/  # OSGi Typed Event → OpenTelemetry bridge
│   │   └── src/main/java/org/eclipse/osgi/technology/opentelemetry/typedevent/
│   │       ├── TypedEventMetricsComponent.java   # Event counter + handler count gauges
│   │       ├── TypedEventTracingComponent.java   # Trace spans for each event
│   │       └── TypedEventInventoryComponent.java # Handler inventory as structured logs
│   ├── opentelemetry-osgi-http-whiteboard/  # HTTP Whiteboard → OpenTelemetry bridge
│   │   └── src/main/java/org/eclipse/osgi/technology/opentelemetry/http/
│   │       ├── HttpWhiteboardMetricsComponent.java   # Per-context servlet/filter/listener gauges
│   │       └── HttpWhiteboardInventoryComponent.java # Full DTO hierarchy as structured logs
│   ├── opentelemetry-osgi-jaxrs-whiteboard/  # JAX-RS Whiteboard → OpenTelemetry bridge
│   │   └── src/main/java/org/eclipse/osgi/technology/opentelemetry/jaxrs/
│   │       ├── JaxrsWhiteboardMetricsComponent.java   # Per-application resource/extension gauges
│   │       └── JaxrsWhiteboardInventoryComponent.java # Full DTO hierarchy as structured logs
│   └── opentelemetry-osgi-log/      # OSGi Log Service → OpenTelemetry bridge
│       └── src/main/java/org/eclipse/osgi/technology/opentelemetry/log/
│           ├── LogBridgeComponent.java          # Forwards LogEntry to OTel logs
│           └── LogMetricsComponent.java         # Log entry counters as OTel metrics
├── weaving/                         # OSGi WeavingHook based bytecode instrumentation
│   ├── pom.xml                      # Aggregator POM
│   ├── opentelemetry-osgi-weaving/  # Host bundle: WeavingHook, WeaverRegistry, ASM embedded
│   │   └── src/main/java/org/eclipse/osgi/technology/opentelemetry/weaving/
│   │       ├── Weaver.java                      # SPI interface for weaver implementations
│   │       ├── WeaverRegistry.java              # ServiceLoader-based weaver discovery
│   │       ├── OpenTelemetryWeavingHook.java    # OSGi WeavingHook delegating to weavers
│   │       ├── WeavingHookActivator.java        # BundleActivator (not DS)
│   │       ├── OpenTelemetryProxy.java          # Graceful proxy with noop fallback
│   │       └── SafeClassWriter.java             # ClassWriter using target bundle classloader
│   ├── opentelemetry-osgi-weaver-servlet/  # Fragment: HttpServlet instrumentation
│   │   ├── src/main/java/org/eclipse/osgi/technology/opentelemetry/weaver/servlet/
│   │   │   ├── ServletWeaver.java                   # Weaver targeting HttpServlet subclasses
│   │   │   ├── ServletClassVisitor.java             # ASM ClassVisitor for servlet methods
│   │   │   ├── ServletServiceMethodVisitor.java     # ASM AdviceAdapter for bytecode injection
│   │   │   └── ServletInstrumentationHelper.java    # Static helpers called from woven code
│   │   └── src/main/resources/META-INF/services/
│   │       └── ...opentelemetry.weaving.Weaver      # Java SPI registration
│   ├── opentelemetry-osgi-weaver-jdbc/     # Fragment: JDBC instrumentation
│   │   ├── src/main/java/org/eclipse/osgi/technology/opentelemetry/weaver/jdbc/
│   │   │   ├── JdbcWeaver.java                      # Weaver targeting Statement implementations
│   │   │   ├── JdbcClassVisitor.java                # ASM ClassVisitor for execute* methods
│   │   │   ├── JdbcMethodVisitor.java               # ASM AdviceAdapter for bytecode injection
│   │   │   └── JdbcInstrumentationHelper.java       # Static helpers called from woven code
│   │   └── src/main/resources/META-INF/services/
│   │       └── ...opentelemetry.weaving.Weaver      # Java SPI registration
│   └── opentelemetry-osgi-weaver-jaxrs/    # Fragment: JAX-RS resource instrumentation
│       ├── src/main/java/org/eclipse/osgi/technology/opentelemetry/weaver/jaxrs/
│       │   ├── JaxRsWeaver.java                     # Weaver targeting @Path-annotated classes
│       │   ├── JaxRsClassVisitor.java               # ASM ClassVisitor reading @Path annotations
│       │   ├── JaxRsMethodVisitor.java              # ASM AdviceAdapter detecting HTTP method annotations
│       │   └── JaxRsInstrumentationHelper.java      # Static helpers called from woven code
│       └── src/main/resources/META-INF/services/
│           └── ...opentelemetry.weaving.Weaver      # Java SPI registration
│   └── opentelemetry-osgi-weaver-scr/      # Fragment: SCR lifecycle instrumentation
│       ├── src/main/java/org/eclipse/osgi/technology/opentelemetry/weaver/scr/
│       │   ├── ScrWeaver.java                       # Weaver targeting DS component classes via XML parsing
│       │   ├── ComponentDescriptor.java             # Record: lifecycle method names from DS XML
│       │   ├── ScrClassVisitor.java                 # ASM ClassVisitor matching methods by XML descriptor
│       │   ├── ScrMethodVisitor.java                # ASM AdviceAdapter injecting instrumentation bytecode
│       │   └── ScrInstrumentationHelper.java        # Static helpers called from woven code
│       └── src/main/resources/META-INF/services/
│           └── ...opentelemetry.weaving.Weaver      # Java SPI registration
├── doc/                             # Documentation
├── README.md
├── AGENTS.md                        # This file
└── LICENSE                          # EPL-2.0
```

## Maven Coordinates

- **GroupId**: `org.eclipse.osgi-technology`
- **Package namespace**: `org.eclipse.osgi.technology.opentelemetry`
- **Parent artifactId**: `opentelemetry-osgi-parent`

### Module Artifacts

| Module | ArtifactId | Folder |
|---|---|---|
| Runtime | `opentelemetry-osgi-runtime` | `core/` |
| Framework Bridge | `opentelemetry-osgi-framework` | `integrations/` |
| SCR Bridge | `opentelemetry-osgi-scr` | `integrations/` |
| Log Bridge | `opentelemetry-osgi-log` | `integrations/` |
| Health Check Bridge | `opentelemetry-osgi-felix-healthcheck` | `integrations/` |
| Config Admin Bridge | `opentelemetry-osgi-cm` | `integrations/` |
| MXBeans Bridge | `opentelemetry-osgi-mxbeans` | `integrations/` |
| Typed Event Bridge | `opentelemetry-osgi-typedevent` | `integrations/` |
| HTTP Whiteboard Bridge | `opentelemetry-osgi-http-whiteboard` | `integrations/` |
| JAX-RS Whiteboard Bridge | `opentelemetry-osgi-jaxrs-whiteboard` | `integrations/` |
| Weaving Host | `opentelemetry-osgi-weaving` | `weaving/` |
| Servlet Weaver | `opentelemetry-osgi-weaver-servlet` | `weaving/` |
| JDBC Weaver | `opentelemetry-osgi-weaver-jdbc` | `weaving/` |
| JAX-RS Weaver | `opentelemetry-osgi-weaver-jaxrs` | `weaving/` |
| SCR Lifecycle Weaver | `opentelemetry-osgi-weaver-scr` | `weaving/` |

### Aggregator POMs

Each subfolder has an aggregator POM that references the root parent:

| Folder | ArtifactId |
|---|---|
| `core/` | `opentelemetry-osgi-core-parent` |
| `integrations/` | `opentelemetry-osgi-integrations-parent` |
| `weaving/` | `opentelemetry-osgi-weaving-parent` |

All module POMs use `<relativePath>../../pom.xml</relativePath>` to reference the root parent directly.

## Build Commands

```bash
# Full build
mvn clean verify

# Build a single module (use relative path)
mvn clean verify -pl core/opentelemetry-osgi-runtime

# Build with dependency resolution logging
mvn clean verify -X
```

## Runtime Exporter Architecture

The runtime supports multiple exporter types, each as a separate DS component with its own configuration PID.
All exporters extend `AbstractOpenTelemetryService` which provides resource building, SDK lifecycle, and `OpenTelemetry` interface delegation.

### Exporter Services

| Service | Component Name | PID | Ranking |
|---|---|---|---|
| `LoggingOpenTelemetryService` | `logging-opentelemetry` | `...runtime.logging` | 0 (default) |
| `OtlpHttpOpenTelemetryService` | `otlp-http-opentelemetry` | `...runtime.otlp.http` | 100 |
| `OtlpGrpcOpenTelemetryService` | `otlp-grpc-opentelemetry` | `...runtime.otlp.grpc` | 100 |

Each service:
- Uses `configurationPolicy = REQUIRE` — activates only when its PID config exists
- Has a short `component.name` defined as a constant in its configuration annotation (`COMPONENT_NAME`)
- Publishes `OpenTelemetry` as an OSGi service
- Uses `@Designate(ocd = ...)` to link to its Metatype-annotated configuration

### Metatype Annotations

All configuration annotations use OSGi Metatype annotations (`@ObjectClassDefinition`, `@AttributeDefinition`) for runtime discoverability.
This generates metatype XML that management tools can use to display configuration options with names, descriptions, and valid values.
Enum-like properties (compression, aggregation temporality) use `@AttributeDefinition(options = @Option(...))` for predefined choices.

### Provider Registration

`OpenTelemetryProviderRegistration` uses dynamic 1..n references to bind all `OpenTelemetry` services.
For each bound service, it registers `TracerProvider`, `MeterProvider`, `LoggerProvider`, and `ContextPropagators` as separate OSGi services with:
- `opentelemetry.name` property — set to the `component.name` of the originating exporter
- `service.ranking` — propagated from the exporter service

Consumers can target a specific exporter:
```java
@Reference(target = "(opentelemetry.name=otlp-http-opentelemetry)")
private TracerProvider tracerProvider;
```

### Adding New Exporters

1. Create a configuration annotation with `COMPONENT_NAME` and `PID` constants, annotated with `@ObjectClassDefinition`
2. Create a service class extending `AbstractOpenTelemetryService`
3. Annotate with `@Component(name = Config.COMPONENT_NAME, configurationPid = Config.PID, ...)` and `@Designate(ocd = Config.class)`
4. Add exporter dependencies to `pom.xml`

## Code Conventions

### Java

- **Java version**: 21 (set via `maven.compiler.release` in parent POM)
- **Code style**: Use public or package-private top-level types instead of inner classes/interfaces/records
- **Records**: Use Java records for immutable data types (e.g. `BundleInfo`)
- **Switch expressions**: Use enhanced switch expressions (Java 21 feature)
- **No inner types**: Prefer separate files for each class, interface, record, and annotation type

### OSGi

- **Metadata generation**: bnd-maven-plugin generates MANIFEST.MF and DS component XML
- **Declarative Services**: Use `@Component`, `@Reference`, `@Activate`, `@Deactivate`, `@Modified` annotations from `org.osgi.service.component.annotations`
- **Configuration**: Use annotation interfaces (not `@ObjectClassDefinition` from metatype — keep it simple with DS config annotation types)
- **Scope**: OSGi annotations are `provided` scope (processed at build time by bnd)
- **Package-info**: Each package has a `package-info.java` with Javadoc (Javadoc comment comes before the package declaration)

### Dynamic Service Reference Pattern

All integration components follow a consistent pattern for domain service references:

- **Constructor injection for OpenTelemetry**: `@Activate public ClassName(@Reference OpenTelemetry openTelemetry)` ensures OTel is available before any dynamic bindings arrive
- **Dynamic MULTIPLE references**: Domain services (ConfigurationAdmin, ServiceComponentRuntime, HealthCheckExecutor, LogReaderService, HttpServiceRuntime, JaxrsServiceRuntime) use `@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)` with `bind<Service>` / `unbind<Service>` methods
- **ConcurrentHashMap tracking**: The service object itself is the map key; values are either state records (for metrics components with gauges to close) or Long (service.id, for inventory/listener components)
- **State records**: Metrics components use package-private top-level records implementing `AutoCloseable` (e.g., `ScrMetricsState`, `ConfigAdminMetricsState`) holding `ObservableLongGauge` fields and a `serviceId` for OTel attributes
- **Distinguishing attribute**: `AttributeKey.longKey("osgi.service.id")` is added to all OTel metric recordings so multiple instances of the same service type produce separate time series
- **@Deactivate cleanup**: Iterates remaining map entries and closes/cleans up as a safety net (DS calls unbind before deactivate, but cleanup is good practice)
- **Shared instruments**: Synchronous instruments (LongCounter) can be shared across service instances since OTel SDK deduplicates them; only async gauges (ObservableLongGauge with callbacks) need per-service registration

### OpenTelemetry

- **API vs SDK**: Integration bundles depend only on `opentelemetry-api`; only the runtime bundle depends on `opentelemetry-sdk`
- **OTLP export**: The runtime supports both `logging` and `otlp` exporter types. OTLP is auto-selected when `OTEL_EXPORTER_OTLP_ENDPOINT` env var is set.
- **Sender**: Uses `opentelemetry-exporter-sender-jdk` (Java's built-in HttpClient) with OTLP/HTTP protocol — no gRPC or external HTTP library needed
- **Instrumentation scopes**: Use fully qualified package names as instrumentation scope names
- **BOM**: Dependency versions managed via `opentelemetry-bom` import in parent POM

### Markdown

- Start each sentence on a new line (one sentence per line for easier diffing)

## Dependency Versions

All versions are centralized in the parent POM properties:

| Property | Value | Artifact |
|---|---|---|
| `opentelemetry.version` | `1.49.0` | OpenTelemetry BOM |
| `osgi.framework.version` | `1.10.0` | OSGi Framework API |
| `osgi.service.component.annotations.version` | `1.5.1` | DS annotations |
| `osgi.service.component.version` | `1.5.1` | DS runtime |
| `osgi.service.log.version` | `1.5.0` | OSGi Log Service |
| `osgi.annotation.bundle.version` | `2.0.0` | Bundle annotations |
| `osgi.service.metatype.annotations.version` | `1.4.1` | Metatype annotations |
| `felix.healthcheck.api.version` | `2.0.4` | Felix Health Check API |
| `felix.healthcheck.core.version` | `2.0.8` | Felix Health Check Core |
| `felix.healthcheck.generalchecks.version` | `3.0.8` | Felix Health Check General Checks |
| `osgi.service.cm.version` | `1.6.1` | OSGi Configuration Admin API |
| `osgi.service.typedevent.version` | `1.0.0` | OSGi Typed Event API |
| `aries.typedevent.version` | `1.0.1` | Apache Aries TypedEvent Bus |
| `aries.component.dsl.version` | `1.2.2` | Aries Component DSL |
| `osgi.util.converter.version` | `1.0.9` | OSGi Converter |
| `osgi.util.pushstream.version` | `1.1.0` | OSGi PushStream |
| `osgi.util.promise.version` | `1.3.0` | OSGi Promise |
| `osgi.util.function.version` | `1.2.0` | OSGi Function |
| `osgi.service.http.whiteboard.version` | `1.1.1` | HTTP Whiteboard API (DTOs) |
| `osgi.service.jaxrs.version` | `1.0.0` | JAX-RS Whiteboard API (DTOs) |
| `bnd.version` | `7.1.0` | bnd-maven-plugin |

When updating OpenTelemetry version, update the `opentelemetry.version` property — all module dependencies are managed via the BOM.

## Framework Module Notes

The framework module (`integrations/opentelemetry-osgi-framework`) provides OSGi framework telemetry as proper DS components:

- Uses `@Reference OpenTelemetry` and `BundleContext` (injected via `@Activate`) — no `FrameworkUtil.getBundle()` workaround
- Registers as `BundleListener` and `ServiceListener` in `@Activate`, unregisters in `@Deactivate`
- `BundleInventoryComponent` uses `SynchronousBundleListener` to capture events before the framework proceeds; emits snapshot at activation + change log records for every bundle event
- `ServiceInventoryComponent` uses `ServiceListener` to track registrations/unregistrations/modifications; emits snapshot at activation + change log records with using-bundles info
- Async gauges use `ObservableLongGauge` with proper cleanup via `close()` on deactivate
- `BundleStateUtil` provides the `bundleStateToString()` utility shared across components
- `BundleInfo` record captures immutable bundle state snapshots

## SCR Module Notes

The SCR module (`integrations/opentelemetry-osgi-scr`) uses the OSGi SCR Introspection API from `org.osgi.service.component.runtime`:

- References `ServiceComponentRuntime` to enumerate all DS component descriptions and configurations
- Uses `ComponentConfigurationDTO` state constants: `UNSATISFIED_CONFIGURATION=1`, `UNSATISFIED_REFERENCE=2`, `SATISFIED=4`, `ACTIVE=8`, `FAILED_ACTIVATION=16`
- The `configStateToString()` utility in `ScrMetricsComponent` maps state integers to human-readable names — reused by other SCR components
- Async gauges (via `ObservableLongGauge`) query component state on every metric collection cycle

## Log Module Notes

The Log module (`integrations/opentelemetry-osgi-log`) uses the OSGi Log Service from `org.osgi.service.log`:

- References `LogReaderService` and registers as a `LogListener` to capture real-time log entries
- Maps `LogLevel` (AUDIT, ERROR, WARN, INFO, DEBUG, TRACE) to OpenTelemetry `Severity`
- Enriches OTel log records with: bundle symbolic name/id/version, logger name, sequence number, thread info, service reference, source code location, exception details
- `LogMetricsComponent` maintains counters by log level and a dedicated error counter by bundle name
- Requires OSGi Log Service in the container (e.g. `org.apache.felix:org.apache.felix.log:1.3.0`)

## Health Check Module Notes

The Health Check module (`integrations/opentelemetry-osgi-felix-healthcheck`) uses the [Apache Felix Health Check](https://felix.apache.org/documentation/subprojects/apache-felix-healthcheck.html) API:

- References `HealthCheckExecutor` to execute all registered health checks periodically (every 30 seconds)
- Uses `HealthCheckSelector.empty()` which, combined with the executor config (`defaultTags=` empty), selects ALL registered health checks regardless of tags
- `HealthCheckMetricsComponent` uses a `ScheduledExecutorService` for periodic execution and caches results in `volatile lastResults` for async gauge callbacks
- Status mapping: `Result.Status` values (OK, WARN, CRITICAL, TEMPORARILY_UNAVAILABLE, HEALTH_CHECK_ERROR) are used as metric attributes
- `HealthCheckTracingComponent` creates a parent span `osgi.hc.execution` with child spans for each individual health check result
- `HealthCheckInventoryComponent` queries `BundleContext` for all `HealthCheck` service references and emits log records with name, tags, and bundle info
- The executor config file (`org.apache.felix.hc.core.impl.executor.HealthCheckExecutorImpl.cfg`) must have `defaultTags=` (empty) to avoid the executor's default tag filter `["default"]` which would miss checks tagged with other values like `systemalive`
- Felix Health Check bundles are proper OSGi bundles (they have `Bundle-SymbolicName`)
- General checks (CPU, Memory, ThreadUsage, DiskSpace, BundlesStarted) require `.cfg` files to activate (`configurationPolicy=REQUIRE`); FrameworkStartCheck has `configurationPolicy=OPTIONAL` and auto-activates

## Config Admin Module Notes

The Config Admin module (`integrations/opentelemetry-osgi-cm`) uses the OSGi Configuration Admin service:

- `ConfigAdminMetricsComponent` and `ConfigAdminEventComponent` both implement `ConfigurationListener` to receive configuration change events
- `ConfigurationEvent` does NOT include property values (security by design) — only PID, factory PID, event type, and service reference
- Event types: `CM_UPDATED=1`, `CM_DELETED=2`, `CM_LOCATION_CHANGED=3`
- `ConfigAdminMetricsComponent` uses async gauges for config counts and a `LongCounter` for event counting by type
- `ConfigAdminEventComponent` creates traces for each configuration change with PID and event type as span attributes
- `ConfigAdminInventoryComponent` queries `configAdmin.listConfigurations(null)` for all configs — returns `null` (not empty array) when none exist
- Inventory log records include: PID, factory PID, bundle location, property count, and property keys (but not values, for security)

## MXBeans Module Notes

The MXBeans module (`integrations/opentelemetry-osgi-mxbeans`) uses `java.lang.management.ManagementFactory` to expose JVM runtime metrics:

- Single component `MxBeansMetricsComponent` with `@Modified` support for dynamic reconfiguration
- Configurable via `MxBeansConfiguration` annotation — 7 metric groups can be individually enabled/disabled: `memoryEnabled`, `cpuEnabled`, `threadsEnabled`, `gcEnabled`, `classLoadingEnabled`, `bufferPoolsEnabled`, `memoryPoolsEnabled`
- Uses `com.sun.management.OperatingSystemMXBean` for process/system CPU load and physical memory (optional import; degrades gracefully on non-HotSpot JVMs)
- All metrics are registered as OpenTelemetry async gauges (callbacks) — no background threads or polling
- GC, memory pool, and buffer pool metrics are per-instance with name attributes
- `Import-Package: com.sun.management;resolution:=optional` in bnd config to avoid mandatory resolution of JVM-internal package

## Typed Event Module Notes

The Typed Event module (`integrations/opentelemetry-osgi-typedevent`) uses the [OSGi Typed Event Service](https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.typedevent.html) to bridge event bus activity into OpenTelemetry:

- Both `TypedEventMetricsComponent` and `TypedEventTracingComponent` implement `UntypedEventHandler` with `event.topics=*` to observe ALL events flowing through the bus
- `TypedEventMetricsComponent` maintains a `LongCounter` (`osgi.typedevent.events.total`) with `typedevent.topic` and `typedevent.topic_prefix` attributes, plus async gauges for handler counts
- `TypedEventTracingComponent` creates `osgi.typedevent.deliver` spans with topic and event data as span attributes (capped at 20 data attributes per event)
- `TypedEventInventoryComponent` queries `BundleContext.getAllServiceReferences()` for `TypedEventHandler`, `UntypedEventHandler`, and `UnhandledEventHandler` services at activation time
- `extractTopicPrefix()` utility extracts the first two segments of a topic path (e.g., `org/eclipse/osgi/events/heartbeat` → `org/eclipse`) for broader metric grouping
- The handler count gauge for untyped handlers includes the two monitoring handlers themselves (metrics + tracing)
- Requires Apache Aries TypedEvent Bus implementation + 5 transitive dependencies (Component DSL, Converter, PushStream, Promise, Function)

### Design Trade-off: UntypedEventHandler vs TypedEventMonitor

The integration uses `UntypedEventHandler` rather than `TypedEventMonitor` because:
- Simpler API — follows the same listener pattern as other integrations (ConfigurationListener, LogListener, BundleListener)
- TypedEventMonitor would require PushStream reactive API with backpressure handling
- **Trade-off**: All events are considered "handled" by the monitoring handlers, so `UnhandledEventHandler` services will never fire
- This is documented in `package-info.java` and should be noted by users who depend on unhandled event detection

## HTTP Whiteboard Module Notes

The HTTP Whiteboard module (`integrations/opentelemetry-osgi-http-whiteboard`) uses the [OSGi HTTP Whiteboard](https://docs.osgi.org/specification/osgi.cmpn/8.0.0/service.http.whiteboard.html) runtime DTOs:

- References `HttpServiceRuntime` to enumerate all servlet contexts, servlets, filters, listeners, resources, and error pages
- Uses `RuntimeDTO` as the top-level descriptor: contains `ServletContextDTO[]` for each active context, plus `FailedServletDTO[]`, `FailedFilterDTO[]`, etc. for failed registrations
- `HttpWhiteboardMetricsComponent` registers 7 async gauges: contexts (total), servlets/filters/listeners/resources/error_pages (per-context via `context.name` attribute), and failed registrations (total across all failure categories)
- `HttpWhiteboardInventoryComponent` emits structured log records at activation with the full DTO hierarchy per context
- Uses `org.osgi.service.http.whiteboard:1.1.1` API artifact (NOT `org.osgi.service.servlet.runtime` which is Jakarta namespace)

### Key DTO Hierarchy

```
RuntimeDTO
├── ServletContextDTO[] servletContextDTOs
│   ├── name, contextPath, serviceId
│   ├── ServletDTO[] servletDTOs
│   ├── FilterDTO[] filterDTOs
│   ├── ListenerDTO[] listenerDTOs
│   ├── ResourceDTO[] resourceDTOs
│   └── ErrorPageDTO[] errorPageDTOs
├── FailedServletDTO[] failedServletDTOs
├── FailedFilterDTO[] failedFilterDTOs
├── FailedListenerDTO[] failedListenerDTOs
├── FailedResourceDTO[] failedResourceDTOs
└── FailedErrorPageDTO[] failedErrorPageDTOs
```

## JAX-RS Whiteboard Module Notes

The JAX-RS Whiteboard module (`integrations/opentelemetry-osgi-jaxrs-whiteboard`) uses the [OSGi JAX-RS Whiteboard](https://docs.osgi.org/specification/osgi.cmpn/8.0.0/service.jaxrs.html) runtime DTOs:

- References `JaxrsServiceRuntime` to enumerate all JAX-RS applications, resources, and extensions
- Uses `RuntimeDTO` containing `ApplicationDTO[]` for each application, plus `FailedApplicationDTO[]`, `FailedResourceDTO[]`, `FailedExtensionDTO[]`
- `JaxrsWhiteboardMetricsComponent` registers 5 async gauges: applications (total), resources/extensions (per-application), total HTTP methods across all resources, and failed registrations
- `JaxrsWhiteboardInventoryComponent` emits structured log records with full application hierarchy
- All imports use `resolution:=optional` because the JAX-RS API bundle has complex requirements (`Require-Capability: osgi.contract=JavaJAXRS`) that cannot be satisfied without a full JAX-RS Whiteboard runtime

### Activation Requirements

The JAX-RS Whiteboard integration only activates when a `JaxrsServiceRuntime` service is present.
The integration bundle resolves in any runtime but sits idle without the runtime service.

## Weaving Module Notes

The weaving module (`weaving/`) uses the [OSGi WeavingHook](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.weavinghook.html) for lightweight bytecode instrumentation at class-load time.
It consists of a host bundle and fragment bundles discovered via Java SPI.

### Architecture

- **Host bundle** (`opentelemetry-osgi-weaving`): Contains the `WeavingHook`, `BundleActivator`, `ServiceTracker`-based `OpenTelemetryProxy`, and `WeaverRegistry` (Java SPI discovery)
- **Fragment bundles** (e.g., `opentelemetry-osgi-weaver-servlet`): Attach to the host via `Fragment-Host`, providing `Weaver` implementations registered in `META-INF/services`
- Fragments share the host's classloader, so plain `ServiceLoader.load(Weaver.class)` works without SPI Fly
- ASM 9.7.1 is embedded in the host bundle via `Private-Package` (org.objectweb.asm.*) to avoid resolution ordering and classloader visibility issues

### Key Design Decisions

- **BundleActivator, not DS**: The WeavingHook must be active before DS component classes load — using DS would miss weaving those classes
- **Fragment bundles for weavers**: Fragments share the host's classloader, enabling plain Java SPI without SPI Fly
- **ASM embedded**: Avoiding a separate ASM bundle eliminates resolution ordering issues and ensures ASM classes are always visible to the weaving code
- **SafeClassWriter**: Uses `COMPUTE_FRAMES` with the target bundle's classloader for frame computation. The default `ClassWriter.getCommonSuperClass()` calls `Class.forName()` which fails across OSGi classloader boundaries. `SafeClassWriter` overrides `getClassLoader()` to use `WovenClass.getBundleWiring().getClassLoader()`, falling back to `java/lang/Object` only when the target classloader cannot resolve a type. This is critical for instrumenting complex classes (e.g., H2 `JdbcPreparedStatement`) where incorrect frame merging causes `VerifyError`.
- **Infrastructure bundle exclusion**: Skips weaving for Felix, Karaf, Jetty, Pax, Aries, CXF, XBean, and Eclipse Equinox bundles to prevent instrumenting container internals
- **OpenTelemetryProxy**: Uses `ServiceTracker` to gracefully handle the OpenTelemetry service not being available yet; falls back to noop tracers/meters
- **Weaving bundles should activate early**: Weaving bundles must be active before application bundles to ensure the WeavingHook is registered before application classes load

### Servlet Weaver Details

- Instruments `javax.servlet.http.HttpServlet` subclasses (skips `javax.servlet.*` classes themselves)
- Targets methods: `service`, `doGet`, `doPost`, `doPut`, `doDelete`, `doHead`, `doOptions`, `doTrace`
- Creates `SERVER` spans with `http.method`, `http.url`, `http.status_code`, `http.servlet.class`, `http.query_string` attributes
- Records `http.server.requests` counter and `http.server.duration` histogram metrics
- `ServletInstrumentationHelper` provides static methods called from woven bytecode (`onServiceEnter`, `onServiceExit`, `onServiceError`)
- The instrumentation scope is `org.eclipse.osgi.technology.opentelemetry.weaver.servlet`

### JDBC Weaver Details

- Instruments classes implementing `java.sql.Statement`, `java.sql.PreparedStatement`, or `java.sql.CallableStatement`
- Skips JDBC API classes themselves (packages starting with `java.sql` or `javax.sql`)
- Targets methods: `execute`, `executeQuery`, `executeUpdate`, `executeBatch`, `executeLargeUpdate`, `executeLargeBatch`
- Creates `CLIENT` spans with `db.system`, `db.operation`, `db.statement`, `db.jdbc.driver_class` attributes
- Span names derived from SQL statement type (SELECT, INSERT, UPDATE, DELETE) or fall back to `JDBC <operation>`
- Records `db.client.operations` counter and `db.client.duration` histogram metrics
- `JdbcInstrumentationHelper` provides static methods called from woven bytecode (`onExecuteEnter`, `onExecuteExit`, `onExecuteError`)
- The instrumentation scope is `org.eclipse.osgi.technology.opentelemetry.weaver.jdbc`

### JAX-RS Weaver Details

- Instruments classes annotated with `@javax.ws.rs.Path` (detected via ASM `AnnotationVisitor` — no JAX-RS compile-time dependency)
- Only instruments methods annotated with HTTP method annotations (`@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`, `@HEAD`, `@OPTIONS`)
- Combines class-level and method-level `@Path` values to compute the full route
- Creates `INTERNAL` spans (not `SERVER` — the outer servlet span already provides `SERVER` kind)
- Span attributes: `http.method`, `http.route`, `jaxrs.resource.class`, `jaxrs.resource.method`
- Records `jaxrs.server.requests` counter and `jaxrs.server.duration` histogram metrics
- `JaxRsInstrumentationHelper` provides static methods called from woven bytecode (`onMethodEnter`, `onMethodExit`, `onMethodError`)
- The instrumentation scope is `org.eclipse.osgi.technology.opentelemetry.weaver.jaxrs`

### SCR Lifecycle Weaver Details

- Inspired by [biz.aQute.trace](https://github.com/aQute-os/biz.aQute.osgi.util), uses DS XML parsing to identify component classes and their lifecycle methods
- Reads the `Service-Component` manifest header, parses XML files, extracts `<implementation class="...">` FQNs and `<component>` element attributes
- Parses lifecycle method names from XML attributes with DS spec defaults: `activate` (default "activate"), `deactivate` (default "deactivate"), `modified` (no default), `init` (default 0)
- **No annotation scanning** — the DS XML descriptor is the single source of truth, as annotations are not mandatory for DS components
- Caches parsed `ComponentDescriptor` records per bundle ID in a `ConcurrentHashMap` for efficient `canWeave()` checks
- Handles wildcard patterns in `Service-Component` header (e.g., `OSGI-INF/*.xml`) via `Bundle.findEntries()`
- Matches methods by name from the XML descriptor; matches constructors when `init > 0` (constructor injection)
- Creates `INTERNAL` spans with `scr.component.name`, `scr.component.class`, `scr.lifecycle.action`, `scr.method.name` attributes
- Span names: `scr.<action> <SimpleClassName>` (e.g., `scr.activate HealthCheckInventoryComponent`)
- Records `scr.lifecycle.operations` counter and `scr.lifecycle.duration` histogram metrics
- `ScrInstrumentationHelper` provides static methods called from woven bytecode (`onLifecycleEnter`, `onLifecycleExit`, `onLifecycleError`)
- The instrumentation scope is `org.eclipse.osgi.technology.opentelemetry.weaver.scr`
- **Note**: Early component activations (before the OpenTelemetry service is registered) produce noop spans — this is the expected behavior of the `OpenTelemetryProxy`

### Build Differences from DS Modules

- Uses `Bundle-Activator` header instead of DS annotations
- Host bundle: bnd `Private-Package` includes ASM classes; `Import-Package` uses optional resolution for OpenTelemetry API
- Fragment bundle: bnd `Fragment-Host: opentelemetry-osgi-weaving` header; dependencies are `provided` scope (resolved via host)

### Adding New Weavers

1. Create a new module as a fragment bundle (`Fragment-Host: opentelemetry-osgi-weaving`)
2. Implement the `Weaver` interface (`name()`, `canWeave()`, `weave()`)
3. Use `SafeClassWriter` (from the host bundle) instead of plain `ClassWriter` when creating the ASM `ClassWriter`
4. Register via `META-INF/services/org.eclipse.osgi.technology.opentelemetry.weaving.Weaver`

## Common Pitfalls

- **`package-info.java`**: The Javadoc comment must come before the `package` declaration — do not repeat the `package` statement
- **OSGi scope**: OSGi dependencies must be `provided` scope in runtime and integration modules (the framework provides them at runtime)
- **bnd-maven-plugin + maven-jar-plugin**: Both are configured in the parent POM; the jar plugin reads the bnd-generated `MANIFEST.MF`
- **OTel JARs are NOT OSGi bundles**: They lack `Bundle-SymbolicName` headers and need to be wrapped for use in OSGi containers
- **SPI Fly**: OpenTelemetry uses `ServiceLoader` internally. In OSGi, cross-bundle SPI requires Apache Aries SPI Fly. Add `SPI-Consumer=*` / `SPI-Provider=*` headers to wrapped bundles.
- **OTLP exporter**: The OTLP/HTTP (`OtlpHttpOpenTelemetryService`) and OTLP/gRPC (`OtlpGrpcOpenTelemetryService`) services activate when their respective configurations exist (`...runtime.otlp.http` or `...runtime.otlp.grpc`). The `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable overrides the `endpoint` config property. OTLP services have `service.ranking=100`, making them preferred over the logging exporter. Both OTLP variants are in the same `opentelemetry-exporter-otlp` artifact — no extra dependency needed. HTTP uses port 4318 (appends /v1/traces etc.), gRPC uses port 4317 (single endpoint). The JDK sender (`opentelemetry-exporter-sender-jdk`) supports both HTTP and gRPC (gRPC over HTTP/2 via JDK HttpClient).
- **bnd osgi.service requirements**: The `-dsannotations-options: norequirements` bnd setting is required to suppress `Require-Capability: osgi.service` headers that can break feature resolvers
- **Relative paths**: Module POMs use `<relativePath>../../pom.xml</relativePath>` since modules are two levels deep (e.g. `core/opentelemetry-osgi-runtime/pom.xml`)
- **Maven groupId path**: The groupId `org.eclipse.osgi-technology` maps to `org/eclipse/osgi-technology/` in Maven repository layout (note: hyphen in path)
- **Felix HC executor defaultTags**: The `HealthCheckExecutorImpl` defaults to `defaultTags=["default"]` when `HealthCheckSelector.empty()` is used. This silently filters out checks tagged differently (e.g., `systemalive`). Deploy `org.apache.felix.hc.core.impl.executor.HealthCheckExecutorImpl.cfg` with `defaultTags=` (empty) to select ALL checks.
- **Felix HC general checks configurationPolicy**: Most general checks use `configurationPolicy=REQUIRE` — they will NOT activate without a `.cfg` file. Only `FrameworkStartCheck` has `OPTIONAL` policy. Factory checks (BundlesStartedCheck, DiskSpaceCheck) need `<PID>-<instance>.cfg` naming.
- **ConfigurationEvent has no properties**: `ConfigurationEvent.getReference()` returns the CM `ServiceReference`, not the configuration's properties. To get property values, fetch via `ConfigurationAdmin.getConfiguration(pid)`.
- **ConfigAdmin listConfigurations null**: `configAdmin.listConfigurations(null)` returns `null` when no configurations exist, not an empty array. Always null-check the result.
- **Weaving hook activation order**: The weaving host bundle uses `BundleActivator` (not DS) because the `WeavingHook` must be registered before DS component classes load. Do not convert it to DS.
- **Weaving ASM embedded**: ASM is included via `Private-Package` in the weaving host bundle. Do not add a separate ASM bundle — it would cause classloader visibility issues.
- **Weaving fragment SPI**: Fragment bundles register weavers via `META-INF/services`, not OSGi service registry. This works because fragments share the host's classloader, making `ServiceLoader.load()` discover them without SPI Fly.
- **Weaving SafeClassWriter**: All weavers must use `SafeClassWriter` (from the weaving host) instead of plain `ClassWriter` with `COMPUTE_FRAMES`. `SafeClassWriter` uses the target bundle's classloader (via `WovenClass.getBundleWiring().getClassLoader()`) for frame computation. Without this, complex classes (e.g., H2 `JdbcPreparedStatement`) cause `VerifyError: Bad type on operand stack` because incorrect type merging corrupts stack map frames in non-instrumented methods.
- **Weaving activation order**: Weaving bundles must be active before application bundles. Late activation would cause application classes to load before the WeavingHook is registered.
- **Weaving infrastructure exclusion**: The `OpenTelemetryWeavingHook` skips bundles from Felix, Karaf, Jetty, Pax, Aries, CXF, XBean, and Eclipse Equinox. When adding new infrastructure exclusions, update the `shouldSkipBundle()` method.
- **SCR weaver DS XML parsing**: The SCR lifecycle weaver parses the `Service-Component` manifest header and DS XML files to identify component implementation classes and lifecycle method names. It uses XML attributes with DS spec defaults (`activate`="activate", `deactivate`="deactivate", `modified`=none, `init`=0) — no annotation scanning is performed. Results are cached per bundle ID as `ComponentDescriptor` records.
- **SCR weaver noop early activations**: Components that activate before the OpenTelemetry service is registered will have their lifecycle methods instrumented but produce noop spans (the `OpenTelemetryProxy` returns noop tracers/meters until the real service arrives). This is expected — the OTel runtime is itself a DS component, so there's an inherent chicken-and-egg ordering.
- **MXBeans com.sun.management**: The MXBeans module uses `com.sun.management.OperatingSystemMXBean` for process/system CPU load and physical memory. This must be imported with `resolution:=optional` in bnd config, as it's a JVM-internal package that the OSGi resolver cannot satisfy. The code uses `instanceof` to degrade gracefully on non-HotSpot JVMs.
- **MXBeans OTel unit naming**: OTel metrics with unit `By` (bytes) become `_bytes` in Prometheus, and `ms` (milliseconds) becomes `_milliseconds`. Queries must use the Prometheus-converted names (e.g., `jvm_memory_used_bytes` not `jvm_memory_used_By`).
- **TypedEvent UntypedEventHandler marks events handled**: The Typed Event integration registers `UntypedEventHandler` services with `event.topics=*`. Per the spec, this makes ALL events "handled", so `UnhandledEventHandler` services registered by other bundles will never fire. This is a conscious trade-off for simpler implementation over `TypedEventMonitor`.
- **TypedEvent Aries Bus uses Component DSL**: The Apache Aries TypedEvent Bus implementation (`org.apache.aries.typedevent.bus`) does NOT use Declarative Services — it uses Aries Component DSL. This means 5 additional transitive dependencies are needed: Component DSL, Converter, PushStream, Promise, Function.
- **TypedEvent runtime deps are all OSGi bundles**: Unlike OTel JARs, all TypedEvent dependencies (Aries bus, OSGi util packages) have proper `Bundle-SymbolicName` headers.
- **HTTP Whiteboard javax vs Jakarta**: The correct Maven artifact is `org.osgi:org.osgi.service.http.whiteboard:1.1.1` (NOT `org.osgi.service.servlet.runtime` which is Jakarta namespace).
- **JAX-RS Whiteboard optional imports**: The JAX-RS API bundle (`org.osgi:org.osgi.service.jaxrs:1.0.0`) has `Require-Capability: osgi.contract=JavaJAXRS` and imports `javax.ws.rs.*`. The integration module uses `resolution:=optional` for all JAX-RS runtime imports to resolve without a full JAX-RS Whiteboard runtime installed.
- **Dynamic references use service as map key**: Integration components use `ConcurrentHashMap<ServiceType, State>` with the service object itself as key (not service.id). The service.id is only used as an OTel attribute for distinguishing time series. This matches OSGi service identity semantics.
- **Constructor injection for OpenTelemetry**: The `OpenTelemetry` reference must use constructor injection (`@Activate public Ctor(@Reference OpenTelemetry otel)`) to guarantee it's available before any dynamic `bind<Service>` methods are called by DS. Field injection with dynamic bindings can cause NPE if bind is called before activate.
- **Multiple async gauge registrations**: Calling `meter.gaugeBuilder("name").buildWithCallback()` multiple times with the same metric name creates separate callback registrations. Each produces its own time series distinguished by the `osgi.service.id` attribute value. All registrations must be closed independently on unbind.
