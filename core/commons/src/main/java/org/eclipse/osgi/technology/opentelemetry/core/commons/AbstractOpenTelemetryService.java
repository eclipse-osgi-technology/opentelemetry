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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;

/**
 * Abstract base class for OpenTelemetry SDK service implementations.
 * <p>
 * Provides common functionality shared by all exporter-specific services:
 * resource building with OSGi framework attributes, SDK lifecycle management,
 * and delegation of {@link OpenTelemetry} interface methods to the underlying
 * SDK.
 * <p>
 * Subclasses implement exporter-specific SDK construction and register as
 * {@link OpenTelemetry} services with their own configuration PID.
 */
public abstract class AbstractOpenTelemetryService implements OpenTelemetry {

	private static final Logger LOG = Logger.getLogger(AbstractOpenTelemetryService.class.getName());

	private volatile OpenTelemetrySdk sdk;

	/**
	 * Sets a new SDK instance, closing the previous one if present.
	 */
	protected void setSdk(OpenTelemetrySdk newSdk) {
		OpenTelemetrySdk old = this.sdk;
		this.sdk = newSdk;
		if (old != null) {
			old.close();
		}
	}

	/**
	 * Closes and removes the current SDK instance.
	 */
	protected void closeSdk() {
		OpenTelemetrySdk old = this.sdk;
		this.sdk = null;
		if (old != null) {
			old.close();
		}
	}

	/**
	 * Resolves the service name, preferring the {@code OTEL_SERVICE_NAME}
	 * environment variable over the configuration value.
	 */
	protected String resolveServiceName(String configValue) {
		String env = System.getenv("OTEL_SERVICE_NAME");
		return (env != null && !env.isEmpty()) ? env : configValue;
	}

	/**
	 * Resolves the OTLP endpoint, preferring the
	 * {@code OTEL_EXPORTER_OTLP_ENDPOINT} environment variable over the
	 * configuration value.
	 */
	protected String resolveEndpoint(String configValue) {
		String env = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT");
		return (env != null && !env.isEmpty()) ? env : configValue;
	}

	/**
	 * Converts a timeout value in milliseconds to a {@link Duration}.
	 */
	protected Duration toDuration(long millis) {
		return Duration.ofMillis(millis);
	}

	/**
	 * Parses an array of {@code key=value} strings into a map.
	 */
	protected Map<String, String> parseKeyValuePairs(String[] pairs) {
		Map<String, String> result = new LinkedHashMap<>();
		if (pairs != null) {
			for (String pair : pairs) {
				int eq = pair.indexOf('=');
				if (eq > 0) {
					result.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
				}
			}
		}
		return result;
	}

	/**
	 * Reads a PEM-encoded file from disk.
	 *
	 * @return the file contents, or {@code null} if the path is empty or the file
	 *         cannot be read
	 */
	protected byte[] readPemFile(String path) {
		if (path == null || path.isEmpty()) {
			return null;
		}
		try {
			return Files.readAllBytes(Path.of(path));
		} catch (IOException e) {
			LOG.log(Level.WARNING, "Failed to read PEM file: " + path, e);
			return null;
		}
	}

	/**
	 * Builds the OpenTelemetry {@link Resource} with OSGi framework attributes.
	 */
	protected Resource buildResource(BundleContext context, String serviceName, String serviceVersion,
			String serviceNamespace, String[] additionalResourceAttributes) {
		AttributesBuilder attrs = Attributes.builder().put(AttributeKey.stringKey("service.name"), serviceName)
				.put(AttributeKey.stringKey("service.version"), serviceVersion);

		if (serviceNamespace != null && !serviceNamespace.isEmpty()) {
			attrs.put(AttributeKey.stringKey("service.namespace"), serviceNamespace);
		}

		String vendor = context.getProperty("org.osgi.framework.vendor");
		if (vendor != null) {
			attrs.put(AttributeKey.stringKey("osgi.framework.vendor"), vendor);
		}
		String version = context.getProperty("org.osgi.framework.version");
		if (version != null) {
			attrs.put(AttributeKey.stringKey("osgi.framework.version"), version);
		}
		String uuid = context.getProperty("org.osgi.framework.uuid");
		if (uuid != null) {
			attrs.put(AttributeKey.stringKey("service.instance.id"), uuid);
			attrs.put(AttributeKey.stringKey("osgi.framework.uuid"), uuid);
		}

		if (additionalResourceAttributes != null) {
			for (String attr : additionalResourceAttributes) {
				int eq = attr.indexOf('=');
				if (eq > 0) {
					String key = attr.substring(0, eq).trim();
					String value = attr.substring(eq + 1).trim();
					attrs.put(AttributeKey.stringKey(key), value);
				}
			}
		}

		return Resource.getDefault().merge(Resource.create(attrs.build()));
	}

	@Override
	public io.opentelemetry.api.trace.TracerProvider getTracerProvider() {
		return getSdk().getTracerProvider();
	}

	@Override
	public io.opentelemetry.api.metrics.MeterProvider getMeterProvider() {
		return getSdk().getMeterProvider();
	}

	@Override
	public io.opentelemetry.api.logs.LoggerProvider getLogsBridge() {
		return getSdk().getLogsBridge();
	}

	@Override
	public io.opentelemetry.context.propagation.ContextPropagators getPropagators() {
		return getSdk().getPropagators();
	}

	private OpenTelemetrySdk getSdk() {
		OpenTelemetrySdk current = sdk;
		if (current == null) {
			LOG.log(Level.WARNING, "OpenTelemetry SDK not yet initialized, returning noop");
			return OpenTelemetrySdk.builder().build();
		}
		return current;
	}
}
