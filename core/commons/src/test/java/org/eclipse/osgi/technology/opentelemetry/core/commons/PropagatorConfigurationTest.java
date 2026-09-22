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

package org.eclipse.osgi.technology.opentelemetry.core.commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

/**
 * Propagators built from configuration.
 * <p>
 * The assertion that matters is the round trip: a context injected by one
 * process has to come back out at the other, because that is the difference
 * between one trace and two.
 */
public class PropagatorConfigurationTest {

	private static final TextMapSetter<Map<String, String>> SETTER = (carrier, key, value) -> carrier.put(key, value);

	private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {

		@Override
		public Iterable<String> keys(Map<String, String> carrier) {
			return carrier.keySet();
		}

		@Override
		public String get(Map<String, String> carrier, String key) {
			return carrier == null ? null : carrier.get(key);
		}
	};

	/** Only {@code buildPropagators} is under test; no SDK is needed for it. */
	private static final class Configured extends AbstractOpenTelemetryService {

		ContextPropagators propagators(String... names) {
			return buildPropagators(names);
		}
	}

	@Test
	void defaultsInjectAndExtractW3CTraceContext() {
		Map<String, String> carrier = new HashMap<>();
		SdkTracerProvider tracers = SdkTracerProvider.builder().build();
		Span span = tracers.get("test").spanBuilder("call").startSpan();
		ContextPropagators propagators = new Configured().propagators();

		try (var scope = span.makeCurrent()) {
			propagators.getTextMapPropagator().inject(Context.current(), carrier, SETTER);
		} finally {
			span.end();
			tracers.close();
		}

		assertThat(carrier).containsKey("traceparent");

		Context extracted = propagators.getTextMapPropagator().extract(Context.root(), carrier, GETTER);
		assertThat(Span.fromContext(extracted).getSpanContext().getTraceId())
				.isEqualTo(span.getSpanContext().getTraceId());
	}

	@Test
	void noneDisablesPropagationEntirely() {
		Map<String, String> carrier = new HashMap<>();

		new Configured().propagators("none").getTextMapPropagator().inject(Context.current(), carrier, SETTER);

		assertThat(carrier).isEmpty();
	}

	@Test
	void anUnknownNameIsIgnoredRatherThanFatal() {
		Map<String, String> carrier = new HashMap<>();
		SdkTracerProvider tracers = SdkTracerProvider.builder().build();
		Span span = tracers.get("test").spanBuilder("call").startSpan();

		ContextPropagators propagators = new Configured().propagators("b3", "tracecontext");
		try (var scope = span.makeCurrent()) {
			propagators.getTextMapPropagator().inject(Context.current(), carrier, SETTER);
		} finally {
			span.end();
			tracers.close();
		}

		assertThat(carrier)
				.as("a configuration that names one propagator we do not have still gets the ones we do")
				.containsKey("traceparent");
	}

	@Test
	void fieldsAreReportedSoACarrierCanBeCleared() {
		assertThat(new Configured().propagators().getTextMapPropagator().fields()).contains("traceparent", "baggage");
	}
}
