package org.eclipse.osgi.technology.opentelemetry.weaving.osgi.scr.test;

/**
 * Marker interface used to control when the test DS component activates.
 * The test registers a service of this type to satisfy the component's
 * mandatory reference, triggering activation.
 */
public interface TestActivationTrigger {
}
