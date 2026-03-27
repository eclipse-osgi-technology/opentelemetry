package org.eclipse.osgi.technology.opentelemetry.weaving.jdbc;

import java.util.List;
import java.util.Set;

import org.eclipse.osgi.technology.opentelemetry.weaving.hook.OpenTelemetryProxy;
import org.eclipse.osgi.technology.opentelemetry.weaving.hook.SafeClassWriter;
import org.eclipse.osgi.technology.opentelemetry.weaving.hook.Weaver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.osgi.framework.hooks.weaving.WovenClass;

/**
 * Weaver that instruments JDBC {@code Statement}, {@code PreparedStatement},
 * and {@code CallableStatement} implementations.
 * The instrumentation wraps {@code execute}, {@code executeQuery},
 * {@code executeUpdate}, {@code executeBatch}, and {@code executeLargeUpdate}
 * methods to create OpenTelemetry spans for each database operation, capturing:
 * <ul>
 *   <li>SQL statement text (when available, truncated to 1000 characters)</li>
 *   <li>Operation name (execute, executeQuery, executeUpdate, etc.)</li>
 *   <li>Statement implementation class name</li>
 *   <li>Query duration as a histogram metric</li>
 * </ul>
 */
public class JdbcWeaver implements Weaver {

    private static final Set<String> JDBC_INTERFACES = Set.of(
            "java/sql/Statement",
            "java/sql/PreparedStatement",
            "java/sql/CallableStatement");

    @Override
    public String name() {
        return "jdbc";
    }

    @Override
    public boolean canWeave(String className, WovenClass wovenClass) {
        if (className.startsWith("java.sql.") || className.startsWith("javax.sql.")) {
            return false;
        }
        byte[] bytes = wovenClass.getBytes();
        ClassReader reader = new ClassReader(bytes);
        String[] interfaces = reader.getInterfaces();
        for (String iface : interfaces) {
            if (JDBC_INTERFACES.contains(iface)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void weave(WovenClass wovenClass, OpenTelemetryProxy telemetry) {
        JdbcInstrumentationHelper.setProxy(telemetry);
        addDynamicImports(wovenClass);

        byte[] original = wovenClass.getBytes();
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new SafeClassWriter(reader, wovenClass);
        JdbcClassVisitor visitor = new JdbcClassVisitor(writer);
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
        imports.add("org.eclipse.osgi.technology.opentelemetry.weaving.jdbc");
    }
}
