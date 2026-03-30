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
 * Creates two JAX-RS (Jakarta RS) Whiteboard instances via ConfigurationAdmin:
 * <ul>
 * <li>rest1 on http1 (port 8181), context path /rest1</li>
 * <li>rest2 on http2 (port 8182), context path /rest2</li>
 * </ul>
 */
@Component(immediate = true)
public class RestWhiteboardConfigurator {

	private Configuration rest1Config;
	private Configuration rest2Config;

	@Reference
	private ConfigurationAdmin configAdmin;

	@Activate
	void activate() throws IOException {
		rest1Config = createRestConfig("rest1", "REST Whiteboard 1", "rest1", "(id=http1)");
		rest2Config = createRestConfig("rest2", "REST Whiteboard 2", "rest2", "(id=http2)");
	}

	@Deactivate
	void deactivate() throws IOException {
		deleteQuietly(rest1Config);
		deleteQuietly(rest2Config);
	}

	private Configuration createRestConfig(String name, String displayName, String contextPath, String httpTarget)
			throws IOException {
		Configuration config = configAdmin.getFactoryConfiguration("JakartarsServletWhiteboardRuntimeComponent", name,
				"?");
		Dictionary<String, Object> props = new Hashtable<>();
		props.put("jersey.jakartars.whiteboard.name", displayName);
		props.put("jersey.context.path", contextPath);
		props.put("osgi.http.whiteboard.target", httpTarget);
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
