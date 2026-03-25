# OpenTelemetry MXBeans Bridge

Exposes [Java Management Extensions (MXBeans)](https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/ManagementFactory.html) as OpenTelemetry metrics.
All metrics use `java.lang.management.ManagementFactory` MXBeans with no external dependencies.

## Features

Seven configurable metric groups, individually enabled/disabled via OSGi Configuration Admin:

| Group | Default | Description |
|---|---|---|
| `memoryEnabled` | `true` | Heap/non-heap memory, JVM uptime, physical memory |
| `cpuEnabled` | `true` | Process/system CPU load, load average, processors |
| `threadsEnabled` | `true` | Live, daemon, peak, total started threads |
| `gcEnabled` | `true` | Collection count and time per garbage collector |
| `classLoadingEnabled` | `true` | Loaded, total loaded, unloaded class counts |
| `bufferPoolsEnabled` | `true` | Per-pool buffer count, memory, capacity |
| `memoryPoolsEnabled` | `true` | Per-pool used, committed, max memory |

## Generated Telemetry

### Metrics

All metrics are registered as OpenTelemetry async gauges (callbacks) — no background threads or polling.

**Memory:**

| Metric | Unit | Description |
|---|---|---|
| `jvm.memory.used` | bytes | Heap and non-heap memory used |
| `jvm.memory.committed` | bytes | Heap and non-heap memory committed |
| `jvm.memory.max` | bytes | Maximum heap memory |
| `jvm.uptime` | ms | JVM uptime in milliseconds |
| `jvm.memory.physical.total` | bytes | Total physical memory |
| `jvm.memory.physical.free` | bytes | Free physical memory |

**CPU:**

| Metric | Unit | Description |
|---|---|---|
| `jvm.cpu.process.load` | ratio | Process CPU load (0.0–1.0) |
| `jvm.cpu.system.load` | ratio | System CPU load (0.0–1.0) |
| `jvm.cpu.load.average` | ratio | System load average |
| `jvm.cpu.available.processors` | count | Available processor count |

**Threads:**

| Metric | Unit | Description |
|---|---|---|
| `jvm.threads.live` | count | Current live thread count |
| `jvm.threads.daemon` | count | Current daemon thread count |
| `jvm.threads.peak` | count | Peak thread count since JVM start |
| `jvm.threads.started` | count | Total started thread count |

**GC** (per collector):

| Metric | Unit | Description |
|---|---|---|
| `jvm.gc.collection.count` | count | Total garbage collections |
| `jvm.gc.collection.time` | ms | Total garbage collection time |

**Class Loading:**

| Metric | Unit | Description |
|---|---|---|
| `jvm.classes.loaded` | count | Currently loaded classes |
| `jvm.classes.loaded.total` | count | Total loaded classes since JVM start |
| `jvm.classes.unloaded` | count | Total unloaded classes |

**Memory Pools** (per pool, e.g. G1 Eden Space, Metaspace):

| Metric | Unit | Description |
|---|---|---|
| `jvm.memory.pool.used` | bytes | Pool memory used |
| `jvm.memory.pool.committed` | bytes | Pool memory committed |
| `jvm.memory.pool.max` | bytes | Pool maximum memory |

**Buffer Pools** (per pool):

| Metric | Unit | Description |
|---|---|---|
| `jvm.buffer.pool.count` | count | Buffer count in pool |
| `jvm.buffer.pool.used` | bytes | Memory used by pool |
| `jvm.buffer.pool.capacity` | bytes | Total pool capacity |

## Configuration

PID: `org.eclipse.osgi.technology.opentelemetry.mxbeans`

Each metric group can be independently enabled or disabled.
Supports `@Modified` for dynamic reconfiguration without restart.

## Components

| Class | Description |
|---|---|
| `MxBeansMetricsComponent` | DS component with `@Modified` support registering async gauge callbacks |
| `MxBeansConfiguration` | Configuration annotation interface defining metric group toggles |

## Platform Notes

Uses `com.sun.management.OperatingSystemMXBean` for process/system CPU load and physical memory.
This is imported with `resolution:=optional` and degrades gracefully on non-HotSpot JVMs using `instanceof` checks.
