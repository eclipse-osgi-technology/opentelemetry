package org.eclipse.osgi.technology.opentelemetry.integration.cm;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;

/**
 * Emits OpenTelemetry log records for all existing OSGi configurations,
 * providing a detailed inventory of the Configuration Admin state.
 * <p>
 * On activation, this component queries
 * {@link ConfigurationAdmin#listConfigurations} and emits structured log
 * records containing:
 * <ul>
 * <li>Configuration PID and factory PID</li>
 * <li>Bundle location binding</li>
 * <li>Number of properties</li>
 * <li>Property keys (values are omitted for security)</li>
 * </ul>
 */
@Component(immediate = true)
public class ConfigAdminInventoryComponent {

	private static final Logger LOG = Logger.getLogger(ConfigAdminInventoryComponent.class.getName());
	private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.integration.cm";

	@Reference(policy = ReferencePolicy.DYNAMIC)
	private volatile OpenTelemetry openTelemetry;

	private io.opentelemetry.api.logs.Logger otelLogger;
	private final ConcurrentHashMap<ConfigurationAdmin, Long> services = new ConcurrentHashMap<>();

	@Activate
	public void activate() {
		this.otelLogger = openTelemetry.getLogsBridge().loggerBuilder(INSTRUMENTATION_SCOPE)
				.setInstrumentationVersion("0.1.0").build();
		LOG.info("ConfigAdminInventoryComponent activated");
	}

	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
	void bindConfigurationAdmin(ConfigurationAdmin configAdmin, Map<String, Object> properties) {
		long serviceId = (Long) properties.get(Constants.SERVICE_ID);
		services.put(configAdmin, serviceId);
		emitInventory(configAdmin, serviceId);
		LOG.info("Bound ConfigurationAdmin service.id=" + serviceId + " — logging configuration inventory");
	}

	void unbindConfigurationAdmin(ConfigurationAdmin configAdmin) {
		Long serviceId = services.remove(configAdmin);
		if (serviceId != null) {
			LOG.info("Unbound ConfigurationAdmin service.id=" + serviceId);
		}
	}

	@Deactivate
	public void deactivate() {
		services.clear();
		LOG.info("ConfigAdminInventoryComponent deactivated");
	}

	private void emitInventory(ConfigurationAdmin configAdmin, long serviceId) {
		try {
			Configuration[] configs = configAdmin.listConfigurations(null);
			int total = configs != null ? configs.length : 0;

			long factoryCount = 0;
			if (configs != null) {
				for (Configuration config : configs) {
					if (config.getFactoryPid() != null) {
						factoryCount++;
					}
				}
			}

			otelLogger.logRecordBuilder().setSeverity(Severity.INFO)
					.setBody("Configuration inventory: " + total + " configurations (" + factoryCount + " factory, "
							+ (total - factoryCount) + " singleton)")
					.setAttribute(AttributeKey.longKey("cm.configuration.count"), (long) total)
					.setAttribute(AttributeKey.longKey("cm.factory.count"), factoryCount)
					.setAttribute(AttributeKey.longKey("cm.singleton.count"), (long) total - factoryCount)
					.setAttribute(AttributeKey.longKey("osgi.service.id"), serviceId).emit();

			if (configs != null) {
				for (Configuration config : configs) {
					emitConfigLog(config, serviceId);
				}
			}

			LOG.info("ConfigAdminInventoryComponent — emitted " + total + " configuration log records");
		} catch (IOException | org.osgi.framework.InvalidSyntaxException e) {
			LOG.log(Level.WARNING, "Failed to emit configuration inventory", e);
		}
	}

	private void emitConfigLog(Configuration config, long serviceId) {
		String pid = config.getPid();
		boolean isFactory = config.getFactoryPid() != null;

		var builder = otelLogger.logRecordBuilder().setSeverity(Severity.INFO)
				.setBody("Configuration: " + pid + (isFactory ? " [factory:" + config.getFactoryPid() + "]" : ""))
				.setAttribute(AttributeKey.stringKey("cm.pid"), pid)
				.setAttribute(AttributeKey.booleanKey("cm.is_factory"), isFactory);

		if (config.getFactoryPid() != null) {
			builder.setAttribute(AttributeKey.stringKey("cm.factory.pid"), config.getFactoryPid());
		}

		if (config.getBundleLocation() != null) {
			builder.setAttribute(AttributeKey.stringKey("cm.bundle.location"), config.getBundleLocation());
		}

		// Log property keys (not values — security)
		Dictionary<String, Object> properties = config.getProperties();
		if (properties != null) {
			builder.setAttribute(AttributeKey.longKey("cm.property.count"), (long) properties.size());
			StringBuilder keys = new StringBuilder();
			Enumeration<String> keyEnum = properties.keys();
			while (keyEnum.hasMoreElements()) {
				if (!keys.isEmpty()) {
					keys.append(", ");
				}
				keys.append(keyEnum.nextElement());
			}
			builder.setAttribute(AttributeKey.stringKey("cm.property.keys"), keys.toString());
		}

		builder.setAttribute(AttributeKey.longKey("osgi.service.id"), serviceId);
		builder.emit();
	}
}
