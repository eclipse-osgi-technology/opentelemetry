package org.eclipse.osgi.technology.opentelemetry.weaver.jdbc;

import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM {@link ClassVisitor} that intercepts JDBC execute methods on
 * {@code Statement}, {@code PreparedStatement}, and {@code CallableStatement}
 * implementations and wraps them with OpenTelemetry instrumentation.
 */
class JdbcClassVisitor extends ClassVisitor {

    private static final Set<String> EXECUTE_METHODS = Set.of(
            "execute", "executeQuery", "executeUpdate",
            "executeBatch", "executeLargeUpdate", "executeLargeBatch");

    private boolean transformed;
    private String className;

    JdbcClassVisitor(ClassVisitor delegate) {
        super(Opcodes.ASM9, delegate);
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

        if (EXECUTE_METHODS.contains(name) && isExecuteDescriptor(name, descriptor)) {
            transformed = true;
            boolean hasSqlParam = descriptor.startsWith("(Ljava/lang/String;");
            return new JdbcMethodVisitor(mv, access, name, descriptor, className, hasSqlParam);
        }

        return mv;
    }

    private boolean isExecuteDescriptor(String name, String descriptor) {
        return switch (name) {
            case "execute" -> descriptor.equals("(Ljava/lang/String;)Z")
                    || descriptor.equals("()Z");
            case "executeQuery" -> descriptor.equals("(Ljava/lang/String;)Ljava/sql/ResultSet;")
                    || descriptor.equals("()Ljava/sql/ResultSet;");
            case "executeUpdate" -> descriptor.equals("(Ljava/lang/String;)I")
                    || descriptor.equals("()I");
            case "executeLargeUpdate" -> descriptor.equals("(Ljava/lang/String;)J")
                    || descriptor.equals("()J");
            case "executeBatch" -> descriptor.equals("()[I");
            case "executeLargeBatch" -> descriptor.equals("()[J");
            default -> false;
        };
    }

    boolean isTransformed() {
        return transformed;
    }
}
