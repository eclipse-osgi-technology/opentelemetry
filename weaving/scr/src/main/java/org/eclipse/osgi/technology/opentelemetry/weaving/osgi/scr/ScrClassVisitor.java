package org.eclipse.osgi.technology.opentelemetry.weaving.osgi.scr;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * ASM {@link ClassVisitor} that instruments DS component lifecycle methods
 * based on the {@link ComponentDescriptor} parsed from DS XML.
 * <p>
 * Methods are matched by name as declared in the component XML descriptor:
 * <ul>
 *   <li>{@code activate} method (default {@code "activate"})</li>
 *   <li>{@code deactivate} method (default {@code "deactivate"})</li>
 *   <li>{@code modified} method (only when explicitly declared in XML)</li>
 *   <li>Constructor injection (when {@code init > 0}, matches public constructor
 *       with matching parameter count)</li>
 * </ul>
 * <p>
 * No annotation scanning is performed — the XML descriptor is the single
 * source of truth for lifecycle method identification.
 */
class ScrClassVisitor extends ClassVisitor {

    private final ComponentDescriptor descriptor;
    private String className;
    private boolean transformed;

    ScrClassVisitor(ClassVisitor delegate, ComponentDescriptor descriptor) {
        super(Opcodes.ASM9, delegate);
        this.descriptor = descriptor;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String methodDescriptor,
            String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, methodDescriptor,
                signature, exceptions);

        if ((access & Opcodes.ACC_SYNTHETIC) != 0 || (access & Opcodes.ACC_BRIDGE) != 0) {
            return mv;
        }

        String action = determineAction(access, name, methodDescriptor);
        if (action != null) {
            transformed = true;
            return new ScrMethodVisitor(mv, access, name, methodDescriptor,
                    className, descriptor.componentName(), action);
        }

        return mv;
    }

    private String determineAction(int access, String name, String methodDescriptor) {
        if ("<init>".equals(name)) {
            if (descriptor.initParameterCount() > 0
                    && (access & Opcodes.ACC_PUBLIC) != 0) {
                int paramCount = Type.getArgumentTypes(methodDescriptor).length;
                if (paramCount == descriptor.initParameterCount()) {
                    return "constructor";
                }
            }
            return null;
        }

        if ("<clinit>".equals(name)) {
            return null;
        }

        if (name.equals(descriptor.activateMethod())) {
            return "activate";
        }
        if (name.equals(descriptor.deactivateMethod())) {
            return "deactivate";
        }
        if (descriptor.modifiedMethod() != null && name.equals(descriptor.modifiedMethod())) {
            return "modified";
        }

        return null;
    }

    boolean isTransformed() {
        return transformed;
    }
}
