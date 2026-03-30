package org.eclipse.osgi.technology.opentelemetry.core.sender.logging.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration for the logging-based OpenTelemetry exporter.
 * <p>
 * Properties can be set via OSGi ConfigAdmin using the PID {@value #PID}.
 * <p>
 * The logging exporter outputs telemetry to stdout via
 * {@code java.util.logging}, useful for development and debugging without
 * requiring an external collector.
 */
@ObjectClassDefinition(name = "OpenTelemetry Logging Exporter", description = "Exports telemetry (traces, metrics, logs) to stdout via java.util.logging. "
		+ "Useful for development and debugging without an external collector.")
public @interface LoggingOpenTelemetryConfiguration {

	String COMPONENT_NAME = "logging-opentelemetry";


	@AttributeDefinition(name = "Service Name", description = "Logical name of the service reported in all telemetry data as the "
			+ "'service.name' resource attribute. Can be overridden by the OTEL_SERVICE_NAME "
			+ "environment variable.")
	String serviceName() default "osgi-application";

	@AttributeDefinition(name = "Service Version", description = "Version of the service reported as the 'service.version' resource attribute.")
	String serviceVersion() default "0.1.0";

	@AttributeDefinition(name = "Service Namespace", description = "Optional logical grouping for the service reported as the "
			+ "'service.namespace' resource attribute. Leave empty if not needed.")
	String serviceNamespace() default "";

	@AttributeDefinition(name = "Additional Resource Attributes", description = "Extra resource attributes added to all telemetry as 'key=value' pairs. "
			+ "These become part of the OpenTelemetry Resource and appear on every trace, metric, "
			+ "and log record. Example: deployment.environment=production")
	String[] additionalResourceAttributes() default {};
}
