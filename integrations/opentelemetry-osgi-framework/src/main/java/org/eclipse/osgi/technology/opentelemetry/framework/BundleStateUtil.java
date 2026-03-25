package org.eclipse.osgi.technology.opentelemetry.framework;

import org.osgi.framework.Bundle;

/**
 * Utility methods for working with OSGi bundle state constants.
 */
public final class BundleStateUtil {

    private BundleStateUtil() {}

    /**
     * Converts a numeric OSGi bundle state to a human-readable string.
     */
    public static String bundleStateToString(int state) {
        return switch (state) {
            case Bundle.UNINSTALLED -> "UNINSTALLED";
            case Bundle.INSTALLED -> "INSTALLED";
            case Bundle.RESOLVED -> "RESOLVED";
            case Bundle.STARTING -> "STARTING";
            case Bundle.STOPPING -> "STOPPING";
            case Bundle.ACTIVE -> "ACTIVE";
            default -> "UNKNOWN(" + state + ")";
        };
    }
}
