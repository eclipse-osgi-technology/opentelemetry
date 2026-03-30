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
 * Creates three Felix HTTP instances on ports 8181, 8182, 8183 via
 * ConfigurationAdmin factory configurations. Also disables the default HTTP
 * instance.
 */
@Component(immediate = true)
public class HttpInstanceConfigurator {

	private Configuration defaultConfig;
	private Configuration http1Config;
	private Configuration http2Config;
	private Configuration http3Config;

	@Reference
	private ConfigurationAdmin configAdmin;

	@Activate
	void activate() throws IOException {
		defaultConfig = this.configAdmin.getConfiguration("org.apache.felix.http", "?");
		Dictionary<String, Object> defaultProps = new Hashtable<>();
		defaultProps.put("org.apache.felix.http.enable", false);
		defaultConfig.update(defaultProps);

		http1Config = createHttpConfig("http1", 8181);
		http2Config = createHttpConfig("http2", 8182);
		http3Config = createHttpConfig("http3", 8183);
	}

	@Deactivate
	void deactivate() throws IOException {
		deleteQuietly(defaultConfig);
		deleteQuietly(http1Config);
		deleteQuietly(http2Config);
		deleteQuietly(http3Config);
	}

	private Configuration createHttpConfig(String name, int port) throws IOException {
		Configuration config = configAdmin.getFactoryConfiguration("org.apache.felix.http", name, "?");
		Dictionary<String, Object> props = new Hashtable<>();
		props.put("org.osgi.service.http.port", port);
		props.put("org.apache.felix.http.runtime.init.id", name);
		props.put("org.apache.felix.http.name", name);
		config.update(props);
		return config;
	}

	private void deleteQuietly(Configuration config) {
		try {
			if (config != null) {
				config.delete();
			}
		} catch (IOException e) {
			// ignore
		}
	}
}
