package org.eclipse.osgi.technology.opentelemetry.integration.mxbeans;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Exposes Java MXBean metrics as OpenTelemetry async gauges.
 * <p>
 * Reads from {@link ManagementFactory} MXBeans and publishes:
 * <ul>
 *   <li>Memory — heap/non-heap used, committed, max</li>
 *   <li>CPU — system load average, available processors, process CPU load</li>
 *   <li>Threads — live thread count, daemon count, peak count</li>
 *   <li>GC — collection count and time per collector</li>
 *   <li>Class loading — loaded, unloaded, total loaded class counts</li>
 *   <li>Buffer pools — count, used memory, capacity per pool</li>
 *   <li>Memory pools — used, committed, max per pool</li>
 * </ul>
 * <p>
 * Individual metric groups can be enabled/disabled via {@link MxBeansConfiguration}.
 */
@Component(immediate = true, configurationPid = "org.eclipse.osgi.technology.opentelemetry.mxbeans")
public class MxBeansMetricsComponent {

    private static final Logger LOG = Logger.getLogger(MxBeansMetricsComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.integration.mxbeans";

    private static final AttributeKey<String> AREA_KEY = AttributeKey.stringKey("jvm.memory.area");
    private static final AttributeKey<String> GC_NAME_KEY = AttributeKey.stringKey("jvm.gc.name");
    private static final AttributeKey<String> POOL_NAME_KEY = AttributeKey.stringKey("jvm.memory.pool.name");
    private static final AttributeKey<String> BUFFER_POOL_KEY = AttributeKey.stringKey("jvm.buffer.pool.name");

    @Reference(policy = ReferencePolicy.DYNAMIC)
    private volatile OpenTelemetry openTelemetry;

    private final List<AutoCloseable> gauges = new ArrayList<>();

    @Activate
    void activate(MxBeansConfiguration config) {
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);
        registerMetrics(meter, config);
        LOG.info("MxBeansMetricsComponent activated");
    }

