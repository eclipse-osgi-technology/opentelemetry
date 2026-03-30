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

package org.eclipse.osgi.technology.opentelemetry.weaving.jakarta.servlet;

import org.eclipse.osgi.technology.opentelemetry.weaving.hook.OpenTelemetryProxy;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM {@link ClassVisitor} that intercepts HTTP handler methods of
 * {@code HttpServlet} subclasses and wraps them with OpenTelemetry instrumentation.
 * <p>
 * Instruments {@code service}, {@code doGet}, {@code doPost}, {@code doPut},
 * {@code doDelete}, {@code doHead}, {@code doOptions}, and {@code doTrace}.
 */
class ServletClassVisitor extends ClassVisitor {

    private static final String HTTP_DESCRIPTOR = "(Ljakarta/servlet/http/HttpServletRequest;Ljakarta/servlet/http/HttpServletResponse;)V";
    private static final java.util.Set<String> INSTRUMENTABLE_METHODS = java.util.Set.of(
            "service", "doGet", "doPost", "doPut", "doDelete", "doHead", "doOptions", "doTrace");

    private final OpenTelemetryProxy telemetry;
    private boolean transformed;
    private String className;

    ServletClassVisitor(ClassVisitor delegate, OpenTelemetryProxy telemetry) {
        super(Opcodes.ASM9, delegate);
        this.telemetry = telemetry;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
            String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

        if (INSTRUMENTABLE_METHODS.contains(name) && HTTP_DESCRIPTOR.equals(descriptor)) {
            transformed = true;
            return new ServletServiceMethodVisitor(mv, access, name, descriptor, className);
        }

        return mv;
    }

    boolean isTransformed() {
        return transformed;
    }
}
