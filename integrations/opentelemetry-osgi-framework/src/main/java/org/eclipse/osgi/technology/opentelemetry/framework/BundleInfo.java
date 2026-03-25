package org.eclipse.osgi.technology.opentelemetry.framework;

/**
 * Immutable snapshot of an OSGi bundle's state.
 *
 * @param bundleId     the unique bundle identifier
 * @param symbolicName the bundle symbolic name
 * @param version      the bundle version string
 * @param state        the numeric bundle state constant
 * @param stateName    the human-readable state name
 * @param location     the bundle install location
 */
public record BundleInfo(
    long bundleId,
    String symbolicName,
    String version,
    int state,
    String stateName,
    String location
) {}
