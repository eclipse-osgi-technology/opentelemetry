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

package org.eclipse.osgi.technology.opentelemetry.example.app.telemetry;

import java.util.Map;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Severity;

/**
 * Active telemetry producer that creates structured log records using the
 * OpenTelemetry Logs Bridge API.
 *
 * <p>
 * Uses an <b>optional, dynamic</b> reference to {@link OpenTelemetry} with
 * bind/unbind. When the service is absent, log operations are silently dropped.
 * This pattern ensures the component never blocks on telemetry availability.
 */
@Component(service = LogProducerService.class, immediate = true)
public class LogProducerService {

	private static final Logger LOG = Logger.getLogger(LogProducerService.class.getName());
	private static final String SCOPE = "org.eclipse.osgi.technology.opentelemetry.example.telemetry";

	private volatile io.opentelemetry.api.logs.Logger otelLogger;

	@Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
	void bindOpenTelemetry(OpenTelemetry openTelemetry) {
		LOG.info("OpenTelemetry service bound — OTel logger created");
		otelLogger = openTelemetry.getLogsBridge().loggerBuilder(SCOPE).setInstrumentationVersion("0.1.0").build();
	}

	void unbindOpenTelemetry(OpenTelemetry openTelemetry) {
		LOG.info("OpenTelemetry service unbound — OTel logger cleared");
		otelLogger = null;
	}

	public void logBusinessEvent(String event, Map<String, String> attributes) {
		io.opentelemetry.api.logs.Logger logger = otelLogger;
		if (logger == null)
			return;

		AttributesBuilder builder = Attributes.builder().put(AttributeKey.stringKey("event.name"), event);
		attributes.forEach((k, v) -> builder.put(AttributeKey.stringKey(k), v));

		logger.logRecordBuilder().setSeverity(Severity.INFO).setBody("Business event: " + event)
				.setAllAttributes(builder.build()).emit();
	}

	public void logWarning(String message) {
		io.opentelemetry.api.logs.Logger logger = otelLogger;
		if (logger == null)
			return;

		logger.logRecordBuilder().setSeverity(Severity.WARN).setBody(message)
				.setAttribute(AttributeKey.stringKey("log.source"), "example").emit();
	}

	public void logError(String message, Throwable t) {
		io.opentelemetry.api.logs.Logger logger = otelLogger;
		if (logger == null)
			return;

		logger.logRecordBuilder().setSeverity(Severity.ERROR).setBody(message)
				.setAttribute(AttributeKey.stringKey("exception.type"), t.getClass().getName())
				.setAttribute(AttributeKey.stringKey("exception.message"), t.getMessage() != null ? t.getMessage() : "")
				.emit();
	}
}
