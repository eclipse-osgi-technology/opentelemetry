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

package org.eclipse.osgi.technology.opentelemetry.example.app.config;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Creates the configuration for the OtlpHttpOpenTelemetryService so it
 * activates (it uses configurationPolicy=REQUIRE). Exports telemetry via
 * OTLP/HTTP to localhost:4318.
 */
@Component(immediate = true)
public class OpenTelemetryConfigurator {

	private static final String OTLP_HTTP_PID = org.eclipse.osgi.technology.opentelemetry.core.sender.http.api.Constants.PID;

	@Reference
	private ConfigurationAdmin configAdmin;

	private Configuration otlpConfig;

	@Activate
	void activate() throws IOException {
		otlpConfig = configAdmin.getConfiguration(OTLP_HTTP_PID, "?");
		Dictionary<String, Object> otlpProps = new Hashtable<>();
		otlpProps.put("serviceName", "opentelemetry-example");
		otlpProps.put("endpoint", "http://localhost:4318");
		otlpConfig.update(otlpProps);
	}

	@Deactivate
	void deactivate() throws IOException {
		try {
			if (otlpConfig != null)
				otlpConfig.delete();
		} catch (IOException e) {
			/* ignore */ }
	}
}
