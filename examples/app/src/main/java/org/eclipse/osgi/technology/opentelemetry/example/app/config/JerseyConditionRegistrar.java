package org.eclipse.osgi.technology.opentelemetry.example.app.config;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.condition.Condition;

/**
 * Registers the {@code jersey.runtime} Condition service that the
 * Eclipse OSGiTech REST Whiteboard components require to activate.
 */
@Component(immediate = true)
public class JerseyConditionRegistrar {

    private ServiceRegistration<Condition> registration;

    @Activate
    void activate(BundleContext ctx) {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(Condition.CONDITION_ID, "jersey.runtime");
        registration = ctx.registerService(Condition.class, Condition.INSTANCE, props);
    }

    @Deactivate
    void deactivate() {
        if (registration != null) {
            registration.unregister();
        }
    }
}
