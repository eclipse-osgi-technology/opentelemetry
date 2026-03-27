package org.eclipse.osgi.technology.opentelemetry.weaving.jakarta.rest;

import java.util.Map;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

/**
 * ASM {@link AdviceAdapter} that detects JAX-RS HTTP method annotations
 * ({@code @GET}, {@code @POST}, etc.) and wraps the method with
 * OpenTelemetry instrumentation.
 * Annotation detection happens during {@link #visitAnnotation}, before
 * {@link #onMethodEnter} is called, so all metadata is available
 * at instrumentation time.
 */
class JaxRsMethodVisitor extends AdviceAdapter {

    private static final String HELPER =
            "org/eclipse/osgi/technology/opentelemetry/weaving/jakarta/rest/JaxRsInstrumentationHelper";
    private static final String PATH_DESCRIPTOR = "Ljakarta/ws/rs/Path;";

    private static final Map<String, String> HTTP_METHOD_ANNOTATIONS = Map.of(
            "Ljakarta/ws/rs/GET;", "GET",
            "Ljakarta/ws/rs/POST;", "POST",
            "Ljakarta/ws/rs/PUT;", "PUT",
            "Ljakarta/ws/rs/DELETE;", "DELETE",
            "Ljakarta/ws/rs/PATCH;", "PATCH",
            "Ljakarta/ws/rs/HEAD;", "HEAD",
            "Ljakarta/ws/rs/OPTIONS;", "OPTIONS");

    private final String className;
    private final String classPath;
    private final String javaMethodName;

    private String httpMethod;
    private String methodPath = "";
    private int contextLocal;
    private Runnable transformCallback;

    JaxRsMethodVisitor(MethodVisitor mv, int access, String name,
            String descriptor, String className, String classPath) {
        super(Opcodes.ASM9, mv, access, name, descriptor);
        this.className = className;
        this.classPath = classPath;
        this.javaMethodName = name;
    }

    void setTransformCallback(Runnable callback) {
        this.transformCallback = callback;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(descriptor, visible);

        String method = HTTP_METHOD_ANNOTATIONS.get(descriptor);
        if (method != null) {
            httpMethod = method;
        }

        if (PATH_DESCRIPTOR.equals(descriptor)) {
            return new AnnotationVisitor(Opcodes.ASM9, av) {
                @Override
                public void visit(String name, Object value) {
                    if ("value".equals(name)) {
                        methodPath = (String) value;
                    }
                    super.visit(name, value);
                }
            };
        }
        return av;
    }

    @Override
    protected void onMethodEnter() {
        if (httpMethod == null) {
            return;
        }
        if (transformCallback != null) {
            transformCallback.run();
        }

        contextLocal = newLocal(Type.getType("[Ljava/lang/Object;"));

        String fullPath = classPath;
        if (!methodPath.isEmpty()) {
            if (!fullPath.endsWith("/") && !methodPath.startsWith("/")) {
                fullPath += "/";
            }
            fullPath += methodPath;
        }

        visitLdcInsn(httpMethod);
        visitLdcInsn(fullPath);
        visitLdcInsn(className.replace('/', '.'));
        visitLdcInsn(javaMethodName);
        visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "onMethodEnter",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/Object;",
                false);
        storeLocal(contextLocal);
    }

    @Override
    protected void onMethodExit(int opcode) {
        if (httpMethod == null) {
            return;
        }
        if (opcode == ATHROW) {
            dup();
            int exLocal = newLocal(Type.getType("Ljava/lang/Throwable;"));
            storeLocal(exLocal);
            loadLocal(contextLocal);
            loadLocal(exLocal);
            visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "onMethodError",
                    "([Ljava/lang/Object;Ljava/lang/Throwable;)V", false);
        }
        loadLocal(contextLocal);
        visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "onMethodExit",
                "([Ljava/lang/Object;)V", false);
    }
}
