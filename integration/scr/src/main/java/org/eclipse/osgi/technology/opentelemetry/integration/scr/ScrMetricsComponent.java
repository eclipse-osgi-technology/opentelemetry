package org.eclipse.osgi.technology.opentelemetry.integration.scr;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Exposes OSGi Declarative Services component state as OpenTelemetry metrics.
 * <p>
 * Uses the SCR Introspection API ({@link ServiceComponentRuntime}) to query
 * component descriptions and configurations, then publishes asynchronous gauge
 * metrics reporting:
 * <ul>
 *   <li>{@code osgi.scr.component.count} — total number of registered component descriptions</li>
 *   <li>{@code osgi.scr.component.states} — number of component configurations per state
 *       (ACTIVE, SATISFIED, UNSATISFIED_REFERENCE, UNSATISFIED_CONFIGURATION, FAILED_ACTIVATION)</li>
 *   <li>{@code osgi.scr.component.active} — number of active component configurations</li>
 *   <li>{@code osgi.scr.reference.satisfied} — count of satisfied references across all components</li>
 *   <li>{@code osgi.scr.reference.unsatisfied} — count of unsatisfied references across all components</li>
 * </ul>
 * <p>
 * Supports dynamic 1..n bindings of {@link ServiceComponentRuntime} services,
 * creating a separate set of gauges per service instance distinguished by the
 * {@code osgi.service.id} attribute.
 */
@Component(immediate = true)
public class ScrMetricsComponent {

    private static final Logger LOG = Logger.getLogger(ScrMetricsComponent.class.getName());
    private static final String INSTRUMENTATION_SCOPE = "org.eclipse.osgi.technology.opentelemetry.scr";
    private static final AttributeKey<Long> SERVICE_ID_KEY = AttributeKey.longKey("osgi.service.id");

    @Reference(policy = ReferencePolicy.DYNAMIC)
    private volatile OpenTelemetry openTelemetry;

    private final ConcurrentHashMap<ServiceComponentRuntime, ScrMetricsState> services = new ConcurrentHashMap<>();

    @Activate
    public void activate() {
        LOG.info("ScrMetricsComponent activated");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindServiceComponentRuntime(ServiceComponentRuntime scr, Map<String, Object> properties) {
        long serviceId = (Long) properties.get(Constants.SERVICE_ID);
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);

        ObservableLongGauge componentCountGauge = meter.gaugeBuilder("osgi.scr.component.count")
            .setDescription("Total number of registered DS component descriptions")
            .setUnit("{components}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    measurement.record(scr.getComponentDescriptionDTOs().size(),
                        Attributes.of(SERVICE_ID_KEY, serviceId));
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read component count", e);
                }
            });

        ObservableLongGauge componentStatesGauge = meter.gaugeBuilder("osgi.scr.component.states")
            .setDescription("Number of DS component configurations per state")
            .setUnit("{components}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    Map<String, Long> stateCounts = new HashMap<>();
                    for (ComponentDescriptionDTO desc : scr.getComponentDescriptionDTOs()) {
                        for (ComponentConfigurationDTO config : scr.getComponentConfigurationDTOs(desc)) {
                            String stateName = configStateToString(config.state);
                            stateCounts.merge(stateName, 1L, Long::sum);
                        }
                    }
                    stateCounts.forEach((state, count) ->
                        measurement.record(count, Attributes.of(
                            AttributeKey.stringKey("osgi.scr.state"), state,
                            SERVICE_ID_KEY, serviceId
                        ))
                    );
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read component states", e);
                }
            });

        ObservableLongGauge activeGauge = meter.gaugeBuilder("osgi.scr.component.active")
            .setDescription("Number of active DS component configurations")
            .setUnit("{components}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    long active = 0;
                    for (ComponentDescriptionDTO desc : scr.getComponentDescriptionDTOs()) {
                        for (ComponentConfigurationDTO config : scr.getComponentConfigurationDTOs(desc)) {
                            if (config.state == ComponentConfigurationDTO.ACTIVE) {
                                active++;
                            }
                        }
                    }
                    measurement.record(active, Attributes.of(SERVICE_ID_KEY, serviceId));
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read active component count", e);
                }
            });

        ObservableLongGauge satisfiedRefGauge = meter.gaugeBuilder("osgi.scr.reference.satisfied")
            .setDescription("Total number of satisfied service references across all components")
            .setUnit("{references}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    long count = 0;
                    for (ComponentDescriptionDTO desc : scr.getComponentDescriptionDTOs()) {
                        for (ComponentConfigurationDTO config : scr.getComponentConfigurationDTOs(desc)) {
                            if (config.satisfiedReferences != null) {
                                count += config.satisfiedReferences.length;
                            }
                        }
                    }
                    measurement.record(count, Attributes.of(SERVICE_ID_KEY, serviceId));
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read satisfied references", e);
                }
            });

        ObservableLongGauge unsatisfiedRefGauge = meter.gaugeBuilder("osgi.scr.reference.unsatisfied")
            .setDescription("Total number of unsatisfied service references across all components")
            .setUnit("{references}")
            .ofLongs()
            .buildWithCallback(measurement -> {
                try {
                    long count = 0;
                    for (ComponentDescriptionDTO desc : scr.getComponentDescriptionDTOs()) {
                        for (ComponentConfigurationDTO config : scr.getComponentConfigurationDTOs(desc)) {
                            if (config.unsatisfiedReferences != null) {
                                count += config.unsatisfiedReferences.length;
                            }
                        }
                    }
                    measurement.record(count, Attributes.of(SERVICE_ID_KEY, serviceId));
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to read unsatisfied references", e);
                }
            });

        ScrMetricsState state = new ScrMetricsState(serviceId, componentCountGauge,
            componentStatesGauge, activeGauge, satisfiedRefGauge, unsatisfiedRefGauge);
        services.put(scr, state);
        LOG.info("ScrMetricsComponent — bound ServiceComponentRuntime (service.id=" + serviceId + ")");
    }

    void unbindServiceComponentRuntime(ServiceComponentRuntime scr) {
        ScrMetricsState state = services.remove(scr);
        if (state != null) {
            state.close();
            LOG.info("ScrMetricsComponent — unbound ServiceComponentRuntime (service.id=" + state.serviceId() + ")");
        }
    }

    @Deactivate
    public void deactivate() {
        services.values().forEach(ScrMetricsState::close);
        services.clear();
        LOG.info("ScrMetricsComponent deactivated");
    }

    static String configStateToString(int state) {
        return switch (state) {
            case ComponentConfigurationDTO.UNSATISFIED_CONFIGURATION -> "UNSATISFIED_CONFIGURATION";
            case ComponentConfigurationDTO.UNSATISFIED_REFERENCE -> "UNSATISFIED_REFERENCE";
            case ComponentConfigurationDTO.SATISFIED -> "SATISFIED";
            case ComponentConfigurationDTO.ACTIVE -> "ACTIVE";
            case 16 -> "FAILED_ACTIVATION"; // ComponentConfigurationDTO.FAILED_ACTIVATION (1.4+)
            default -> "UNKNOWN(" + state + ")";
        };
    }

    static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
