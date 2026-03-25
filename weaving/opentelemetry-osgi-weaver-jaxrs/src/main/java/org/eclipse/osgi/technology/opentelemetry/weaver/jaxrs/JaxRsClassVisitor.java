package org.eclipse.osgi.technology.opentelemetry.weaver.jaxrs;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM {@link ClassVisitor} that reads the class-level {@code @Path} annotation
 * and delegates method instrumentation to {@link JaxRsMethodVisitor} for methods
 * carrying JAX-RS HTTP method annotations.
 */
class JaxRsClassVisitor extends ClassVisitor {

    private static final String PATH_DESCRIPTOR = "Ljavax/ws/rs/Path;";

    private String className;
    private String classPath = "";
    private boolean transformed;

    JaxRsClassVisitor(ClassVisitor delegate) {
        super(Opcodes.ASM9, delegate);
    }

    @Override
    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
        if (PATH_DESCRIPTOR.equals(descriptor)) {
            return new AnnotationVisitor(Opcodes.ASM9, av) {
                @Override
                public void visit(String name, Object value) {
                    if ("value".equals(name)) {
                        classPath = (String) value;
                    }
                    super.visit(name, value);
                }
            };
        }
        return av;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
            String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        // Return our method visitor for all non-synthetic methods; it will
        // only instrument if HTTP method annotations are found
        if ((access & Opcodes.ACC_SYNTHETIC) == 0 && (access & Opcodes.ACC_BRIDGE) == 0) {
            JaxRsMethodVisitor jmv = new JaxRsMethodVisitor(
                    mv, access, name, descriptor, className, classPath);
            jmv.setTransformCallback(() -> transformed = true);
            return jmv;
        }
        return mv;
    }

    boolean isTransformed() {
        return transformed;
    }
}
