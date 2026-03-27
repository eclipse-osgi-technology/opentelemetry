package org.eclipse.osgi.technology.opentelemetry.weaving.jakarta.servlet;

import java.util.List;

import org.eclipse.osgi.technology.opentelemetry.weaving.hook.OpenTelemetryProxy;
import org.eclipse.osgi.technology.opentelemetry.weaving.hook.SafeClassWriter;
import org.eclipse.osgi.technology.opentelemetry.weaving.hook.Weaver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.osgi.framework.hooks.weaving.WovenClass;

/**
 * Weaver that instruments {@code jakarta.servlet.http.HttpServlet} subclasses.
 * <p>
 * The instrumentation wraps the {@code service(HttpServletRequest, HttpServletResponse)}
 * method to create an OpenTelemetry span for each HTTP request, capturing:
 * <ul>
 *   <li>HTTP method, URI, and query string as span attributes</li>
 *   <li>Response status code</li>
 *   <li>Request duration as a histogram metric</li>
 *   <li>Exception details on error</li>
 * </ul>
 */
public class ServletWeaver implements Weaver {

    private static final String HTTP_SERVLET_CLASS = "jakarta/servlet/http/HttpServlet";

    @Override
    public String name() {
        return "servlet";
    }

    @Override
    public boolean canWeave(String className, WovenClass wovenClass) {
        if (className.startsWith("jakarta.servlet.")) {
            return false;
        }
        byte[] bytes = wovenClass.getBytes();
        ClassReader reader = new ClassReader(bytes);
        String superName = reader.getSuperName();
        return HTTP_SERVLET_CLASS.equals(superName) || extendsHttpServlet(reader);
    }

    @Override
    public void weave(WovenClass wovenClass, OpenTelemetryProxy telemetry) {
        ServletInstrumentationHelper.setProxy(telemetry);
        addDynamicImports(wovenClass);

        byte[] original = wovenClass.getBytes();
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new SafeClassWriter(reader, wovenClass);
        ServletClassVisitor visitor = new ServletClassVisitor(writer, telemetry);
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
        imports.add("org.eclipse.osgi.technology.opentelemetry.weaving.hook");
        imports.add("org.eclipse.osgi.technology.opentelemetry.weaving.jakarta.servlet");
    }

    private boolean extendsHttpServlet(ClassReader reader) {
        // Quick check: look at the superclass name only (one level)
        // This covers the vast majority of servlets (direct subclasses of HttpServlet)
        String superName = reader.getSuperName();
        return HTTP_SERVLET_CLASS.equals(superName);
    }
}
