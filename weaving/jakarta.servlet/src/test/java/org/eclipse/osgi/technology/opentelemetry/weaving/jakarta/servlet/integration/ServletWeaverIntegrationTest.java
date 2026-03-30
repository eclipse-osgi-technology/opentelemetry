/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 * 
 */

package org.eclipse.osgi.technology.opentelemetry.weaving.jakarta.servlet.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.servlet.runtime.HttpServiceRuntime;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
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

@WithConfiguration(pid = "org.apache.felix.http", properties = {
		@Property(key = "org.osgi.service.http.port", value = "9999", scalar = Property.Scalar.Integer) })
public class ServletWeaverIntegrationTest {

	@InjectBundleContext
	BundleContext context;

	private final List<SpanData> exportedSpans = new CopyOnWriteArrayList<>();
	private SdkTracerProvider tracerProvider;
	private ServiceRegistration<OpenTelemetry> otelRegistration;

	@BeforeEach
	void setUp() {
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
	}

	@AfterEach
	void tearDown() {
		if (otelRegistration != null) {
			otelRegistration.unregister();
		}
		if (tracerProvider != null) {
			tracerProvider.close();
		}
		exportedSpans.clear();
	}

	@Test
	void servletRequestCreatesSpan(@InjectService(timeout = 10000) HttpServiceRuntime runtime) throws Exception {
		// Get actual endpoint from runtime service
		ServiceReference<HttpServiceRuntime> runtimeRef = context.getServiceReference(HttpServiceRuntime.class);
		Object endpoints = runtimeRef.getProperty("osgi.http.endpoint");
		String endpoint = endpoints instanceof String[] ? ((String[]) endpoints)[0] : String.valueOf(endpoints);

		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint + "test")).GET().build();

		// Retry until servlet from test.app bundle is registered
		HttpResponse<String> response = null;
		for (int i = 0; i < 20; i++) {
			Thread.sleep(500);
			response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				break;
			}
		}
		assertThat(response.statusCode()).isEqualTo(200);

		tracerProvider.forceFlush().join(5000, TimeUnit.MILLISECONDS);

		assertThat(exportedSpans).isNotEmpty();

		SpanData span = exportedSpans.stream().filter(s -> s.getName().contains("/test")).findFirst()
				.orElseThrow(() -> new AssertionError("No span found containing '/test'. Spans: " + exportedSpans));

		assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
		assertThat(span.getAttributes().get(AttributeKey.stringKey("http.method"))).isEqualTo("GET");
	}
}
