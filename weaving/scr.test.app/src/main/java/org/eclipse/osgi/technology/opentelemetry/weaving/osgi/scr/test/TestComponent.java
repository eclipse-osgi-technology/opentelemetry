package org.eclipse.osgi.technology.opentelemetry.weaving.osgi.scr.test;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Component(immediate = true)
public class TestComponent {

    @Activate
    void activate() {
        // Lifecycle method — will be woven by the SCR weaver
    }

    @Deactivate
    void deactivate() {
        // Lifecycle method — will be woven by the SCR weaver
    }
}
