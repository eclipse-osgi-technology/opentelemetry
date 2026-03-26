package org.eclipse.osgi.technology.opentelemetry.weaver.jaxrs;

import java.util.List;

import org.eclipse.osgi.technology.opentelemetry.weaving.OpenTelemetryProxy;
import org.eclipse.osgi.technology.opentelemetry.weaving.SafeClassWriter;
import org.eclipse.osgi.technology.opentelemetry.weaving.Weaver;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.osgi.framework.hooks.weaving.WovenClass;

/**
 * Weaver that instruments JAX-RS resource classes annotated with
 * {@code @jakarta.ws.rs.Path}.
 * The instrumentation detects HTTP method annotations ({@code @GET},
 * {@code @POST}, {@code @PUT}, {@code @DELETE}, etc.) on methods and
 * wraps them to create OpenTelemetry spans capturing:
 * <ul>
 *   <li>HTTP method (from annotation type)</li>
 *   <li>HTTP route (from {@code @Path} values)</li>
 *   <li>Resource class and method name</li>
 *   <li>Invocation duration as a histogram metric</li>
 * </ul>
 */
public class JaxRsWeaver implements Weaver {

    private static final String PATH_DESCRIPTOR = "Ljakarta/ws/rs/Path;";

    @Override
    public String name() {
        return "jaxrs";
    }

    @Override
    public boolean canWeave(String className, WovenClass wovenClass) {
        if (className.startsWith("jakarta.ws.rs.")) {
            return false;
        }
        byte[] bytes = wovenClass.getBytes();
        ClassReader reader = new ClassReader(bytes);
        PathDetector detector = new PathDetector();
        reader.accept(detector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
        return detector.hasPath;
    }

    @Override
    public void weave(WovenClass wovenClass, OpenTelemetryProxy telemetry) {
        JaxRsInstrumentationHelper.setProxy(telemetry);
        addDynamicImports(wovenClass);

        byte[] original = wovenClass.getBytes();
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new SafeClassWriter(reader, wovenClass);
        JaxRsClassVisitor visitor = new JaxRsClassVisitor(writer);
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);

        if (visitor.isTransformed()) {
            wovenClass.setBytes(writer.toByteArray());
        }
    }

    private void addDynamicImports(WovenClass wovenClass) {
        List<String> imports = wovenClass.getDynamicImports();
        imports.add("io.opentelemetry.api");
        imports.add("io.opentelemetry.api.trace");
        imports.add("io.opentelemetry.api.metrics");
        imports.add("io.opentelemetry.api.common");
        imports.add("io.opentelemetry.context");
        imports.add("org.eclipse.osgi.technology.opentelemetry.weaving");
        imports.add("org.eclipse.osgi.technology.opentelemetry.weaver.jaxrs");
    }

    /**
     * Lightweight class visitor that only checks for the presence of {@code @Path}.
     */
    private static class PathDetector extends ClassVisitor {
        boolean hasPath;

        PathDetector() {
            super(Opcodes.ASM9);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (PATH_DESCRIPTOR.equals(descriptor)) {
                hasPath = true;
            }
            return null;
        }
    }
}
