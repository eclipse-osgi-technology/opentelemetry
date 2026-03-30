/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 * 
 */

package org.eclipse.osgi.technology.opentelemetry.integration.framework;

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
