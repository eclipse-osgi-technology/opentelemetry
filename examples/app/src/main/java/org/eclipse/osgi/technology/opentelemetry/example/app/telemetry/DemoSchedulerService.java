package org.eclipse.osgi.technology.opentelemetry.example.app.telemetry;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Periodically triggers all telemetry producers to generate a continuous stream
 * of traces, metrics, and log records.
 *
 * <p>
 * Runs every 60 seconds after a 10-second initial delay. Each cycle:
 * <ul>
 * <li>Creates traced operations via {@link TracingProducerService}</li>
 * <li>Records metrics via {@link MetricsProducerService}</li>
 * <li>Emits structured logs via {@link LogProducerService}</li>
 * <li>Performs JDBC operations via {@link JdbcTelemetryService}</li>
 * <li>Demonstrates context propagation via
 * {@link ContextPropagationService}</li>
 * </ul>
 */
@Component(immediate = true)
public class DemoSchedulerService {

	@Reference
	private MetricsProducerService metricsProducer;

	@Reference
	private TracingProducerService tracingProducer;

	@Reference
	private LogProducerService logProducer;

	@Reference
	private JdbcTelemetryService jdbcService;

	@Reference
	private ContextPropagationService contextPropagation;

	private ScheduledExecutorService scheduler;

	private static final String[] OPERATIONS = { "bundle.resolve", "service.lookup", "config.update", "bundle.refresh",
			"service.bind", "package.wire" };

	@Activate
	void activate() {
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "otel-demo-scheduler");
			t.setDaemon(true);
			return t;
		});
		scheduler.scheduleAtFixedRate(this::runCycle, 10, 60, TimeUnit.SECONDS);
	}

	@Deactivate
	void deactivate() {
		if (scheduler != null) {
			scheduler.shutdownNow();
		}
	}

	private void runCycle() {
		try {
			String operation = OPERATIONS[ThreadLocalRandom.current().nextInt(OPERATIONS.length)];

			// Tracing
			tracingProducer.executeWithTracing("demo.cycle." + operation, () -> {
				simulateWork(ThreadLocalRandom.current().nextInt(10, 100));
			});

			// Nested spans
			tracingProducer.executeNestedOperation();

			// Error span (every 5th cycle roughly)
			if (ThreadLocalRandom.current().nextInt(5) == 0) {
				tracingProducer.executeWithError("demo.simulated-failure");
			}

			// Metrics
			metricsProducer.recordRequest("scheduled", operation);
			metricsProducer.recordDuration(ThreadLocalRandom.current().nextDouble(5, 200));
			metricsProducer.incrementActive();
			simulateWork(20);
			metricsProducer.decrementActive();

			// Logs
			logProducer.logBusinessEvent("demo.cycle.completed",
					Map.of("operation", operation, "timestamp", Instant.now().toString()));

			// JDBC
			long id = jdbcService.insertEvent(operation, "Cycle at " + Instant.now());
			metricsProducer.setStoreSize(jdbcService.queryEvents().size());

			// Cleanup old events (keep last 50)
			jdbcService.deleteOldEvents(50);

			// Context propagation
			contextPropagation.demonstratePropagation();

		} catch (Exception e) {
			logProducer.logError("Demo cycle failed", e);
		}
	}

	private void simulateWork(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
