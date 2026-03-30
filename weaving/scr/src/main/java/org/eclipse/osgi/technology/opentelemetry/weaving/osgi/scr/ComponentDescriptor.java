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

package org.eclipse.osgi.technology.opentelemetry.weaving.osgi.scr;

/**
 * Describes a DS component's lifecycle methods as declared in the component XML descriptor.
 * <p>
 * The DS specification defines default values for lifecycle method names:
 * {@code activate} defaults to {@code "activate"}, {@code deactivate} defaults
 * to {@code "deactivate"}, and {@code modified} has no default (null means no
 * modified callback).
 * The {@code init} attribute specifies constructor injection parameter count
 * (default 0, meaning no constructor injection).
 *
 * @param className the fully qualified implementation class name
 * @param componentName the component name from the {@code name} attribute
 * @param activateMethod the activate method name (default {@code "activate"})
 * @param deactivateMethod the deactivate method name (default {@code "deactivate"})
 * @param modifiedMethod the modified method name, or null if not specified
 * @param initParameterCount the constructor injection parameter count (0 = no constructor injection)
 */
public record ComponentDescriptor(
        String className,
        String componentName,
        String activateMethod,
        String deactivateMethod,
        String modifiedMethod,
        int initParameterCount
) {}
