package org.eclipse.osgi.technology.opentelemetry.weaving.jdbc.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.Property;
import org.osgi.test.common.annotation.config.WithConfiguration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class JdbcWeaverIntegrationTest {

	@InjectBundleContext
	BundleContext context;

	private final List<SpanData> exportedSpans = new CopyOnWriteArrayList<>();
	private SdkTracerProvider tracerProvider;
	private ServiceRegistration<OpenTelemetry> otelRegistration;
	private Connection connection;

	@BeforeEach
	void setUp() throws Exception {
		SpanExporter testExporter = new SpanExporter() {
			@Override
			public CompletableResultCode export(Collection<SpanData> spans) {
				exportedSpans.addAll(spans);
				return CompletableResultCode.ofSuccess();
			}

			@Override
			public CompletableResultCode flush() {
				return CompletableResultCode.ofSuccess();
			}

			@Override
			public CompletableResultCode shutdown() {
				return CompletableResultCode.ofSuccess();
			}
		};

		tracerProvider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(testExporter)).build();

		OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

		Dictionary<String, Object> otelProps = new Hashtable<>();
		otelProps.put(org.osgi.framework.Constants.SERVICE_RANKING, Integer.MAX_VALUE);
		otelRegistration = context.registerService(OpenTelemetry.class, sdk, otelProps);

		org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
		ds.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
		connection = ds.getConnection();
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT PRIMARY KEY, name VARCHAR(100))");
		}
		// Clear spans from setup operations
		tracerProvider.forceFlush().join(5000, TimeUnit.MILLISECONDS);
		exportedSpans.clear();
	}

	@AfterEach
	void tearDown() throws Exception {
		if (connection != null && !connection.isClosed()) {
			try (Statement stmt = connection.createStatement()) {
				stmt.execute("DROP TABLE IF EXISTS test_table");
			}
			connection.close();
		}
		if (otelRegistration != null) {
			otelRegistration.unregister();
		}
		if (tracerProvider != null) {
			tracerProvider.close();
		}
		exportedSpans.clear();
	}

	@Test
	void selectQueryCreatesSpan() throws Exception {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM test_table")) {
			// consume result
		}

		tracerProvider.forceFlush().join(5000, TimeUnit.MILLISECONDS);

		assertThat(exportedSpans).isNotEmpty();

		SpanData span = exportedSpans.stream().filter(s -> s.getName().equals("SELECT")).findFirst()
				.orElseThrow(() -> new AssertionError("No SELECT span found. Spans: " + exportedSpans));

		assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
		assertThat(span.getAttributes().get(AttributeKey.stringKey("db.system"))).isEqualTo("jdbc");
		assertThat(span.getAttributes().get(AttributeKey.stringKey("db.statement"))).contains("SELECT");
		assertThat(span.getAttributes().get(AttributeKey.stringKey("db.operation"))).isEqualTo("executeQuery");
	}

	@Test
	void insertCreatesSpan() throws Exception {
		try (Statement stmt = connection.createStatement()) {
			stmt.executeUpdate("INSERT INTO test_table VALUES (1, 'Alice')");
		}

		tracerProvider.forceFlush().join(5000, TimeUnit.MILLISECONDS);

		assertThat(exportedSpans).isNotEmpty();

		SpanData span = exportedSpans.stream().filter(s -> s.getName().equals("INSERT")).findFirst()
				.orElseThrow(() -> new AssertionError("No INSERT span found. Spans: " + exportedSpans));

		assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
		assertThat(span.getAttributes().get(AttributeKey.stringKey("db.system"))).isEqualTo("jdbc");
		assertThat(span.getAttributes().get(AttributeKey.stringKey("db.statement"))).contains("INSERT");
	}
}