    @Modified
    void modified(MxBeansConfiguration config) {
        closeGauges();
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);
        registerMetrics(meter, config);
        LOG.info("MxBeansMetricsComponent configuration updated");
    }

    @Deactivate
    void deactivate() {
        closeGauges();
        LOG.info("MxBeansMetricsComponent deactivated");
    }

    private void registerMetrics(Meter meter, MxBeansConfiguration config) {
        if (config.memoryEnabled()) {
            registerMemoryMetrics(meter);
        }
        if (config.cpuEnabled()) {
            registerCpuMetrics(meter);
        }
        if (config.threadsEnabled()) {
            registerThreadMetrics(meter);
        }
        if (config.gcEnabled()) {
            registerGcMetrics(meter);
        }
        if (config.classLoadingEnabled()) {
            registerClassLoadingMetrics(meter);
        }
        if (config.bufferPoolsEnabled()) {
            registerBufferPoolMetrics(meter);
        }
        if (config.memoryPoolsEnabled()) {
            registerMemoryPoolMetrics(meter);
        }
    }

    private void registerMemoryMetrics(Meter meter) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

        gauges.add(meter.gaugeBuilder("jvm.memory.used")
                .setDescription("Amount of used memory")
                .setUnit("By")
                .ofLongs()
                .buildWithCallback(measurement -> {
                    MemoryUsage heap = memory.getHeapMemoryUsage();
                    MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
                    measurement.record(heap.getUsed(), Attributes.of(AREA_KEY, "heap"));
                    measurement.record(nonHeap.getUsed(), Attributes.of(AREA_KEY, "non_heap"));
                }));

        gauges.add(meter.gaugeBuilder("jvm.memory.committed")
                .setDescription("Amount of committed memory")
                .setUnit("By")
                .ofLongs()
                .buildWithCallback(measurement -> {
                    MemoryUsage heap = memory.getHeapMemoryUsage();
                    MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
                    measurement.record(heap.getCommitted(), Attributes.of(AREA_KEY, "heap"));
                    measurement.record(nonHeap.getCommitted(), Attributes.of(AREA_KEY, "non_heap"));
                }));

        gauges.add(meter.gaugeBuilder("jvm.memory.max")
                .setDescription("Maximum amount of memory")
                .setUnit("By")
                .ofLongs()
                .buildWithCallback(measurement -> {
                    MemoryUsage heap = memory.getHeapMemoryUsage();
                    MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
                    long heapMax = heap.getMax() > 0 ? heap.getMax() : 0;
                    long nonHeapMax = nonHeap.getMax() > 0 ? nonHeap.getMax() : 0;
                    measurement.record(heapMax, Attributes.of(AREA_KEY, "heap"));
                    measurement.record(nonHeapMax, Attributes.of(AREA_KEY, "non_heap"));
                }));

        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        gauges.add(meter.gaugeBuilder("jvm.uptime")
                .setDescription("JVM uptime")
                .setUnit("ms")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(runtime.getUptime())));
    }

    private void registerCpuMetrics(Meter meter) {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

        gauges.add(meter.gaugeBuilder("jvm.cpu.system_load_average")
                .setDescription("System CPU load average over the last minute")
                .buildWithCallback(measurement -> {
                    double load = os.getSystemLoadAverage();
                    if (load >= 0) {
                        measurement.record(load);
                    }
                }));

        gauges.add(meter.gaugeBuilder("jvm.cpu.available_processors")
                .setDescription("Number of available processors")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(os.getAvailableProcessors())));

        // Try to access com.sun.management for process CPU load
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            gauges.add(meter.gaugeBuilder("jvm.cpu.process_load")
                    .setDescription("Process CPU load (0.0 to 1.0)")
                    .buildWithCallback(measurement -> measurement.record(sunOs.getProcessCpuLoad())));

            gauges.add(meter.gaugeBuilder("jvm.cpu.system_load")
                    .setDescription("System CPU load (0.0 to 1.0)")
                    .buildWithCallback(measurement -> measurement.record(sunOs.getCpuLoad())));

            gauges.add(meter.gaugeBuilder("jvm.memory.physical.total")
                    .setDescription("Total physical memory size")
                    .setUnit("By")
                    .ofLongs()
                    .buildWithCallback(measurement -> measurement.record(sunOs.getTotalMemorySize())));

            gauges.add(meter.gaugeBuilder("jvm.memory.physical.free")
                    .setDescription("Free physical memory size")
                    .setUnit("By")
                    .ofLongs()
                    .buildWithCallback(measurement -> measurement.record(sunOs.getFreeMemorySize())));
        }
    }

    private void registerThreadMetrics(Meter meter) {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();

        gauges.add(meter.gaugeBuilder("jvm.threads.live")
                .setDescription("Current live thread count")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(threads.getThreadCount())));

        gauges.add(meter.gaugeBuilder("jvm.threads.daemon")
                .setDescription("Current daemon thread count")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(threads.getDaemonThreadCount())));

        gauges.add(meter.gaugeBuilder("jvm.threads.peak")
                .setDescription("Peak thread count since JVM start")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(threads.getPeakThreadCount())));

        gauges.add(meter.gaugeBuilder("jvm.threads.started")
                .setDescription("Total number of threads started since JVM start")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(threads.getTotalStartedThreadCount())));
    }

    private void registerGcMetrics(Meter meter) {
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            Attributes attrs = Attributes.of(GC_NAME_KEY, gc.getName());

            gauges.add(meter.gaugeBuilder("jvm.gc.collection_count")
                    .setDescription("Total number of GC collections")
                    .ofLongs()
                    .buildWithCallback(measurement -> measurement.record(gc.getCollectionCount(), attrs)));

            gauges.add(meter.gaugeBuilder("jvm.gc.collection_time")
                    .setDescription("Total time spent in GC")
                    .setUnit("ms")
                    .ofLongs()
                    .buildWithCallback(measurement -> measurement.record(gc.getCollectionTime(), attrs)));
        }
    }

    private void registerClassLoadingMetrics(Meter meter) {
        ClassLoadingMXBean classLoading = ManagementFactory.getClassLoadingMXBean();

        gauges.add(meter.gaugeBuilder("jvm.classes.loaded")
                .setDescription("Number of currently loaded classes")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(classLoading.getLoadedClassCount())));

        gauges.add(meter.gaugeBuilder("jvm.classes.unloaded")
                .setDescription("Total number of unloaded classes since JVM start")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(classLoading.getUnloadedClassCount())));

        gauges.add(meter.gaugeBuilder("jvm.classes.total_loaded")
                .setDescription("Total number of classes loaded since JVM start")
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(classLoading.getTotalLoadedClassCount())));
    }

    private void registerBufferPoolMetrics(Meter meter) {
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            Attributes attrs = Attributes.of(BUFFER_POOL_KEY, pool.getName());

            gauges.add(meter.gaugeBuilder("jvm.buffer.count")
                    .setDescription("Number of buffers in the pool")
                    .ofLongs()
                    .buildWithCallback(measurement -> measurement.record(pool.getCount(), attrs)));

            gauges.add(meter.gaugeBuilder("jvm.buffer.memory_used")
                    .setDescription("Amount of memory used by buffers")
                    .setUnit("By")
                    .ofLongs()
                    .buildWithCallback(measurement -> measurement.record(pool.getMemoryUsed(), attrs)));

            gauges.add(meter.gaugeBuilder("jvm.buffer.total_capacity")
                    .setDescription("Total capacity of buffers")
                    .setUnit("By")
                    .ofLongs()
                    .buildWithCallback(measurement -> measurement.record(pool.getTotalCapacity(), attrs)));
        }
    }

    private void registerMemoryPoolMetrics(Meter meter) {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            Attributes attrs = Attributes.of(POOL_NAME_KEY, pool.getName());

            gauges.add(meter.gaugeBuilder("jvm.memory.pool.used")
                    .setDescription("Amount of used memory in pool")
                    .setUnit("By")
                    .ofLongs()
                    .buildWithCallback(measurement -> {
                        MemoryUsage usage = pool.getUsage();
                        if (usage != null) {
                            measurement.record(usage.getUsed(), attrs);
                        }
                    }));

            gauges.add(meter.gaugeBuilder("jvm.memory.pool.committed")
                    .setDescription("Amount of committed memory in pool")
                    .setUnit("By")
                    .ofLongs()
                    .buildWithCallback(measurement -> {
                        MemoryUsage usage = pool.getUsage();
                        if (usage != null) {
                            measurement.record(usage.getCommitted(), attrs);
                        }
                    }));

            gauges.add(meter.gaugeBuilder("jvm.memory.pool.max")
                    .setDescription("Maximum memory in pool")
                    .setUnit("By")
                    .ofLongs()
                    .buildWithCallback(measurement -> {
                        MemoryUsage usage = pool.getUsage();
                        if (usage != null && usage.getMax() > 0) {
                            measurement.record(usage.getMax(), attrs);
                        }
                    }));
        }
    }

    private void closeGauges() {
        for (AutoCloseable gauge : gauges) {
            try {
                gauge.close();
            } catch (Exception e) {
                LOG.log(Level.FINE, "Failed to close gauge", e);
            }
        }
        gauges.clear();
    }
}
