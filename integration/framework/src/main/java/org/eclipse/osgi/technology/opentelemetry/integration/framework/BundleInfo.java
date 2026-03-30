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
