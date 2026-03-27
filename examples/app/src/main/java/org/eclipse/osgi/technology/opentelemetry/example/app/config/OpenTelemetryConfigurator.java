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
 * activates (it uses configurationPolicy=REQUIRE). Exports telemetry
 * via OTLP/HTTP to localhost:4318.
 */
@Component(immediate = true)
public class OpenTelemetryConfigurator {

    private static final String OTLP_HTTP_PID = org.eclipse.osgi.technology.opentelemetry.sender.http.api.Constants.PID;
    private static final String LOGGING_PID = org.eclipse.osgi.technology.opentelemetry.sender.log.api.Constants.PID;

    @Reference
    private ConfigurationAdmin configAdmin;

    private Configuration otlpConfig;
    private Configuration loggingConfig;

    @Activate
    void activate() throws IOException {
        // OTLP/HTTP exporter (ranking 100, preferred when collector is running)
        otlpConfig = configAdmin.getConfiguration(OTLP_HTTP_PID, "?");
        Dictionary<String, Object> otlpProps = new Hashtable<>();
        otlpProps.put("serviceName", "opentelemetry-example");
        otlpProps.put("endpoint", "http://localhost:4318");
        otlpConfig.update(otlpProps);

        // Logging exporter (fallback, always works)
        loggingConfig = configAdmin.getConfiguration(LOGGING_PID, "?");
        Dictionary<String, Object> loggingProps = new Hashtable<>();
        loggingProps.put("serviceName", "opentelemetry-example");
        loggingConfig.update(loggingProps);
    }

    @Deactivate
    void deactivate() throws IOException {
        deleteQuietly(otlpConfig);
        deleteQuietly(loggingConfig);
    }

    private void deleteQuietly(Configuration config) {
        try {
            if (config != null) config.delete();
        } catch (IOException e) { /* ignore */ }
    }
}
